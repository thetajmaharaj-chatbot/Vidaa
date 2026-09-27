package com.example.vidaaremote.model

data class TvDevice(
    val name: String,
    val host: String,
    val port: Int = 36669,
    val model: String? = null,
)
