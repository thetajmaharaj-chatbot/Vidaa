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

enum class AppScreen { DISCOVERY, PAIRING, REMOTE }

@Composable
fun VidaaRemoteApp(client: VidaaRemoteClient) {
    var screen by remember { mutableStateOf(AppScreen.DISCOVERY) }
    var selectedDevice by remember { mutableStateOf<TvDevice?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("VIDAA Remote") },
                actions = {
                    if (screen != AppScreen.DISCOVERY) {
                        IconButton(onClick = {
                            client.disconnect()
                            selectedDevice = null
                            screen = AppScreen.DISCOVERY
                            message = null
                        }) {
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
            message?.let {
                AssistChip(onClick = {}, label = { Text(it) })
                Spacer(Modifier.height(16.dp))
            }

            when (screen) {
                AppScreen.DISCOVERY -> DiscoveryScreen(
                    onConnect = { device ->
                        client.connect(device)
                            .onSuccess {
                                selectedDevice = device
                                client.startPairing()
                                message = "Connected to ${device.host}. Enter the PIN shown on the TV."
                                screen = AppScreen.PAIRING
                            }
                            .onFailure { message = it.message }
                    }
                )

                AppScreen.PAIRING -> PairingScreen(
                    device = selectedDevice,
                    onPair = { pin ->
                        client.authenticate(pin)
                            .onSuccess {
                                message = "Paired. Remote controls are ready."
                                screen = AppScreen.REMOTE
                            }
                            .onFailure { message = it.message }
                    }
                )

                AppScreen.REMOTE -> RemoteScreen(
                    device = selectedDevice,
                    onKey = { key ->
                        client.sendKey(key)
                            .onSuccess { message = "Sent ${key.wireValue}" }
                            .onFailure { message = it.message }
                    }
                )
            }
        }
    }
}

@Composable
private fun DiscoveryScreen(onConnect: (TvDevice) -> Unit) {
    var host by remember { mutableStateOf("192.168.1.100") }

    Text("Connect to your TV", style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(8.dp))
    Text("For the first milestone, enter the TV's IP address manually. Automatic LAN discovery comes next.")
    Spacer(Modifier.height(24.dp))

    OutlinedTextField(
        value = host,
        onValueChange = { host = it.trim() },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("TV IP address") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
    )

    Spacer(Modifier.height(16.dp))
    Button(
        modifier = Modifier.fillMaxWidth(),
        onClick = {
            if (host.isNotBlank()) {
                onConnect(TvDevice(name = "Hisense VIDAA TV", host = host))
            }
        }
    ) {
        Icon(Icons.Default.Tv, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("Connect")
    }

    Spacer(Modifier.height(16.dp))
    Text("VIDAA control service: MQTT/TLS • TCP 36669", style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun PairingScreen(device: TvDevice?, onPair: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }

    Text("Pair with TV", style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(8.dp))
    Text("Connected to ${device?.host ?: "TV"}. Enter the code displayed on the television.")
    Spacer(Modifier.height(24.dp))

    OutlinedTextField(
        value = pin,
        onValueChange = { pin = it.filter(Char::isDigit).take(8) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Pairing PIN") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        singleLine = true
    )

    Spacer(Modifier.height(16.dp))
    Button(
        modifier = Modifier.fillMaxWidth(),
        enabled = pin.length >= 4,
        onClick = { onPair(pin) }
    ) {
        Text("Pair")
    }
}

@Composable
private fun RemoteScreen(device: TvDevice?, onKey: (VidaaKey) -> Unit) {
    Text(device?.name ?: "VIDAA TV", style = MaterialTheme.typography.headlineMedium)
    Text(device?.host.orEmpty(), style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(20.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        RemoteIconButton(Icons.Default.PowerSettingsNew, "Power") { onKey(VidaaKey.POWER) }
        RemoteIconButton(Icons.Default.Home, "Home") { onKey(VidaaKey.HOME) }
        RemoteIconButton(Icons.Default.ArrowBack, "Back") { onKey(VidaaKey.BACK) }
    }

    Spacer(Modifier.height(24.dp))

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        RemoteIconButton(Icons.Default.KeyboardArrowUp, "Up") { onKey(VidaaKey.UP) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            RemoteIconButton(Icons.Default.KeyboardArrowLeft, "Left") { onKey(VidaaKey.LEFT) }
            Spacer(Modifier.width(16.dp))
            FilledTonalButton(onClick = { onKey(VidaaKey.OK) }, modifier = Modifier.size(76.dp)) {
                Text("OK")
            }
            Spacer(Modifier.width(16.dp))
            RemoteIconButton(Icons.Default.KeyboardArrowRight, "Right") { onKey(VidaaKey.RIGHT) }
        }
        RemoteIconButton(Icons.Default.KeyboardArrowDown, "Down") { onKey(VidaaKey.DOWN) }
    }

    Spacer(Modifier.height(24.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        Button(onClick = { onKey(VidaaKey.VOLUME_DOWN) }) { Text("VOL −") }
        Button(onClick = { onKey(VidaaKey.MUTE) }) { Icon(Icons.Default.VolumeOff, "Mute") }
        Button(onClick = { onKey(VidaaKey.VOLUME_UP) }) { Text("VOL +") }
    }

    Spacer(Modifier.height(16.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        OutlinedButton(onClick = { onKey(VidaaKey.REWIND) }) { Icon(Icons.Default.FastRewind, "Rewind") }
        OutlinedButton(onClick = { onKey(VidaaKey.PLAY) }) { Icon(Icons.Default.PlayArrow, "Play") }
        OutlinedButton(onClick = { onKey(VidaaKey.PAUSE) }) { Icon(Icons.Default.Pause, "Pause") }
        OutlinedButton(onClick = { onKey(VidaaKey.FAST_FORWARD) }) { Icon(Icons.Default.FastForward, "Fast forward") }
    }
}

@Composable
private fun RemoteIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    FilledTonalIconButton(onClick = onClick, modifier = Modifier.size(64.dp)) {
        Icon(icon, contentDescription = label)
    }
}
