package com.example.voicephonecontrol

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    private var recognizer: SpeechRecognizer? = null
    private lateinit var controller: CommandController

    private val micPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startListening()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        controller = CommandController(this)

        setContent {
            MaterialTheme {
                var transcript by remember { mutableStateOf("Tap the microphone and speak") }
                var result by remember { mutableStateOf("Ready") }
                var listening by remember { mutableStateOf(false) }

                fun listen() {
                    if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                        micPermission.launch(Manifest.permission.RECORD_AUDIO)
                        return
                    }
                    startListening(
                        onReady = { listening = true; result = "Listening…" },
                        onText = { text ->
                            listening = false
                            transcript = text
                            result = controller.execute(text)
                        },
                        onError = { error ->
                            listening = false
                            result = error
                        }
                    )
                }

                Scaffold { padding ->
                    Column(
                        modifier = Modifier
                            .padding(padding)
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        Spacer(Modifier.height(16.dp))
                        Text("Voice Phone Control", style = MaterialTheme.typography.headlineMedium)
                        Text("No AI • fixed voice commands", style = MaterialTheme.typography.bodyMedium)

                        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(18.dp)) {
                                Text("Heard", style = MaterialTheme.typography.labelLarge)
                                Text(transcript)
                                Spacer(Modifier.height(12.dp))
                                Text("Status", style = MaterialTheme.typography.labelLarge)
                                Text(result)
                            }
                        }

                        Button(
                            onClick = { listen() },
                            enabled = !listening,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                        ) {
                            Icon(Icons.Default.Mic, contentDescription = null)
                            Spacer(Modifier.width(10.dp))
                            Text(if (listening) "Listening…" else "Tap to speak")
                        }

                        OutlinedButton(
                            onClick = {
                                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = null)
                            Spacer(Modifier.width(10.dp))
                            Text("Enable Accessibility Control")
                        }

                        Text(
                            "Try: Open WhatsApp • Go home • Go back • Scroll down • Volume up • Type hello • Tap send • Read my screen",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }

    private fun startListening(
        onReady: () -> Unit = {},
        onText: (String) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            onError("Speech recognition is not available on this phone")
            return
        }
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) = onReady()
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onError(error: Int) = onError("Voice recognition error: $error")
                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val best = matches?.firstOrNull()
                    if (best != null) onText(best) else onError("I didn't hear a command")
                }
                override fun onPartialResults(partialResults: Bundle?) = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak a phone command")
            }
            startListening(intent)
        }
    }

    override fun onDestroy() {
        recognizer?.destroy()
        controller.shutdown()
        super.onDestroy()
    }
}
