package com.example.voiceapp

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.*

class TextToSpeechManager(private val context: Context) {
    private var tts: TextToSpeech? = null

    fun initialize() {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale.JAPAN)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e("TextToSpeechManager", "Language not supported")
                }
            } else {
                Log.e("TextToSpeechManager", "Initialization failed")
            }
        }
    }

    fun speak(text: String) {
        tts?.let {
            if (it.isSpeaking) {
                it.stop()
            }
            it.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        } ?: run {
            Log.e("TextToSpeechManager", "TextToSpeech not initialized")
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}