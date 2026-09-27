package com.example.vidaaremote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.example.vidaaremote.protocol.MqttVidaaClient
import com.example.vidaaremote.ui.VidaaRemoteApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val client = MqttVidaaClient(applicationContext)

        setContent {
            MaterialTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    VidaaRemoteApp(client = client)
                }
            }
        }
    }
}
