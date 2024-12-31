package com.example.voiceapp

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.os.Bundle
import android.util.Log
import java.util.Locale

class TextToSpeechManager(private val context: Context) {
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var onInitializedCallback: (() -> Unit)? = null
    private var onUtteranceComplete: (() -> Unit)? = null

    fun initialize(callback: (() -> Unit)? = null) {
        onInitializedCallback = callback
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale.JAPAN)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e("TextToSpeechManager", "Language not supported")
                } else {
                    isInitialized = true
                    onInitializedCallback?.invoke()
                }
            } else {
                Log.e("TextToSpeechManager", "Initialization failed")
            }
        }
    }

    fun speak(text: String, onComplete: (() -> Unit)? = null) {
        if (!isInitialized) {
            Log.w("TextToSpeechManager", "TextToSpeech not initialized yet")
            return
        }

        tts?.let {
            if (it.isSpeaking) {
                it.stop()
            }
            val utteranceId = "utterance_${System.currentTimeMillis()}"
            it.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    onComplete?.invoke()
                }
                override fun onError(utteranceId: String?) {}
            })
            val params = Bundle()
            params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
            it.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }
}
