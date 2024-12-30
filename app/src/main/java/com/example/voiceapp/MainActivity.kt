package com.example.voiceapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.View  // この行を追加
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import java.util.*

class MainActivity : AppCompatActivity(), SpeechRecognitionManager.SpeechRecognitionCallback {
    private lateinit var speechRecognitionManager: SpeechRecognitionManager
    private lateinit var textToSpeech: TextToSpeech
    private lateinit var startListeningButton: Button
    private lateinit var recognizedTextView: TextView
    private lateinit var aiResponseTextView: TextView
    private lateinit var statusText: TextView
    private lateinit var cancelButton: Button
    private lateinit var askAiButton: Button
    private var isListening = false // 音声認識中かどうかを管理するフラグ
    private var isProcessing = false
    private var currentRecognizedText: String = ""

    private var mainScope = CoroutineScope(Dispatchers.Main)
    private lateinit var aiManager: AIManager
    private var lastToastTime = 0L // Toastメッセージの表示時間を管理する変数
    private var recognizedTextHistory = StringBuilder()

    private lateinit var sensorManager: SensorManager
    private lateinit var checkSensorsButton: Button
    private lateinit var sensorInfoText: TextView

    private enum class AppState {
        IDLE,              // 初期状態
        RECORDING,         // 音声入力中
        CONVERTING,        // 音声をテキストに変換中
        READY_TO_ASK,     // AI問い合わせ可能
        ASKING_AI,        // AIに問い合わせ中
        AI_THINKING,      // AI応答生成中
        AI_RESPONDED,     // AI応答受信完了
        SPEAKING,         // 応答読み上げ中
        COMPLETED,        // 処理完了
        ERROR             // エラー発生
    }

    private var currentState = AppState.IDLE

    private fun updateState(newState: AppState) {
        currentState = newState
        val statusText = when (newState) {
            AppState.IDLE -> R.string.status_waiting_input
            AppState.RECORDING -> R.string.status_recording
            AppState.CONVERTING -> R.string.status_converting
            AppState.READY_TO_ASK -> R.string.status_ready_to_ask
            AppState.ASKING_AI -> R.string.status_asking_ai
            AppState.AI_THINKING -> R.string.status_ai_thinking
            AppState.AI_RESPONDED -> R.string.status_ai_responded
            AppState.SPEAKING -> R.string.status_speaking_response
            AppState.COMPLETED -> R.string.status_completed
            AppState.ERROR -> R.string.status_error_occurred
        }
        updateUIForState(newState)
        this.statusText.text = getString(statusText)
        Log.d("MainActivity", "状態遷移: $newState")
    }

