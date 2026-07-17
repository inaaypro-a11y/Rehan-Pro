package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.database.ChatMessageEntity
import com.example.data.database.ChatSessionEntity
import com.example.data.repository.ChatRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ChatRepository
    private val sharedPrefs = application.getSharedPreferences("rehanproai_prefs", Context.MODE_PRIVATE)

    init {
        val database = AppDatabase.getDatabase(application)
        repository = ChatRepository(database.chatDao())
    }

    // --- User Preferences ---
    private val _userName = MutableStateFlow(sharedPrefs.getString("user_name", "Guest") ?: "Guest")
    val userName: StateFlow<String> = _userName.asStateFlow()

    private val _isDarkTheme = MutableStateFlow(sharedPrefs.getBoolean("dark_theme", true))
    val isDarkTheme: StateFlow<Boolean> = _isDarkTheme.asStateFlow()

    private val _aiProvider = MutableStateFlow(sharedPrefs.getString("ai_provider", "gemini") ?: "gemini")
    val aiProvider: StateFlow<String> = _aiProvider.asStateFlow()

    private val _customApiKey = MutableStateFlow(sharedPrefs.getString("custom_api_key", "") ?: "")
    val customApiKey: StateFlow<String> = _customApiKey.asStateFlow()

    // --- Sessions ---
    val sessions: StateFlow<List<ChatSessionEntity>> = repository.allSessions
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _activeSessionId = MutableStateFlow<Int?>(null)
    val activeSessionId: StateFlow<Int?> = _activeSessionId.asStateFlow()

    // --- Messages for Active Session ---
    @OptIn(ExperimentalCoroutinesApi::class)
    val activeSessionMessages: StateFlow<List<ChatMessageEntity>> = _activeSessionId
        .flatMapLatest { sessionId ->
            if (sessionId != null) {
                repository.getMessagesForSession(sessionId)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // --- UI States ---
    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    // Pending attachment (base64 string and mimeType)
    private val _pendingAttachment = MutableStateFlow<String?>(null)
    val pendingAttachment: StateFlow<String?> = _pendingAttachment.asStateFlow()

    private val _pendingAttachmentType = MutableStateFlow<String?>(null)
    val pendingAttachmentType: StateFlow<String?> = _pendingAttachmentType.asStateFlow()

    init {
        // Auto-create initial session if none exists or select the first session
        viewModelScope.launch {
            repository.allSessions.collect { list ->
                if (_activeSessionId.value == null && list.isNotEmpty()) {
                    _activeSessionId.value = list.first().id
                }
            }
        }
    }

    // --- User Action Handlers ---

    fun updateProfileName(name: String) {
        sharedPrefs.edit().putString("user_name", name).apply()
        _userName.value = name
    }

    fun toggleTheme() {
        val current = _isDarkTheme.value
        sharedPrefs.edit().putBoolean("dark_theme", !current).apply()
        _isDarkTheme.value = !current
    }

    fun updateAiProvider(provider: String) {
        sharedPrefs.edit().putString("ai_provider", provider).apply()
        _aiProvider.value = provider
    }

    fun updateCustomApiKey(key: String) {
        sharedPrefs.edit().putString("custom_api_key", key).apply()
        _customApiKey.value = key
    }

    fun selectSession(sessionId: Int) {
        _activeSessionId.value = sessionId
    }

    fun createNewSession(title: String = "New Chat") {
        viewModelScope.launch {
            val newId = repository.createNewSession(title)
            _activeSessionId.value = newId
        }
    }

    fun deleteSession(sessionId: Int) {
        viewModelScope.launch {
            repository.deleteSession(sessionId)
            if (_activeSessionId.value == sessionId) {
                // Try selecting another session
                val list = sessions.value
                val remaining = list.filter { it.id != sessionId }
                if (remaining.isNotEmpty()) {
                    _activeSessionId.value = remaining.first().id
                } else {
                    _activeSessionId.value = null
                }
            }
        }
    }

    fun setAttachment(base64Data: String?, mimeType: String?) {
        _pendingAttachment.value = base64Data
        _pendingAttachmentType.value = mimeType
    }

    fun clearAttachment() {
        _pendingAttachment.value = null
        _pendingAttachmentType.value = null
    }

    fun sendMessage(text: String) {
        val currentSessionId = _activeSessionId.value
        if (text.trim().isEmpty() && _pendingAttachment.value == null) return

        _isGenerating.value = true
        val attachment = _pendingAttachment.value
        val attachmentType = _pendingAttachmentType.value
        clearAttachment() // Clear once sent

        viewModelScope.launch {
            try {
                // Ensure there's an active session
                val sessionId = if (currentSessionId == null) {
                    val newId = repository.createNewSession("New Chat")
                    _activeSessionId.value = newId
                    newId
                } else {
                    currentSessionId
                }

                repository.sendMessage(
                    sessionId = sessionId,
                    text = text,
                    attachmentPath = attachment,
                    attachmentType = attachmentType,
                    aiProvider = _aiProvider.value,
                    customApiKey = _customApiKey.value.ifBlank { null }
                )
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Failed to send message", e)
            } finally {
                _isGenerating.value = false
            }
        }
    }

    fun clearAllChats() {
        viewModelScope.launch {
            repository.clearAllData()
            _activeSessionId.value = null
            createNewSession("New Chat")
        }
    }
}
