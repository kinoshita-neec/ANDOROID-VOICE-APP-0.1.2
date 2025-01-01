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
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.voiceapp.databinding.ActivityMainBinding
import com.google.android.material.navigation.NavigationView
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
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private var currentState = UIState()
    private var isReturningFromSettings = false
    private var isSpeechRecognitionInitialized = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingRecognitionStart = false
    private lateinit var chatLogger: ChatLogger

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

        // ドロワーレイアウトとナビゲーションビューの設定
        drawerLayout = binding.drawerLayout
        navigationView = binding.navigationView
        setupNavigation()

        // TextToSpeechManagerの初期化のみ行い、初期化完了時にwelcomeを開始
        textToSpeechManager = TextToSpeechManager(this)
        textToSpeechManager.initialize {
            startWelcomeSequence()
        }

        // チャットアダプターの初期化
        chatAdapter = ChatAdapter()
        binding.chatRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = chatAdapter
        }

        aiManager = AIManager(this)

        binding.settingsButton.setOnClickListener {
            isReturningFromSettings = true
            // 音声認識を停止
            speechRecognitionManager.stopListening()
            // 設定画面に遷移
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // SpeechRecognitionManagerは初期化のみ行う
        initializeSpeechRecognition()

        chatLogger = ChatLogger(this)
        updatePromptPreview()
    }

    override fun onResume() {
        super.onResume()
        if (isReturningFromSettings) {
            isReturningFromSettings = false
            startWelcomeSequence()
        }
    }

    private fun startWelcomeSequence() {
        val welcomeMessage = getSharedPreferences("agent_settings", Context.MODE_PRIVATE)
            ?.getString("agent_name", "あすか")?.let { name ->
                "${name}が戻ってきました。ご用件をどうぞ。"
            } ?: getString(R.string.welcome_message)

        Log.d("MainActivity", "Starting welcome sequence with message: $welcomeMessage")
        pendingRecognitionStart = false
        
        textToSpeechManager.speak(welcomeMessage) {
            manageSpeechRecognitionFlow()
        }
    }

    private fun ensureSpeechRecognitionStart() {
        if (pendingRecognitionStart) {
            Log.d("MainActivity", "Recognition start already pending")
            return
        }
        pendingRecognitionStart = true
        manageSpeechRecognitionFlow()
    }

    /**
     * 音声認識の開始フローを管理する共通ヘルパー関数
     * 
     * この関数は以下の責務を持ちます：
     * 1. 音声読み上げの完了確認
     * 2. 適切なタイミングでの音声認識開始
     * 3. 状態管理とエラーハンドリング
     */
    private fun manageSpeechRecognitionFlow() {
        fun checkAndStart() {
            if (!textToSpeechManager.isSpeaking()) {
                Log.d("MainActivity", "Speech completed, preparing to start recognition")
                mainHandler.postDelayed({
                    if (!isReturningFromSettings && !textToSpeechManager.isSpeaking()) {
                        pendingRecognitionStart = false
                        checkPermissionAndStartListening()
                    }
                }, 1000) // 安全のため1秒待機
            } else {
                Log.d("MainActivity", "Still speaking, waiting for completion")
                mainHandler.postDelayed({ checkAndStart() }, 100)
            }
        }

        checkAndStart()
    }

    private fun waitForSpeechCompletion(onComplete: () -> Unit) {
        fun checkSpeechStatus() {
            if (!textToSpeechManager.isSpeaking()) {
                Log.d("MainActivity", "Speech completion confirmed")
                Handler(Looper.getMainLooper()).postDelayed({
                    onComplete()
                }, 500)
            } else {
                Log.d("MainActivity", "Still speaking, waiting...")
                Handler(Looper.getMainLooper()).postDelayed({ checkSpeechStatus() }, 100)
            }
        }
        checkSpeechStatus()
    }

    private fun initializeSpeechRecognition() {
        Log.d("MainActivity", "Initializing speech recognition")
        speechRecognitionManager = SpeechRecognitionManager(this, this)
        isSpeechRecognitionInitialized = true
    }

    /**
     * 音声認識の権限チェックと開始
     * 
     * 1. RECORD_AUDIO権限の確認
     * 2. 権限がない場合は要求
     * 3. 権限がある場合は音声認識を開始
     */
    private fun checkPermissionAndStartListening() {
        Log.d("MainActivity", "Checking permission before starting recognition")
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), PERMISSION_REQUEST_CODE)
            return
        }
        
        // 読み上げが完全に終了していることを確認
        if (!textToSpeechManager.isSpeaking()) {
            Log.d("MainActivity", "Actually starting speech recognition")
            updateUIState(currentState.copy(isListening = true))
            speechRecognitionManager.startListening()
        } else {
            Log.d("MainActivity", "Still speaking, delaying recognition")
            Handler(Looper.getMainLooper()).postDelayed({
                checkPermissionAndStartListening()
            }, 500)
        }
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
        updatePromptPreview()  // プロンプトプレビューを更新
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
                chatLogger.saveMessage(ChatMessage(text, true))  // ユーザーのメッセージを保存
                
                val response = withContext(Dispatchers.IO) {
                    aiManager.getAIResponse(text)
                }
                
                chatLogger.saveMessage(ChatMessage(response, false))  // AIの応答を保存
                chatAdapter.addMessage(ChatMessage(response, false))
                binding.chatRecyclerView.scrollToPosition(chatAdapter.itemCount - 1)
                
                updateUIState(currentState.copy(isProcessing = false))
                pendingRecognitionStart = false

                textToSpeechManager.speak(response) {
                    ensureSpeechRecognitionStart()
                }
                updatePromptPreview()  // プロンプトプレビューを更新
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
        private const val SPEECH_START_DELAY = 1000L  // ウェルカムメッセージ読み上げ完了後の待機時間を1秒に変更
    }

    private fun setupNavigation() {
        navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_conversation_log -> {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, ConversationLogFragment())
                        .addToBackStack(null)
                        .commit()
                    true
                }
                else -> false
            }
        }
    }

    private fun updatePromptPreview() {
        val promptPreview = PromptPreviewFragment.getSystemPrompt(this)
        binding.promptPreviewContent.text = promptPreview
    }
}
