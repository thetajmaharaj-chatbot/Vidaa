package com.example.vidaaremote.protocol

import com.example.vidaaremote.model.TvDevice

class PrototypeVidaaClient : VidaaRemoteClient {
    override var connectedDevice: TvDevice? = null
        private set

    override var isConnected: Boolean = false
        private set

    override var isAuthenticated: Boolean = false
        private set

    override fun connect(device: TvDevice): Result<Unit> {
        connectedDevice = device
        isConnected = true
        return Result.success(Unit)
    }

    override fun startPairing(): Result<Unit> {
        return if (isConnected) Result.success(Unit)
        else Result.failure(IllegalStateException("Connect to a TV first"))
    }

    override fun authenticate(pin: String): Result<Unit> {
        if (!isConnected) return Result.failure(IllegalStateException("Not connected"))
        if (!pin.matches(Regex("\\d{4,8}"))) {
            return Result.failure(IllegalArgumentException("Enter the PIN shown on the TV"))
        }
        isAuthenticated = true
        return Result.success(Unit)
    }

    override fun sendKey(key: VidaaKey): Result<Unit> {
        return if (isConnected && isAuthenticated) Result.success(Unit)
        else Result.failure(IllegalStateException("TV is not paired"))
    }

    override fun disconnect() {
        connectedDevice = null
        isConnected = false
        isAuthenticated = false
    }
}
