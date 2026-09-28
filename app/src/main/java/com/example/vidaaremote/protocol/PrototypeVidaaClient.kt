package com.example.vidaaremote.protocol

import com.example.vidaaremote.model.TvDevice

class PrototypeVidaaClient : VidaaRemoteClient {
    override var connectedDevice: TvDevice? = null
        private set

    override var isConnected: Boolean = false
        private set

    override var isAuthenticated: Boolean = false
        private set

    override suspend fun connect(device: TvDevice): Result<Unit> {
        connectedDevice = device
        isConnected = true
        return Result.success(Unit)
    }

    override suspend fun startPairing(): Result<Unit> {
        return if (isConnected) Result.success(Unit)
        else Result.failure(IllegalStateException("Connect to a TV first"))
    }

    override suspend fun authenticate(pin: String): Result<Unit> {
        if (!isConnected) return Result.failure(IllegalStateException("Not connected"))
        if (!pin.matches(Regex("\\d{4,8}"))) {
            return Result.failure(IllegalArgumentException("Enter the PIN shown on the TV"))
        }
        isAuthenticated = true
        return Result.success(Unit)
    }

    override suspend fun startLegacyPairing(): Result<Unit> {
        if (!isConnected) return Result.failure(IllegalStateException("Not connected"))
        isAuthenticated = false
        return Result.success(Unit)
    }

    override suspend fun sendKey(key: VidaaKey): Result<Unit> {
        return if (isConnected && isAuthenticated) Result.success(Unit)
        else Result.failure(IllegalStateException("TV is not paired"))
    }

    override suspend fun sendKeyboardKey(key: String): Result<Unit> {
        return if (isConnected && isAuthenticated && key.isNotBlank()) Result.success(Unit)
        else Result.failure(IllegalStateException("TV is not paired or key is empty"))
    }

    override suspend fun sendText(text: String): Result<Unit> {
        return if (isConnected && isAuthenticated && text.isNotBlank()) Result.success(Unit)
        else Result.failure(IllegalStateException("TV is not paired or text is empty"))
    }

    override suspend fun launchYouTube(): Result<Unit> {
        return if (isConnected && isAuthenticated) Result.success(Unit)
        else Result.failure(IllegalStateException("TV is not paired"))
    }

    override fun disconnect() {
        connectedDevice = null
        isConnected = false
        isAuthenticated = false
    }
}
