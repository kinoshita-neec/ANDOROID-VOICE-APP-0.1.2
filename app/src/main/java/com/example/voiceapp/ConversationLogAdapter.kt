package com.example.voiceapp

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.voiceapp.databinding.ItemConversationLogBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ConversationLogAdapter : RecyclerView.Adapter<ConversationLogAdapter.ViewHolder>() {
    private val messages = mutableListOf<ChatMessage>()

    class ViewHolder(private val binding: ItemConversationLogBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(message: ChatMessage) {
            binding.apply {
                senderText.text = if (message.isUser) "ユーザー" else "AI"
                messageText.text = message.message
                timestampText.text = DATE_FORMAT.format(Date(message.timestamp))
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemConversationLogBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(messages[position])
    }

    override fun getItemCount() = messages.size

    fun setMessages(newMessages: List<ChatMessage>) {
        messages.clear()
        messages.addAll(newMessages.sortedByDescending { it.timestamp })
        notifyDataSetChanged()
    }

    companion object {
        private val DATE_FORMAT = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault())
    }
}
