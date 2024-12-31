package com.example.voiceapp

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.voiceapp.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * アプリケーションのメインアクティビティ
 * 
 * このクラスは以下の主要な機能を提供します：
 * 1. 音声認識とAI対話のUIインターフェース
 * 2. 音声認識マネージャーと連携した音声入力処理
 * 3. AIマネージャーを使用した対話処理
 * 4. テキスト読み上げ機能の管理
 * 5. チャットスタイルのメッセージ表示
 */
class MainActivity : AppCompatActivity(), SpeechRecognitionManager.SpeechRecognitionCallback {
    // プロパティの宣言を追加
    private lateinit var binding: ActivityMainBinding
    private lateinit var speechRecognitionManager: SpeechRecognitionManager
    private lateinit var aiManager: AIManager
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var textToSpeechManager: TextToSpeechManager
    private var currentState = UIState()

    /**
     * アクティビティの初期化
     * 
     * 以下の順序で初期化を行います：
     * 1. システムの音声設定
     * 2. レイアウトのバインディング
     * 3. TextToSpeechManagerの初期化
     * 4. SpeechRecognitionManagerの初期化
     * 5. チャットアダプターの設定
     * 6. AIManagerの初期化
     * 7. ボタンのセットアップ
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        // システムの音声をオフにする
        volumeControlStream = AudioManager.STREAM_MUSIC
        (getSystemService(Context.AUDIO_SERVICE) as AudioManager).apply {
            // 通知音を無効化
            adjustStreamVolume(AudioManager.STREAM_SYSTEM, AudioManager.ADJUST_MUTE, 0)
        }
        
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // TextToSpeechManagerの初期化と読み上げ
        textToSpeechManager = TextToSpeechManager(this)
        textToSpeechManager.initialize {
            startWelcomeSequence()
        }

        // SpeechRecognitionManagerの初期化
        speechRecognitionManager = SpeechRecognitionManager(this, this)

        // チャットアダプターの初期化
        chatAdapter = ChatAdapter()
        binding.chatRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = chatAdapter
        }

        aiManager = AIManager(this)
    }

    private fun startWelcomeSequence() {
        // ウェルカムメッセージを読み上げ
        textToSpeechManager.speak(getString(R.string.welcome_message))
        
        // 読み上げ完了を待ってから音声認識を開始（3秒後）
        Handler(Looper.getMainLooper()).postDelayed({
            checkPermissionAndStartListening()
        }, 3000)
    }

    /**
     * 音声認識の権限チェックと開始
     * 
     * 1. RECORD_AUDIO権限の確認
     * 2. 権限がない場合は要求
     * 3. 権限がある場合は音声認識を開始
     */
    private fun checkPermissionAndStartListening() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), PERMISSION_REQUEST_CODE)
            return
        }
        updateUIState(currentState.copy(isListening = true))
        speechRecognitionManager.startListening()
    }

    /**
     * UIの状態を更新し、表示を反映
     * 
     * 更新される要素：
     * - 音声認識ボタンの状態とアイコン
     * - キャンセルボタンの有効/無効状態
     * - エラーメッセージの表示
     * - 処理中の表示状態
     */
    private fun updateUIState(newState: UIState) {
        currentState = newState
        
        // エラーメッセージの表示
        currentState.error?.let {
            Toast.makeText(this, it, Toast.LENGTH_SHORT).show()
        }
    }

    // SpeechRecognitionCallback の実装
    override fun onRecognitionStarted() {
        updateUIState(currentState.copy(
            isListening = true,
            error = null
        ))
    }

    override fun onRecognitionResult(text: String) {
        updateUIState(currentState.copy(
            isProcessing = true
        ))
        chatAdapter.addMessage(ChatMessage(text, true))
        binding.chatRecyclerView.scrollToPosition(chatAdapter.itemCount - 1)
        getAIResponse(text)
    }

    override fun onRecognitionError(errorMessage: String) {
        updateUIState(currentState.copy(
            isListening = false,
            error = errorMessage
        ))
    }

    override fun onPartialResult(text: String) {
        // 必要に応じて部分認識結果を処理
    }

    /**
     * AIからの応答を取得し表示
     * 
     * 処理の流れ：
     * 1. コルーチンでバックグラウンド処理を開始
     * 2. AIManagerを使用して応答を取得
     * 3. 応答をチャットに表示
     * 4. UI状態を更新
     * 5. エラー発生時の処理
     */
    private fun getAIResponse(text: String) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    aiManager.getAIResponse(text)
                }
                chatAdapter.addMessage(ChatMessage(response, false))
                binding.chatRecyclerView.scrollToPosition(chatAdapter.itemCount - 1)
                updateUIState(currentState.copy(
                    isProcessing = false
                ))
            } catch (e: Exception) {
                updateUIState(currentState.copy(
                    isProcessing = false,
                    error = e.message
                ))
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            speechRecognitionManager.startListening()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 通知音を元に戻す
        (getSystemService(Context.AUDIO_SERVICE) as AudioManager).apply {
            adjustStreamVolume(AudioManager.STREAM_SYSTEM, AudioManager.ADJUST_UNMUTE, 0)
        }
        speechRecognitionManager.destroy()
        textToSpeechManager.shutdown()
    }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1
    }
}