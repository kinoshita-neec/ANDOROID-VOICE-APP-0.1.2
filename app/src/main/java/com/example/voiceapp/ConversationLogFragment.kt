package com.example.voiceapp

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.voiceapp.databinding.FragmentConversationLogBinding

/**
 * 会話ログを表示・管理するフラグメント
 * - 会話履歴の表示とソート機能
 * - ログの選択と削除
 * - 保持する会話ログ数の設定
 * を提供します。
 */
class ConversationLogFragment : Fragment() {
    private var _binding: FragmentConversationLogBinding? = null
    private val binding get() = _binding!!
    private lateinit var chatLogger: ChatLogger
    private lateinit var adapter: ConversationLogAdapter
    private var currentSortOrder = SortOrder.TIME_DESC

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentConversationLogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        chatLogger = ChatLogger(requireContext())
        adapter = ConversationLogAdapter()
        adapter.setMessages(chatLogger.getMessages())  // メッセージを設定
        binding.conversationLogRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.conversationLogRecyclerView.adapter = adapter

        binding.saveButton.setOnClickListener {
            val count = binding.conversationLogCount.text.toString().toIntOrNull()
            if (count != null && count > 0) {
                saveConversationLogCount(count)
                Toast.makeText(requireContext(), "会話ログの数を保存しました", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "有効な数を入力してください", Toast.LENGTH_SHORT).show()
            }
        }

        setupDeleteButton()
        setupSortHeaders()
        loadMessages()
    }

    private fun setupDeleteButton() {
        binding.deleteButton.setOnClickListener {
            val selectedMessages = adapter.getSelectedMessages()
            chatLogger.deleteMessages(selectedMessages)
            adapter.updateMessages(chatLogger.getMessages())
        }
    }

    private fun setupSortHeaders() {
        binding.apply {
            headerCheckBox.setOnCheckedChangeListener { _, isChecked ->
                (conversationLogRecyclerView.adapter as ConversationLogAdapter).selectAll(isChecked)
            }
            headerTimestamp.setOnClickListener {
                currentSortOrder = when (currentSortOrder) {
                    SortOrder.TIME_ASC -> SortOrder.TIME_DESC
                    else -> SortOrder.TIME_ASC
                }
                updateSortIndicators()
                loadMessages()
            }
            headerSender.setOnClickListener {
                currentSortOrder = when (currentSortOrder) {
                    SortOrder.SENDER_ASC -> SortOrder.SENDER_DESC
                    else -> SortOrder.SENDER_ASC
                }
                updateSortIndicators()
                loadMessages()
            }
            headerMessage.setOnClickListener {
                currentSortOrder = when (currentSortOrder) {
                    SortOrder.MESSAGE_ASC -> SortOrder.MESSAGE_DESC
                    else -> SortOrder.MESSAGE_ASC
                }
                updateSortIndicators()
                loadMessages()
            }
        }
    }

    private fun updateSortIndicators() {
        binding.apply {
            headerTimestamp.text = when (currentSortOrder) {
                SortOrder.TIME_ASC -> "日時 ▲"
                SortOrder.TIME_DESC -> "日時 ▼"
                else -> "日時"
            }
            headerSender.text = when (currentSortOrder) {
                SortOrder.SENDER_ASC -> "送信者 ▲"
                SortOrder.SENDER_DESC -> "送信者 ▼"
                else -> "送信者"
            }
            headerMessage.text = when (currentSortOrder) {
                SortOrder.MESSAGE_ASC -> "メッセージ ▲"
                SortOrder.MESSAGE_DESC -> "メッセージ ▼"
                else -> "メッセージ"
            }
        }
    }

    private fun loadMessages() {
        val messages = chatLogger.getMessages().let { messages ->
            when (currentSortOrder) {
                SortOrder.TIME_ASC -> messages.sortedBy { it.timestamp }
                SortOrder.TIME_DESC -> messages.sortedByDescending { it.timestamp }
                SortOrder.SENDER_ASC -> messages.sortedBy { it.isUser }
                SortOrder.SENDER_DESC -> messages.sortedByDescending { it.isUser }
                SortOrder.MESSAGE_ASC -> messages.sortedBy { it.message }
                SortOrder.MESSAGE_DESC -> messages.sortedByDescending { it.message }
            }
        }
        (binding.conversationLogRecyclerView.adapter as ConversationLogAdapter).setMessages(messages)
    }

    private fun saveConversationLogCount(count: Int) {
        val sharedPreferences = requireContext().getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        sharedPreferences.edit().putInt("conversation_log_count", count).apply()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    enum class SortOrder {
        TIME_ASC, TIME_DESC,
        SENDER_ASC, SENDER_DESC,
        MESSAGE_ASC, MESSAGE_DESC
    }
}
