package com.example.voiceapp

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class ChatLogger(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("chat_history", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun saveMessage(message: ChatMessage) {
        try {
            val messages = getMessages().toMutableList()
            messages.add(message)
            prefs.edit().putString("messages", gson.toJson(messages)).apply()
        } catch (e: Exception) {
            Log.e("ChatLogger", "Error saving message", e)
        }
    }

    fun getMessages(): List<ChatMessage> {
        return try {
            val json = prefs.getString("messages", "[]")
            val type = object : TypeToken<List<ChatMessage>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            Log.e("ChatLogger", "Error getting messages", e)
            emptyList()
        }
    }

    fun clearMessages() {
        prefs.edit().clear().apply()
    }
}
