package com.example.vidaaremote.protocol

import com.example.vidaaremote.model.TvDevice

interface VidaaRemoteClient {
    val connectedDevice: TvDevice?
    val isConnected: Boolean
    val isAuthenticated: Boolean

    suspend fun connect(device: TvDevice): Result<Unit>
    suspend fun startPairing(): Result<Unit>
    suspend fun authenticate(pin: String): Result<Unit>
    suspend fun startLegacyPairing(): Result<Unit>
    suspend fun sendKey(key: VidaaKey): Result<Unit>
    suspend fun sendText(text: String): Result<Unit>
    suspend fun launchYouTube(): Result<Unit>
    fun disconnect()
}
