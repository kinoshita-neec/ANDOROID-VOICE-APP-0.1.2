package com.example.voiceapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.constraintlayout.widget.ConstraintLayout

class ChatAdapter : RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {
    private val messages = mutableListOf<ChatMessage>()

    class ChatViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val messageText: TextView = view.findViewById(R.id.messageText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat_message, parent, false)
        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val message = messages[position]
        holder.messageText.apply {
            text = message.text
            
            // メッセージの位置とスタイルを設定
            val params = layoutParams as ConstraintLayout.LayoutParams
            if (message.isUser) {
                setBackgroundResource(R.drawable.chat_bubble_user)
                params.horizontalBias = 1f  // 右寄せ
            } else {
                setBackgroundResource(R.drawable.chat_bubble_ai)
                params.horizontalBias = 0f  // 左寄せ
            }
            layoutParams = params
            
            // アニメーションの追加（オプション）
            alpha = 0f
            animate().alpha(1f).setDuration(200).start()
        }
    }

    override fun getItemCount() = messages.size

    fun addMessage(message: ChatMessage) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }

    fun clear() {
        messages.clear()
        notifyDataSetChanged()
    }
}

data class ChatMessage(
    val text: String,
    val isFromUser: Boolean
)
