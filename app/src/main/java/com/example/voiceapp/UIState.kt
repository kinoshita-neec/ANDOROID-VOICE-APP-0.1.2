package com.example.voiceapp

data class UIState(
    val isListening: Boolean = false,
    val isCancelEnabled: Boolean = false,
    val isProcessing: Boolean = false,
    val error: String? = null
)
