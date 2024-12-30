package com.example.voiceapp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.util.*

class SpeechRecognitionManager(
    private val context: Context,
    private val callback: SpeechRecognitionCallback
) {
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var shouldContinue = true // 連続認識を制御するフラグ

    interface SpeechRecognitionCallback {
        fun onRecognitionStarted()
        fun onRecognitionResult(text: String)
        fun onRecognitionError(errorMessage: String)
        fun onPartialResult(text: String)
    }

    init {
        initializeSpeechRecognizer()
    }

    @Synchronized
    private fun initializeSpeechRecognizer() {
        try {
            speechRecognizer?.destroy()
            speechRecognizer = null
            System.gc()  // ガベージコレクションを促す
            
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(createRecognitionListener())
            }
        } catch (e: Exception) {
            Log.e("SpeechRecognitionManager", "Error in initialization: ${e.message}")
            callback.onRecognitionError("音声認識の初期化に失敗しました")
        }
    }

    private fun createRecognitionListener() = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            callback.onRecognitionStarted()
        }

        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}

        override fun onError(error: Int) {
            val errorMessage = when (error) {
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "ネットワークタイムアウト"
                SpeechRecognizer.ERROR_NETWORK -> "ネットワークエラー"
                SpeechRecognizer.ERROR_AUDIO -> "オーディオエラー"
                SpeechRecognizer.ERROR_SERVER -> "サーバーエラー"
                SpeechRecognizer.ERROR_CLIENT -> "クライアントエラー"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "音声入力タイムアウト"
                SpeechRecognizer.ERROR_NO_MATCH -> "一致するものがありません。もう一度お試しください。"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "認識エンジンがビジー状態"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "権限が不足しています"
                else -> "不明なエラー"
            }
            Log.e("SpeechRecognitionManager", "Error occurred: $error - $errorMessage")
            callback.onRecognitionError(errorMessage)

            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (isListening) startListeningInternal()
                    }, 1000)
                }
                else -> stopListening()
            }
        }

        override fun onResults(results: Bundle?) {
            results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.let {
                if (it.isNotEmpty()) {
                    callback.onRecognitionResult(it[0])
                    
                    // 連続認識の制御
                    if (shouldContinue && isListening) {
                        Handler(Looper.getMainLooper()).postDelayed({
                            startListeningInternal()
                        }, 1000)
                    }
                }
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.let {
                if (it.isNotEmpty()) {
                    callback.onPartialResult(it[0])
                }
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {
            Log.d("SpeechRecognitionManager", "Event: $eventType")
        }
    }

    @Synchronized
    fun startListening() {
        shouldContinue = true
        startListeningInternal()
    }

    private fun startListeningInternal() {
        if (!shouldContinue) return
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            callback.onRecognitionError("音声認識が利用できません")
            return
        }

        try {
            Handler(Looper.getMainLooper()).postDelayed({
                if (!shouldContinue) return@postDelayed

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ja-JP")
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                }

                isListening = true
                speechRecognizer?.startListening(intent)
            }, 500)
        } catch (e: Exception) {
            Log.e("SpeechRecognitionManager", "Error starting speech recognition: ${e.message}")
            callback.onRecognitionError("音声認識の開始に失敗しました")
            isListening = false
        }
    }

    @Synchronized
    fun stopListening() {
        shouldContinue = false
        isListening = false
        
        try {
            speechRecognizer?.let { recognizer ->
                recognizer.stopListening()
                Handler(Looper.getMainLooper()).postDelayed({
                    recognizer.cancel()
                }, 200)
            }
        } catch (e: Exception) {
            Log.e("SpeechRecognitionManager", "Error stopping recognition: ${e.message}")
        }
    }

    fun reinitialize() {
        destroy()
        Handler(Looper.getMainLooper()).postDelayed({
            initializeSpeechRecognizer()
        }, 500)
    }

    fun destroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    fun isListening() = isListening
}
