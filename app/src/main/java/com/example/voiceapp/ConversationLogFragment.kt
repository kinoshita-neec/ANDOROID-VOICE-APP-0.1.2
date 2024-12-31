package com.example.voiceapp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.voiceapp.databinding.FragmentConversationLogBinding

class ConversationLogFragment : Fragment() {
    private var _binding: FragmentConversationLogBinding? = null
    private val binding get() = _binding!!
    private lateinit var chatLogger: ChatLogger

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
        setupRecyclerView()
        loadMessages()
    }

    private fun setupRecyclerView() {
        binding.conversationLogRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = ConversationLogAdapter()
        }
    }

    private fun loadMessages() {
        val messages = chatLogger.getMessages()
        (binding.conversationLogRecyclerView.adapter as ConversationLogAdapter).setMessages(messages)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
