package com.example.vidaaremote.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.vidaaremote.model.TvDevice
import com.example.vidaaremote.protocol.VidaaKey
import com.example.vidaaremote.protocol.VidaaRemoteClient
import kotlinx.coroutines.launch

enum class AppScreen { DISCOVERY, PAIRING, REMOTE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VidaaRemoteApp(client: VidaaRemoteClient) {
    val scope = rememberCoroutineScope()
    var screen by remember { mutableStateOf(AppScreen.DISCOVERY) }
    var selectedDevice by remember { mutableStateOf<TvDevice?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("VIDAA Remote") },
                actions = {
                    if (screen != AppScreen.DISCOVERY) {
                        IconButton(
                            enabled = !busy,
                            onClick = {
                                client.disconnect()
                                selectedDevice = null
                                screen = AppScreen.DISCOVERY
                                message = null
                            }
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Disconnect")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(20.dp)
        ) {
            if (busy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
            }

            message?.let {
                AssistChip(onClick = {}, label = { Text(it) })
                Spacer(Modifier.height(16.dp))
            }

            when (screen) {
                AppScreen.DISCOVERY -> DiscoveryScreen(
                    busy = busy,
                    onConnect = { device ->
                        scope.launch {
                            busy = true
                            message = "Connecting to ${device.host}:36669…"
                            client.connect(device)
                                .onSuccess {
                                    selectedDevice = device
                                    if (client.isAuthenticated) {
                                        message = "Connected using saved pairing."
                                        screen = AppScreen.REMOTE
                                    } else {
                                        client.startPairing()
                                            .onSuccess {
                                                message = "PIN requested. Enter the code shown on your TV."
                                                screen = AppScreen.PAIRING
                                            }
                                            .onFailure { message = it.message }
                                    }
                                }
                                .onFailure { message = it.message }
                            busy = false
                        }
                    }
                )

                AppScreen.PAIRING -> PairingScreen(
                    device = selectedDevice,
                    busy = busy,
                    onRestartPairing = {
                        scope.launch {
                            busy = true
                            client.startPairing()
                                .onSuccess { message = "PIN requested again. Check the TV screen." }
                                .onFailure { message = it.message }
                            busy = false
                        }
                    },
                    onPair = { pin ->
                        scope.launch {
                            busy = true
                            message = "Checking PIN…"
                            client.authenticate(pin)
                                .onSuccess {
                                    message = "Paired successfully. Remote controls are live."
                                    screen = AppScreen.REMOTE
                                }
                                .onFailure { message = it.message }
                            busy = false
                        }
                    },
                    onLegacyPairing = {
                        scope.launch {
                            busy = true
                            message = "Starting P0218 / RemoteNOW pairing…"
                            client.startLegacyPairing()
                                .onSuccess {
                                    message = "PIN displayed by legacy mode. Enter that PIN above, then tap Pair TV."
                                }
                                .onFailure { message = it.message }
                            busy = false
                        }
                    }
                )

                AppScreen.REMOTE -> RemoteScreen(
                    device = selectedDevice,
                    busy = busy,
                    onKey = { key ->
                        scope.launch {
                            client.sendKey(key)
                                .onFailure { message = it.message }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun DiscoveryScreen(
    busy: Boolean,
    onConnect: (TvDevice) -> Unit,
) {
    var host by remember { mutableStateOf("") }

    Text("Connect to your VIDAA TV", style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(8.dp))
    Text("Your phone and TV must be on the same Wi-Fi. Enter the TV's local IP address.")
    Spacer(Modifier.height(24.dp))

    OutlinedTextField(
        value = host,
        onValueChange = { host = it.trim() },
        modifier = Modifier.fillMaxWidth(),
        enabled = !busy,
        label = { Text("TV IP address") },
        placeholder = { Text("192.168.1.100") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
    )

    Spacer(Modifier.height(16.dp))
    Button(
        modifier = Modifier.fillMaxWidth(),
        enabled = host.isNotBlank() && !busy,
        onClick = {
            onConnect(TvDevice(name = "Hisense VIDAA TV", host = host))
        }
    ) {
        Icon(Icons.Default.Tv, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("Connect & pair")
    }

    Spacer(Modifier.height(16.dp))
    Text("Local connection: MQTT 3.1.1 over mutual TLS • port 36669", style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun PairingScreen(
    device: TvDevice?,
    busy: Boolean,
    onRestartPairing: () -> Unit,
    onPair: (String) -> Unit,
    onLegacyPairing: () -> Unit,
) {
    var pin by remember { mutableStateOf("") }

    Text("Pair with TV", style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(8.dp))
    Text("Connected to ${device?.host ?: "TV"}. A PIN should be visible on the television.")
    Spacer(Modifier.height(24.dp))

    OutlinedTextField(
        value = pin,
        onValueChange = { pin = it.filter(Char::isDigit).take(8) },
        modifier = Modifier.fillMaxWidth(),
        enabled = !busy,
        label = { Text("TV pairing PIN") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        singleLine = true
    )

    Spacer(Modifier.height(16.dp))
    Button(
        modifier = Modifier.fillMaxWidth(),
        enabled = pin.length >= 4 && !busy,
        onClick = { onPair(pin) }
    ) {
        Text("Pair TV")
    }

    TextButton(
        modifier = Modifier.fillMaxWidth(),
        enabled = !busy,
        onClick = onRestartPairing,
    ) {
        Text("Request a new PIN")
    }

    Spacer(Modifier.height(8.dp))
    HorizontalDivider()
    Spacer(Modifier.height(12.dp))

    Text(
        "P0218 firmware shows its PIN after the older RemoteNOW connection is started.",
        style = MaterialTheme.typography.bodySmall,
    )
    Spacer(Modifier.height(10.dp))
    OutlinedButton(
        modifier = Modifier.fillMaxWidth(),
        enabled = !busy,
        onClick = onLegacyPairing,
    ) {
        Text("Show PIN — Legacy / P0218 mode")
    }
}

@Composable
private fun RemoteScreen(
    device: TvDevice?,
    busy: Boolean,
    onKey: (VidaaKey) -> Unit,
) {
    Text(device?.name ?: "VIDAA TV", style = MaterialTheme.typography.headlineMedium)
    Text("${device?.host.orEmpty()} • connected", style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(20.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        RemoteIconButton(Icons.Default.PowerSettingsNew, "Power", busy) { onKey(VidaaKey.POWER) }
        RemoteIconButton(Icons.Default.Home, "Home", busy) { onKey(VidaaKey.HOME) }
        RemoteIconButton(Icons.Default.ArrowBack, "Back", busy) { onKey(VidaaKey.BACK) }
    }

    Spacer(Modifier.height(24.dp))

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        RemoteIconButton(Icons.Default.KeyboardArrowUp, "Up", busy) { onKey(VidaaKey.UP) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            RemoteIconButton(Icons.Default.KeyboardArrowLeft, "Left", busy) { onKey(VidaaKey.LEFT) }
            Spacer(Modifier.width(16.dp))
            FilledTonalButton(
                enabled = !busy,
                onClick = { onKey(VidaaKey.OK) },
                modifier = Modifier.size(76.dp),
            ) {
                Text("OK")
            }
            Spacer(Modifier.width(16.dp))
            RemoteIconButton(Icons.Default.KeyboardArrowRight, "Right", busy) { onKey(VidaaKey.RIGHT) }
        }
        RemoteIconButton(Icons.Default.KeyboardArrowDown, "Down", busy) { onKey(VidaaKey.DOWN) }
    }

    Spacer(Modifier.height(24.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        Button(enabled = !busy, onClick = { onKey(VidaaKey.VOLUME_DOWN) }) { Text("VOL −") }
        Button(enabled = !busy, onClick = { onKey(VidaaKey.MUTE) }) { Icon(Icons.Default.VolumeOff, "Mute") }
        Button(enabled = !busy, onClick = { onKey(VidaaKey.VOLUME_UP) }) { Text("VOL +") }
    }

    Spacer(Modifier.height(16.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        OutlinedButton(enabled = !busy, onClick = { onKey(VidaaKey.REWIND) }) { Icon(Icons.Default.FastRewind, "Rewind") }
        OutlinedButton(enabled = !busy, onClick = { onKey(VidaaKey.PLAY) }) { Icon(Icons.Default.PlayArrow, "Play") }
        OutlinedButton(enabled = !busy, onClick = { onKey(VidaaKey.PAUSE) }) { Icon(Icons.Default.Pause, "Pause") }
        OutlinedButton(enabled = !busy, onClick = { onKey(VidaaKey.FAST_FORWARD) }) { Icon(Icons.Default.FastForward, "Fast forward") }
    }
}

@Composable
private fun RemoteIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    busy: Boolean,
    onClick: () -> Unit,
) {
    FilledTonalIconButton(
        enabled = !busy,
        onClick = onClick,
        modifier = Modifier.size(64.dp),
    ) {
        Icon(icon, contentDescription = label)
    }
}
