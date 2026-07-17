package com.example.data.repository

import com.example.data.api.GeminiApiClient
import com.example.data.api.OpenAiApiClient
import com.example.data.database.ChatDao
import com.example.data.database.ChatMessageEntity
import com.example.data.database.ChatSessionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class ChatRepository(private val chatDao: ChatDao) {

    val allSessions: Flow<List<ChatSessionEntity>> = chatDao.getAllSessions()

    fun getMessagesForSession(sessionId: Int): Flow<List<ChatMessageEntity>> {
        return chatDao.getMessagesForSession(sessionId)
    }

    suspend fun createNewSession(title: String): Int = withContext(Dispatchers.IO) {
        val session = ChatSessionEntity(title = title)
        chatDao.insertSession(session).toInt()
    }

    suspend fun updateSessionTitle(sessionId: Int, newTitle: String) = withContext(Dispatchers.IO) {
        val existing = chatDao.getSessionById(sessionId)
        if (existing != null) {
            chatDao.updateSession(existing.copy(title = newTitle, lastUpdated = System.currentTimeMillis()))
        }
    }

    suspend fun deleteSession(sessionId: Int) = withContext(Dispatchers.IO) {
        chatDao.deleteMessagesForSession(sessionId)
        chatDao.deleteSessionById(sessionId)
    }

    suspend fun clearAllData() = withContext(Dispatchers.IO) {
        chatDao.deleteAllMessages()
        chatDao.deleteAllSessions()
    }

    /**
     * Core chat logic. Saves user message, calls Gemini/OpenAI API (supporting conversation history),
     * and saves model response.
     */
    suspend fun sendMessage(
        sessionId: Int,
        text: String,
        attachmentPath: String? = null,
        attachmentType: String? = null,
        systemInstruction: String = "You are RehanProAI, an elite ChatGPT-like personal AI assistant. " +
                "Always be smart, polite, concise, and professional. Support English and Telugu languages natively.",
        aiProvider: String = "openai",
        customApiKey: String? = null
    ): ChatMessageEntity = withContext(Dispatchers.IO) {
        // 1. Save User Message
        val userMsg = ChatMessageEntity(
            sessionId = sessionId,
            role = "USER",
            text = text,
            attachmentPath = attachmentPath,
            attachmentType = attachmentType
        )
        chatDao.insertMessage(userMsg)

        // Update Session Timestamp
        val session = chatDao.getSessionById(sessionId)
        if (session != null) {
            // Update title if it was the default first message title
            val newTitle = if (session.title == "New Chat" && text.isNotEmpty()) {
                if (text.length > 25) text.take(22) + "..." else text
            } else {
                session.title
            }
            chatDao.updateSession(session.copy(title = newTitle, lastUpdated = System.currentTimeMillis()))
        }

        // Get full chat history to pass to API for full conversational memory
        val history = chatDao.getMessagesForSessionSync(sessionId)

        // 2. Determine if user requested image generation
        val isImageRequest = text.lowercase().let {
            it.contains("generate image") || it.contains("draw") || 
            it.contains("create image") || it.contains("paint") || 
            it.contains("generate a photo") || it.contains("show me a photo")
        }

        var responseAttachmentPath: String? = null
        var responseAttachmentType: String? = null
        var aiMsg: ChatMessageEntity

        if (isImageRequest) {
            val aiResponseText: String
            // Attempt actual image generation
            val base64Data = if (aiProvider == "openai") {
                OpenAiApiClient.generateImage(text, customApiKey)
            } else {
                GeminiApiClient.generateImage(text)
            }

            if (base64Data != null) {
                aiResponseText = "🎨 I have generated this beautiful AI image for your prompt: \"$text\""
                responseAttachmentPath = "data:image/png;base64,$base64Data"
                responseAttachmentType = "image/png"
            } else {
                // If offline / empty key, provide a gorgeous visual mock representation
                aiResponseText = "🎨 **Cosmic AI Image Generator**\n\n" +
                        "I have initialized my creative engines for your request: *\"$text\"*.\n\n" +
                        "Configure your `${if (aiProvider == "openai") "OPENAI_API_KEY" else "GEMINI_API_KEY"}` in the AI Studio Secrets panel or Settings to enable real-time image renders! " +
                        "I have generated a high-fidelity visual preview of this graphic asset below."
                
                // We save a special code "MOCK_IMAGE_ASSET" to trigger a spectacular canvas drawing in UI!
                responseAttachmentPath = "MOCK_IMAGE_ASSET"
                responseAttachmentType = "image/png"
            }
            aiMsg = ChatMessageEntity(
                sessionId = sessionId,
                role = "MODEL",
                text = aiResponseText,
                attachmentPath = responseAttachmentPath,
                attachmentType = responseAttachmentType
            )
            val aiMsgId = chatDao.insertMessage(aiMsg).toInt()
            aiMsg = aiMsg.copy(id = aiMsgId)
        } else {
            // Call Standard Conversational AI with streaming support
            aiMsg = ChatMessageEntity(
                sessionId = sessionId,
                role = "MODEL",
                text = ""
            )
            val aiMsgId = chatDao.insertMessage(aiMsg).toInt()
            aiMsg = aiMsg.copy(id = aiMsgId)

            var accumulatedText = ""
            if (aiProvider == "openai") {
                OpenAiApiClient.generateChatResponseStream(history, systemInstruction, customApiKey) { chunk ->
                    accumulatedText += chunk
                    aiMsg = aiMsg.copy(text = accumulatedText)
                    chatDao.insertMessage(aiMsg)
                }
            } else {
                GeminiApiClient.generateChatResponseStream(history, systemInstruction) { chunk ->
                    accumulatedText += chunk
                    aiMsg = aiMsg.copy(text = accumulatedText)
                    chatDao.insertMessage(aiMsg)
                }
            }
        }

        // Update lastUpdated one more time
        val updatedSession = chatDao.getSessionById(sessionId)
        if (updatedSession != null) {
            chatDao.updateSession(updatedSession.copy(lastUpdated = System.currentTimeMillis()))
        }

        aiMsg
    }
}
