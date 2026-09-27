# VIDAA Remote Starter

Android/Kotlin + Jetpack Compose starter for a local Hisense/VIDAA remote-control app.

## Current milestone

- Manual TV IP entry
- PIN-pairing UI
- D-pad / Home / Back / Power
- Volume controls
- Basic playback controls
- Clean protocol interface ready for a real MQTT/TLS implementation
- HiveMQ MQTT client dependency included

The current `PrototypeVidaaClient` intentionally does **not** send network commands yet. It lets the app UI and flow be developed safely before we lock in the firmware-specific VIDAA authentication handshake.

## VIDAA protocol target

- MQTT 3.1.1
- TLS
- TCP port 36669
- Pairing PIN displayed by TV
- Remote key topic family under `/remoteapp/...`

## Open

Open the folder in Android Studio Quail 2026.1.4+ (or another compatible version), sync Gradle, and run on a physical Android device on the same network as the TV.

## Next implementation step

Replace `PrototypeVidaaClient` with `MqttVidaaClient` implementing:

1. TLS socket/client configuration
2. VIDAA protocol/profile detection
3. MQTT subscriptions
4. pairing request
5. PIN authentication
6. credential persistence
7. `sendKey()` publishing
8. automatic reconnect
9. LAN discovery

## Automatic APK builds on GitHub

This project includes `.github/workflows/build-apk.yml`. Every push to `main` and every pull request builds a debug APK with:

- JDK 17
- Android Gradle Plugin 9.4.0
- Gradle 9.6.0
- Android API 37
- Android Build Tools 36.0.0

The resulting `app-debug.apk` is uploaded as the GitHub Actions artifact `vidaa-remote-debug-apk` and retained for 30 days. You can also run the workflow manually from the Actions tab.
