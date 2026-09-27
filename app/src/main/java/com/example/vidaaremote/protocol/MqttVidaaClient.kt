package com.example.vidaaremote.protocol

import android.content.Context
import android.util.Base64
import com.example.vidaaremote.model.TvDevice
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient
import io.netty.handler.ssl.util.InsecureTrustManagerFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.net.ssl.KeyManagerFactory

/**
 * Local Hisense / VIDAA MQTT-over-TLS client.
 *
 * Protocol reference:
 * https://github.com/tombabolewski/vidaa-control
 */
class MqttVidaaClient(
    context: Context,
) : VidaaRemoteClient {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("vidaa_remote", Context.MODE_PRIVATE)

    @Volatile
    override var connectedDevice: TvDevice? = null
        private set

    @Volatile
    override var isConnected: Boolean = false
        private set

    @Volatile
    override var isAuthenticated: Boolean = false
        private set

    private var mqtt: Mqtt3AsyncClient? = null
    private var clientId: String = ""
    private var topicClientId: String = ""
    private var mqttUsername: String = ""
    private var currentAuthMethod: AuthMethod = AuthMethod.MODERN

    @Volatile
    private var authAccepted = false

    @Volatile
    private var authLatch: CountDownLatch? = null

    private val mobileDeviceId: String by lazy { loadOrCreateDeviceId() }

    override suspend fun connect(device: TvDevice): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            disconnectInternal()
            connectedDevice = device

            val stored = loadStoredCredentials(device.host)
            if (stored != null) {
                val tokenClient = buildMqttClient(
                    device = device,
                    clientId = stored.clientId,
                    username = stored.username,
                )
                try {
                    connectClient(tokenClient, stored.username, stored.accessToken)
                    mqtt = tokenClient
                    clientId = stored.clientId
                    topicClientId = stored.clientId
                    mqttUsername = stored.username
                    isConnected = true
                    isAuthenticated = true
                    subscribeToResponses(tokenClient)
                    return@runCatching
                } catch (_: Exception) {
                    try {
                        tokenClient.disconnect()
                    } catch (_: Exception) {
                    }
                    clearStoredCredentials(device.host)
                }
            }

            var lastError: Throwable? = null
            val attempts = listOf(AuthMethod.MODERN, AuthMethod.MIDDLE, AuthMethod.LEGACY)

            for (method in attempts) {
                val creds = generateCredentials(mobileDeviceId, method)
                val candidate = buildMqttClient(device, creds.clientId, creds.username)
                try {
                    connectClient(candidate, creds.username, creds.password)
                    mqtt = candidate
                    clientId = creds.clientId
                    topicClientId = creds.clientId
                    mqttUsername = creds.username
                    currentAuthMethod = method
                    isConnected = true
                    isAuthenticated = false
                    subscribeToResponses(candidate)
                    return@runCatching
                } catch (t: Throwable) {
                    lastError = t
                    try {
                        candidate.disconnect()
                    } catch (_: Exception) {
                    }
                }
            }

            // Older firmware sometimes accepts the static RemoteNOW service credentials.
            val staticClientId = mobileDeviceId.replace(":", "").uppercase(Locale.US) + "\$vidaa_common"
            val staticCandidate = buildMqttClient(device, staticClientId, STATIC_USERNAME)
            try {
                connectClient(staticCandidate, STATIC_USERNAME, STATIC_PASSWORD)
                mqtt = staticCandidate
                clientId = staticClientId
                topicClientId = mobileDeviceId.uppercase(Locale.US) + "\$normal"
                mqttUsername = STATIC_USERNAME
                currentAuthMethod = AuthMethod.LEGACY
                isConnected = true
                isAuthenticated = false
                subscribeToResponses(staticCandidate)
                return@runCatching
            } catch (t: Throwable) {
                lastError = t
                try {
                    staticCandidate.disconnect()
                } catch (_: Exception) {
                }
            }

            throw IllegalStateException(
                "Could not connect to VIDAA MQTT service on ${device.host}:${device.port}. " +
                    "Make sure the TV is on, phone control is enabled, and both devices are on the same Wi-Fi.",
                lastError,
            )
        }
    }

    override suspend fun startPairing(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            requireConnected()
            val payload = JSONObject()
                .put("app_version", 2)
                .put("connect_result", 0)
                .put("device_type", "Mobile App")
                .toString()

            publish(topic("ui_service", "vidaa_app_connect"), payload)

            // This handshake is optional on some generations, but harmless where supported.
            val loginInfo = JSONObject()
                .put("type", "login")
                .put("tvLogin", false)
                .put("tvDeviceId", "")
                .put("tvCountry", "")
                .put("mobileLogin", true)
                .put("mobileDeviceId", mobileDeviceId)
                .toString()

            try {
                publish(topic("ui_service", "login_each_other_info"), loginInfo)
            } catch (_: Exception) {
                // Pairing can proceed without this optional message.
            }
        }
    }

    override suspend fun authenticate(pin: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            requireConnected()
            require(pin.matches(Regex("\\d{4,8}"))) { "Enter the PIN shown on the TV." }

            authAccepted = false
            val latch = CountDownLatch(1)
            authLatch = latch

            val payload = JSONObject().put("authNum", pin).toString()
            publish(topic("ui_service", "authenticationcode"), payload)

            if (!latch.await(20, TimeUnit.SECONDS)) {
                throw IllegalStateException("Pairing timed out. Start pairing again and enter the TV PIN promptly.")
            }
            if (!authAccepted) {
                throw IllegalStateException("The TV rejected the PIN.")
            }

            isAuthenticated = true
        }
    }

    override suspend fun startLegacyPairing(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val device = connectedDevice ?: throw IllegalStateException("Connect to the TV first.")

            val oldClient = mqtt
            mqtt = null
            isConnected = false
            isAuthenticated = false
            if (oldClient != null) {
                try {
                    oldClient.disconnect().get(3, TimeUnit.SECONDS)
                } catch (_: Exception) {
                }
            }

            val legacyTopicId = mobileDeviceId.uppercase(Locale.US) + "\$normal"
            val legacyMqttId = legacyTopicId
            val candidate = buildMqttClient(device, legacyMqttId, STATIC_USERNAME)

            connectClient(candidate, STATIC_USERNAME, STATIC_PASSWORD)
            mqtt = candidate
            clientId = legacyMqttId
            topicClientId = legacyTopicId
            mqttUsername = STATIC_USERNAME
            currentAuthMethod = AuthMethod.LEGACY
            isConnected = true
            isAuthenticated = false
            authAccepted = false
            subscribeToResponses(candidate)

            // On P0218 / RemoteNOW-era firmware this unauthorized state request
            // is what causes the TV to display its pairing PIN.
            try {
                publish("/remoteapp/tv/ui_service/${topicClientId}/actions/gettvstate", "")
            } catch (_: Exception) {
            }
        }
    }

    override suspend fun sendKey(key: VidaaKey): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            requireConnected()
            check(isAuthenticated) { "Pair the TV before using the remote." }
            publish(
                "/remoteapp/tv/remote_service/${topicClientId.ifBlank { clientId }}/actions/sendkey",
                key.wireValue,
            )
        }
    }

    override fun disconnect() {
        disconnectInternal()
    }

    private fun disconnectInternal() {
        val client = mqtt
        mqtt = null
        isConnected = false
        isAuthenticated = false
        connectedDevice = null
        authLatch?.countDown()
        authLatch = null
        if (client != null) {
            try {
                client.disconnect()
            } catch (_: Exception) {
            }
        }
    }

    private fun connectClient(client: Mqtt3AsyncClient, username: String, password: String) {
        client.connectWith()
            .cleanSession(true)
            .simpleAuth()
            .username(username)
            .password(password.toByteArray(StandardCharsets.UTF_8))
            .applySimpleAuth()
            .send()
            .get(10, TimeUnit.SECONDS)
    }

    private fun buildMqttClient(
        device: TvDevice,
        clientId: String,
        username: String,
    ): Mqtt3AsyncClient {
        val keyManagerFactory = buildKeyManagerFactory()

        return MqttClient.builder()
            .useMqttVersion3()
            .identifier(clientId)
            .serverHost(device.host)
            .serverPort(device.port)
            .sslConfig()
            .protocols(listOf("TLSv1.2"))
            .keyManagerFactory(keyManagerFactory)
            .trustManagerFactory(InsecureTrustManagerFactory.INSTANCE)
            .hostnameVerifier { _, _ -> true }
            .applySslConfig()
            .buildAsync()
    }

    private fun subscribeToResponses(client: Mqtt3AsyncClient) {
        val topics = listOf(
            "/remoteapp/mobile/${topicClientId.ifBlank { clientId }}/ui_service/data/authentication",
            "/remoteapp/mobile/${topicClientId.ifBlank { clientId }}/ui_service/data/authenticationcodetoast",
            "/remoteapp/mobile/${topicClientId.ifBlank { clientId }}/ui_service/data/authenticationcode",
            "/remoteapp/mobile/${topicClientId.ifBlank { clientId }}/ui_service/data/authenticationcodeclose",
            "/remoteapp/mobile/${topicClientId.ifBlank { clientId }}/ui_service/data/tokenissuance",
            "/remoteapp/mobile/${topicClientId.ifBlank { clientId }}/platform_service/data/tokenissuance",
            "/remoteapp/mobile/broadcast/ui_service/state",
            "/remoteapp/mobile/broadcast/platform_service/actions/volumechange",
        )

        topics.forEach { filter ->
            client.subscribeWith()
                .topicFilter(filter)
                .callback { publish ->
                    val bytes = publish.payload.orElse(null)?.let { buffer ->
                        val copy = ByteArray(buffer.remaining())
                        buffer.get(copy)
                        copy
                    } ?: ByteArray(0)
                    handleMessage(publish.topic.toString(), String(bytes, StandardCharsets.UTF_8))
                }
                .send()
                .get(5, TimeUnit.SECONDS)
        }
    }

    private fun handleMessage(topic: String, payloadText: String) {
        if (topic.contains("tokenissuance")) {
            val json = parseObject(payloadText) ?: return
            val accessToken = json.optString("accesstoken")
            if (accessToken.isNotBlank()) {
                val refreshToken = json.optString("refreshtoken")
                saveStoredCredentials(
                    host = connectedDevice?.host ?: return,
                    clientId = clientId,
                    username = mqttUsername,
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                )
                authAccepted = true
                isAuthenticated = true
                authLatch?.countDown()
            }
            return
        }

        if (topic.contains("authenticationcode")) {
            val json = parseObject(payloadText)
            val result = json?.optInt("result", -1) ?: -1
            if (result == 1) {
                authAccepted = true
                isAuthenticated = true
                authLatch?.countDown()
            } else if (result == 0) {
                authAccepted = false
                authLatch?.countDown()
            }
        }
    }

    private fun parseObject(value: String): JSONObject? {
        return try {
            JSONObject(value)
        } catch (_: Exception) {
            null
        }
    }

    private fun publish(topic: String, payload: String) {
        val client = mqtt ?: throw IllegalStateException("TV is not connected.")
        client.publishWith()
            .topic(topic)
            .payload(payload.toByteArray(StandardCharsets.UTF_8))
            .send()
            .get(5, TimeUnit.SECONDS)
    }

    private fun topic(service: String, action: String): String =
        "/remoteapp/tv/$service/${topicClientId.ifBlank { clientId }}/actions/$action"

    private fun requireConnected() {
        check(isConnected && mqtt != null) { "Connect to the TV first." }
    }

    private fun buildKeyManagerFactory(): KeyManagerFactory {
        val certDer = readPemBlock(
            assetName = "vidaa_client.pem",
            beginMarker = "-----BEGIN CERTIFICATE-----",
            endMarker = "-----END CERTIFICATE-----",
        )
        val keyDer = readPemBlock(
            assetName = "vidaa_client.key",
            beginMarker = "-----BEGIN PRIVATE KEY-----",
            endMarker = "-----END PRIVATE KEY-----",
        )

        val certificate = CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(certDer)) as X509Certificate
        val privateKey = KeyFactory.getInstance("RSA")
            .generatePrivate(PKCS8EncodedKeySpec(keyDer))

        val password = "vidaa".toCharArray()
        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
        keyStore.load(null)
        keyStore.setKeyEntry("vidaa-client", privateKey, password, arrayOf(certificate))

        return KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
            init(keyStore, password)
        }
    }

    private fun readPemBlock(assetName: String, beginMarker: String, endMarker: String): ByteArray {
        val text = appContext.assets.open(assetName).bufferedReader().use { it.readText() }
        val body = text.substringAfter(beginMarker)
            .substringBefore(endMarker)
            .replace(Regex("\\s"), "")
        require(body.isNotBlank()) { "Invalid $assetName" }
        return Base64.decode(body, Base64.DEFAULT)
    }

    private fun generateCredentials(deviceId: String, method: AuthMethod): Credentials {
        val timestamp = System.currentTimeMillis() / 1000L
        val race = "$PATTERN\$$deviceId"
        val raceHash = md5(race).take(6)
        val generatedClientId = "$deviceId\$his\$${raceHash}_vidaacommon_001"

        val usernameTime = when (method) {
            AuthMethod.LEGACY -> timestamp
            AuthMethod.MIDDLE, AuthMethod.MODERN -> timestamp xor TIME_XOR_CONSTANT
        }
        val username = "his\$$usernameTime"

        val suffix = if (method == AuthMethod.MODERN) VALUE_SUFFIX_MODERN else VALUE_SUFFIX_LEGACY
        val remainder = timestamp.toString().sumOf { it.digitToInt() } % 10
        val valueHash = md5("his$remainder$suffix").take(6)
        val password = md5("$timestamp\$$valueHash")

        return Credentials(generatedClientId, username, password)
    }

    private fun md5(value: String): String =
        MessageDigest.getInstance("MD5")
            .digest(value.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02X".format(it) }

    private fun loadOrCreateDeviceId(): String {
        prefs.getString(KEY_DEVICE_ID, null)?.let { return it }

        val bytes = ByteArray(6)
        SecureRandom().nextBytes(bytes)
        bytes[0] = ((bytes[0].toInt() and 0xFE) or 0x02).toByte()
        val id = bytes.joinToString(":") { "%02x".format(it) }
        prefs.edit().putString(KEY_DEVICE_ID, id).apply()
        return id
    }

    private fun loadStoredCredentials(host: String): StoredCredentials? {
        val prefix = "tv.$host."
        val storedClientId = prefs.getString(prefix + "client_id", null) ?: return null
        val username = prefs.getString(prefix + "username", null) ?: return null
        val accessToken = prefs.getString(prefix + "access_token", null) ?: return null
        val refreshToken = prefs.getString(prefix + "refresh_token", "") ?: ""
        return StoredCredentials(storedClientId, username, accessToken, refreshToken)
    }

    private fun saveStoredCredentials(
        host: String,
        clientId: String,
        username: String,
        accessToken: String,
        refreshToken: String,
    ) {
        val prefix = "tv.$host."
        prefs.edit()
            .putString(prefix + "client_id", clientId)
            .putString(prefix + "username", username)
            .putString(prefix + "access_token", accessToken)
            .putString(prefix + "refresh_token", refreshToken)
            .apply()
    }

    private fun clearStoredCredentials(host: String) {
        val prefix = "tv.$host."
        prefs.edit()
            .remove(prefix + "client_id")
            .remove(prefix + "username")
            .remove(prefix + "access_token")
            .remove(prefix + "refresh_token")
            .apply()
    }

    private enum class AuthMethod {
        MODERN,
        MIDDLE,
        LEGACY,
    }

    private data class Credentials(
        val clientId: String,
        val username: String,
        val password: String,
    )

    private data class StoredCredentials(
        val clientId: String,
        val username: String,
        val accessToken: String,
        val refreshToken: String,
    )

    private companion object {
        const val PATTERN = "38D65DC30F45109A369A86FCE866A85B"
        const val VALUE_SUFFIX_MODERN = "h!i@s#\$v%i^d&a*a"
        const val VALUE_SUFFIX_LEGACY = "h*i&s%e!r^v0i1c9"
        const val TIME_XOR_CONSTANT = 0x569814772B03A968L

        const val STATIC_USERNAME = "hisenseservice"
        const val STATIC_PASSWORD = "multimqttservice"

        const val KEY_DEVICE_ID = "mobile_device_id"
    }
}
