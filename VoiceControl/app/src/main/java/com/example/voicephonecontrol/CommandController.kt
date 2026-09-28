package com.example.voicephonecontrol

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.provider.Settings
import android.speech.tts.TextToSpeech
import java.util.Locale

class CommandController(private val context: Context) {
    private var tts: TextToSpeech? = null

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) tts?.language = Locale.getDefault()
        }
    }

    fun execute(raw: String): String {
        val command = raw.trim()
        val lower = command.lowercase()
        val service = PhoneControlService.instance

        return when {
            lower == "go home" || lower == "home" -> {
                if (service?.goHome() == true) "Home" else accessibilityRequired()
            }
            lower == "go back" || lower == "back" -> {
                if (service?.goBack() == true) "Back" else accessibilityRequired()
            }
            lower.contains("recent") -> {
                if (service?.openRecents() == true) "Recents" else accessibilityRequired()
            }
            lower == "scroll down" || lower == "scroll" -> {
                if (service?.scroll(true) == true) "Scrolled down" else "Couldn't scroll"
            }
            lower == "scroll up" -> {
                if (service?.scroll(false) == true) "Scrolled up" else "Couldn't scroll"
            }
            lower.startsWith("type ") -> {
                val text = command.substringAfter(" ", "")
                if (text.isBlank()) "Say what you want typed"
                else if (service?.typeText(text) == true) "Typed: $text" else "Tap a text field first"
            }
            lower.startsWith("tap ") || lower.startsWith("click ") -> {
                val label = command.substringAfter(" ", "")
                if (service?.clickByLabel(label) == true) "Tapped $label" else "Couldn't find $label"
            }
            lower == "read my screen" || lower == "read screen" || lower == "what's on my screen" -> {
                val text = service?.readScreenText() ?: accessibilityRequired()
                speak(text)
                text
            }
            lower == "volume up" || lower == "turn volume up" -> adjustVolume(AudioManager.ADJUST_RAISE, "Volume up")
            lower == "volume down" || lower == "turn volume down" -> adjustVolume(AudioManager.ADJUST_LOWER, "Volume down")
            lower == "mute" || lower == "mute phone" -> adjustVolume(AudioManager.ADJUST_MUTE, "Muted")
            lower == "unmute" || lower == "unmute phone" -> adjustVolume(AudioManager.ADJUST_UNMUTE, "Unmuted")
            lower.startsWith("open ") -> openApp(command.substringAfter(" "))
            lower == "open settings" -> {
                context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                "Settings opened"
            }
            else -> "Command not recognised"
        }
    }

    private fun adjustVolume(direction: Int, message: String): String {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
        return message
    }

    private fun openApp(name: String): String {
        val pm = context.packageManager
        val target = name.trim().lowercase()
        val known = mapOf(
            "whatsapp" to "com.whatsapp",
            "youtube" to "com.google.android.youtube",
            "chrome" to "com.android.chrome",
            "facebook" to "com.facebook.katana",
            "instagram" to "com.instagram.android",
            "tiktok" to "com.zhiliaoapp.musically",
            "camera" to "camera",
            "settings" to "settings"
        )

        if (target == "settings") {
            context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return "Settings opened"
        }
        if (target == "camera") {
            val intent = Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return runCatching { context.startActivity(intent); "Camera opened" }
                .getOrElse { "Camera not available" }
        }

        val pkg = known[target] ?: run {
            val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            pm.queryIntentActivities(launcher, 0).firstOrNull { info ->
                val label = info.loadLabel(pm).toString().lowercase()
                label == target || label.contains(target)
            }?.activityInfo?.packageName
        }

        if (pkg == null) return "App not found: $name"
        val intent = pm.getLaunchIntentForPackage(pkg) ?: return "Can't open $name"
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return "$name opened"
    }

    private fun accessibilityRequired(): String = "Enable Voice Phone Control in Accessibility settings"

    private fun speak(text: String) {
        tts?.speak(text.take(3500), TextToSpeech.QUEUE_FLUSH, null, "screen_read")
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }
}
