/**
 * チャットメッセージを表現するデータクラス
 * 
 * @property text メッセージの内容
 * @property isUser true: ユーザーのメッセージ, false: AIのメッセージ
 */
package com.example.voiceapp

data class ChatMessage(
    val text: String,
    val isUser: Boolean
)
