package com.example.voiceapp

import android.content.Context
import android.util.Log
import com.example.voiceapp.BuildConfig
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class AIManager(private val context: Context) {
    companion object {
        private const val BASE_URL = "https://api.openai.com/v1"
        private const val CHAT_ENDPOINT = "$BASE_URL/chat/completions"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .addInterceptor { chain ->
            val original = chain.request()
            val request = original.newBuilder()
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer ${apiKey.trim()}")
                .method(original.method, original.body)
                .build()
            chain.proceed(request)
        }
        .build()

    private val apiKey = BuildConfig.OPENAI_API_KEY

    init {
        // APIキーのデバッグ情報を詳細に出力
        Log.d("AIManager", "API Key length: ${apiKey.length}")
        Log.d("AIManager", "API Key first chars: ${apiKey.take(5)}")
        Log.d("AIManager", "API Key last chars: ${apiKey.takeLast(5)}")
        // APIキーに余分な文字が含まれていないか確認
        Log.d("AIManager", "API Key contains whitespace: ${apiKey.contains(" ")}")
        Log.d("AIManager", "Raw API Key bytes: ${apiKey.toByteArray().joinToString()}")
    }

    fun getAIResponse(prompt: String): String = runBlocking {
        Log.d("AIManager", "API URL: $BASE_URL")
        Log.d("AIManager", "API呼び出し開始: $prompt")
        
        return@runBlocking withContext(Dispatchers.IO) {
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = """
                {
                    "model": "gpt-4o-mini",
                    "messages": [
                        {
                            "role": "system",
                            "content": "You are a helpful assistant."
                        },
                        {
                            "role": "user",
                            "content": "${prompt.replace("\"", "\\\"").replace("\n", "\\n")}"
                        }
                    ],
                    "temperature": 0.7
                }
            """.trimIndent()

            Log.d("AIManager", "Request Body: $requestBody")
            Log.d("AIManager", "Using API Key: ${apiKey.take(5)}...")

            val request = Request.Builder()
                .url(CHAT_ENDPOINT)
                .post(requestBody.toRequestBody(mediaType))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer ${apiKey.trim()}")
                .build()

            // デバッグ情報の詳細出力
            Log.d("AIManager", "Request URL: ${request.url}")
            Log.d("AIManager", "Authorization Header: Bearer ${apiKey.take(5)}...${apiKey.takeLast(5)}")
            Log.d("AIManager", "Content-Type Header: ${request.header("Content-Type")}")
            Log.d("AIManager", "Request Method: ${request.method}")

            // リクエストヘッダーの内容をログ出力
            Log.d("AIManager", "Request headers: ${request.headers}")
            // ...existing code...
            try {
                Log.d("AIManager", "APIリクエスト送信")
                client.newCall(request).execute().use { response: Response -> 
                    // レスポンスの詳細をログ出力
                    Log.d("AIManager", "Response code: ${response.code}")
                    Log.d("AIManager", "Response message: ${response.message}")
                    Log.d("AIManager", "Response headers: ${response.headers}")
                    val responseBody = response.body?.string()
                    Log.d("AIManager", "APIレスポンス: $responseBody")
                    
                    // when式の戻り値の型を明示的に指定
                    val result: String = when (response.code) {
                        200 -> {
                            if (responseBody != null) {
                                try {
                                    val jsonObject = JSONObject(responseBody)
                                    val choices = jsonObject.getJSONArray("choices")
                                    val firstChoice = choices.getJSONObject(0)
                                    val message = firstChoice.getJSONObject("message")
                                    message.getString("content")
                                } catch (e: JSONException) {
                                    Log.e("AIManager", "JSON parsing error: ${e.message}")
                                    "応答の解析に失敗しました: ${e.message}"
                                } catch (e: Exception) {
                                    Log.e("AIManager", "Unexpected parsing error: ${e.message}")
                                    "応答の解析中に予期せぬエラーが発生しました: ${e.message}"
                                }
                            } else {
                                "応答が空でした"
                            }
                        }
                        429 -> {
                            Log.w("AIManager", "レート制限に達しました")
                            "申し訳ありません。APIの利用制限に達しました。しばらく待ってから再度お試しください。"
                        }
                        401 -> "APIキーが無効です"
                        403 -> "APIアクセスが禁止されています"
                        404 -> "APIエンドポイントが見つかりません"
                        500 -> "OpenAIサーバーでエラーが発生しました"
                        else -> "エラーが発生しました: ${response.code}"
                    }
                    result // when式の結果を返す
                }
            } catch (e: Exception) {
                Log.e("AIManager", "APIリクエストエラー", e)
                "エラーが発生しました: ${e.localizedMessage}"
            }
        }
    }
}