    private fun updateUIForState(state: AppState) {
        runOnUiThread {
            when (state) {
                AppState.IDLE -> {
                    startListeningButton.isEnabled = true
                    askAiButton.isEnabled = false
                    cancelButton.isEnabled = false
                }
                AppState.RECORDING -> {
                    startListeningButton.isEnabled = true
                    startListeningButton.text = getString(R.string.stop_listening)
                    cancelButton.isEnabled = true
                    // AI問い合わせボタンの状態を維持
                }
                AppState.CONVERTING -> {
                    startListeningButton.isEnabled = false
                    askAiButton.isEnabled = false
                    cancelButton.isEnabled = true
                }
                AppState.READY_TO_ASK -> {
                    startListeningButton.isEnabled = true
                    startListeningButton.text = getString(R.string.start_listening)
                    askAiButton.isEnabled = true
                    cancelButton.isEnabled = false
                }
                AppState.ASKING_AI, AppState.AI_THINKING -> {
                    startListeningButton.isEnabled = false
                    askAiButton.isEnabled = false
                    cancelButton.isEnabled = true
                }
                AppState.AI_RESPONDED, AppState.SPEAKING -> {
                    startListeningButton.isEnabled = false
                    askAiButton.isEnabled = false
                    cancelButton.isEnabled = true
                }
                AppState.COMPLETED -> {
                    startListeningButton.isEnabled = true
                    startListeningButton.text = getString(R.string.start_listening)
                    askAiButton.isEnabled = currentRecognizedText.isNotEmpty()
                    cancelButton.isEnabled = false
                }
                AppState.ERROR -> {
                    startListeningButton.isEnabled = true
                    startListeningButton.text = getString(R.string.start_listening)
                    askAiButton.isEnabled = currentRecognizedText.isNotEmpty()
                    cancelButton.isEnabled = false
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize components
        speechRecognitionManager = SpeechRecognitionManager(this, this)
        aiManager = AIManager(this)
        startListeningButton = findViewById(R.id.startListeningButton)
        recognizedTextView = findViewById(R.id.recognizedText)
        aiResponseTextView = findViewById(R.id.aiResponseText)
        statusText = findViewById(R.id.statusText)
        cancelButton = findViewById(R.id.cancelButton)
        askAiButton = findViewById(R.id.askAiButton)

        cancelButton.setOnClickListener {
            cancelOperation()
        }

        askAiButton.setOnClickListener {
            if (currentRecognizedText.isNotEmpty()) {
                askAI(currentRecognizedText)
            }
        }

        // Check for microphone permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        }

        startListeningButton.setOnClickListener {
            if (speechRecognitionManager.isListening()) {
                stopListening()
            } else {
                startListening()
            }
        }

        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = textToSpeech.setLanguage(Locale.JAPAN)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e("MainActivity", "Language not supported")
                }
            } else {
                Log.e("MainActivity", "Initialization failed")
            }
        }

        sensorManager = SensorManager(this)
        checkSensorsButton = findViewById(R.id.checkSensorsButton)
        sensorInfoText = findViewById(R.id.sensorInfoText)

        checkSensorsButton.setOnClickListener {
            updateSensorInfo()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, do nothing here, startListening is called by button click
            } else {
                Toast.makeText(this, "マイクの許可が必要です", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startListening() {
        startListeningButton.isEnabled = false
        startListeningButton.text = getString(R.string.stop_listening)
        speechRecognitionManager.startListening()
        updateUIState()
    }

    private fun stopListening() {
        try {
            isListening = false  // 先にフラグを更新
            speechRecognitionManager.stopListening()
            
            Handler(Looper.getMainLooper()).postDelayed({
                runOnUiThread {
                    startListeningButton.text = getString(R.string.start_listening)
                    startListeningButton.isEnabled = true
                    
                    if (currentRecognizedText.isNotEmpty()) {
                        updateState(AppState.READY_TO_ASK)
                        askAiButton.isEnabled = true
                    } else {
                        updateState(AppState.IDLE)
                    }
                }
            }, 500)
        } catch (e: Exception) {
            Log.e("MainActivity", "Error in stopListening: ${e.message}")
            updateState(AppState.ERROR)
        }
    }

    // SpeechRecognitionCallback implementations
    override fun onRecognitionStarted() {
        updateState(AppState.RECORDING)
        showToast(getString(R.string.please_speak))
    }

    override fun onRecognitionResult(text: String) {
        runOnUiThread {
            // 認識テキストの更新（追加ではなく上書き）
            currentRecognizedText = text
            recognizedTextView.text = text

            // UI状態更新
            if (!isProcessing) {
                updateState(AppState.READY_TO_ASK)
                askAiButton.isEnabled = true
            }
        }
    }

    override fun onRecognitionError(errorMessage: String) {
        updateState(AppState.ERROR)
        showToast(errorMessage)
    }

    override fun onPartialResult(text: String) {
        // 部分認識結果の表示
        runOnUiThread {
            if (text.isNotEmpty()) {
                recognizedTextView.text = text
            }
        }
    }

    private fun processRecognizedText(text: String) {
        try {
            Log.d("MainActivity", "AI処理開始")
            isProcessing = true
            isListening = false
            stopListening()
            updateUIState()

            mainScope.launch {
                try {
                    // AI応答を取得
                    val response = withContext(Dispatchers.IO) {
                        aiManager.getAIResponse(text)
                    }
                    
                    Log.d("MainActivity", "AI応答受信: $response")
                    
                    // UI更新
                    withContext(Dispatchers.Main) {
                        aiResponseTextView.text = response
                        statusText.text = getString(R.string.status_speaking)
                        
                        // 音声で応答を読み上げ
                        textToSpeech.speak(response, TextToSpeech.QUEUE_FLUSH, null, null)
                        delay(2000) // 読み上げ時間を確保
                    }
                    
                } catch (e: Exception) {
                    Log.e("MainActivity", "AI処理エラー", e)
                    withContext(Dispatchers.Main) {
                        statusText.text = getString(R.string.status_error)
                        showToast("AI処理中にエラーが発生しました: ${e.message}")
                    }
                } finally {
                    withContext(Dispatchers.Main) {
                        isProcessing = false
                        delay(1000)
                        updateUIState()
                        startListening()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "処理開始エラー", e)
            isProcessing = false
            updateUIState()
        }
    }

    private fun askAI(text: String) {
        updateState(AppState.ASKING_AI)
        mainScope.launch {
            try {
                updateState(AppState.AI_THINKING)
                val response = withContext(Dispatchers.IO) {
                    aiManager.getAIResponse(text)
                }
                updateState(AppState.AI_RESPONDED)
                aiResponseTextView.text = response
                
                updateState(AppState.SPEAKING)
                textToSpeech.speak(response, TextToSpeech.QUEUE_FLUSH, null, null)
                delay(2000)
                
                updateState(AppState.COMPLETED)
            } catch (e: Exception) {
                Log.e("MainActivity", "AI処理エラー", e)
                updateState(AppState.ERROR)
                showToast("AI処理中にエラーが発生しました: ${e.message}")
            }
        }
    }

    private fun showToast(message: String) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastToastTime > 2000) { // 2秒間隔で表示
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            lastToastTime = currentTime
        }
    }

    // エラー回復のための再初期化メソッド
    private fun reinitializeSpeechRecognizer() {
        try {
            speechRecognitionManager.destroy()
            Handler(Looper.getMainLooper()).postDelayed({
                speechRecognitionManager = SpeechRecognitionManager(this, this)
            }, 200) // 短い遅延を入れて再初期化
        } catch (e: Exception) {
            Log.e("MainActivity", "Error reinitializing SpeechRecognizer: ${e.message}")
        }
    }

    // アプリがバックグラウンドから復帰した時の処理
    override fun onResume() {
        super.onResume()
        try {
            reinitializeSpeechRecognizer()
            isListening = false
            isProcessing = false
            updateUIState()
        } catch (e: Exception) {
            Log.e("MainActivity", "Error in onResume: ${e.message}")
        }
        sensorManager.registerSensors()
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterSensors()
    }

    override fun onStop() {
        super.onStop()
        textToSpeech.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognitionManager.destroy()
        textToSpeech.shutdown()
        mainScope.cancel() // aiManager.cancel()の代わりにmainScope.cancel()を使用
    }

    private fun cancelOperation() {
        try {
            if (isProcessing) {
                mainScope.cancel()
                mainScope = CoroutineScope(Dispatchers.Main)
                isProcessing = false
            }
            // 音声認識の停止と再初期化
            speechRecognitionManager.stopListening()
            Handler(Looper.getMainLooper()).postDelayed({
                speechRecognitionManager.reinitialize()
                isListening = false
                updateState(AppState.IDLE)
            }, 1000)
        } catch (e: Exception) {
            Log.e("MainActivity", "Error in cancelOperation: ${e.message}")
            updateState(AppState.ERROR)
        }
    }

    private fun updateUIState() {
        runOnUiThread {
            when {
                isProcessing -> {
                    statusText.text = getString(R.string.status_processing)
                    startListeningButton.isEnabled = false
                    askAiButton.isEnabled = false
                    cancelButton.isEnabled = true
                }
                isListening -> {
                    statusText.text = getString(R.string.status_listening)
                    startListeningButton.isEnabled = true
                    startListeningButton.text = getString(R.string.stop_listening)
                    // AI問い合わせボタンの状態を保持
                    cancelButton.isEnabled = true
                }
                else -> {
                    startListeningButton.isEnabled = true
                    startListeningButton.text = getString(R.string.start_listening)
                    askAiButton.isEnabled = currentRecognizedText.isNotEmpty()
                    cancelButton.isEnabled = false
                    statusText.text = if (currentRecognizedText.isNotEmpty()) {
                        getString(R.string.status_ready_to_ask)
                    } else {
                        getString(R.string.status_idle)
                    }
                }
            }
        }
    }

    // 画面回転などでActivityが再作成される際のデータ保存
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("recognizedTextHistory", recognizedTextHistory.toString())
    }

    // データの復元
    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        recognizedTextHistory = StringBuilder(savedInstanceState.getString("recognizedTextHistory", ""))
        recognizedTextView.text = recognizedTextHistory.toString()
    }

    // クリア機能の追加（必要に応じて）
    private fun clearRecognizedText() {
        recognizedTextHistory.clear()
        recognizedTextView.text = ""
    }

    private fun updateSensorInfo() {
        sensorInfoText.visibility = View.VISIBLE
        val sensorInfo = sensorManager.getSensorInfo()
        sensorInfoText.text = sensorInfo
        
        // センサー情報を読み上げ
        val speakableInfo = sensorManager.getSpeakableInfo()
        textToSpeech.speak(speakableInfo, TextToSpeech.QUEUE_FLUSH, null, null)
    }
}