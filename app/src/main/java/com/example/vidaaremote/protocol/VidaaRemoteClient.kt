package com.example.vidaaremote.protocol

import com.example.vidaaremote.model.TvDevice

interface VidaaRemoteClient {
    val connectedDevice: TvDevice?
    val isConnected: Boolean
    val isAuthenticated: Boolean

    fun connect(device: TvDevice): Result<Unit>
    fun startPairing(): Result<Unit>
    fun authenticate(pin: String): Result<Unit>
    fun sendKey(key: VidaaKey): Result<Unit>
    fun disconnect()
}
