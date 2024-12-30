package com.example.voiceapp

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.*

/**
 * テキスト読み上げを管理するクラス
 * 
 * 機能：
 * - 日本語テキストの音声合成
 * - 初期化状態の管理
 * - 読み上げ開始/停止の制御
 * 
 * 使用上の注意：
 * - 初期化完了前の speak 呼び出しは無視される
 * - 複数の読み上げリクエストは後のものが優先される
 * - アプリ終了時に必ず shutdown を呼び出す
 */
class TextToSpeechManager(private val context: Context) {
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var onInitializedCallback: (() -> Unit)? = null

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

    fun speak(text: String) {
        if (!isInitialized) {
            Log.w("TextToSpeechManager", "TextToSpeech not initialized yet")
            return
        }

        tts?.let {
            if (it.isSpeaking) {
                it.stop()
            }
            it.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        } ?: run {
            Log.e("TextToSpeechManager", "TextToSpeech is null")
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}