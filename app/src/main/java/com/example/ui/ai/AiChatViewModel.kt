package com.example.ui.ai

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.ai.ChatModelOption
import com.example.ai.VortexAiRepository
import com.example.data.local.ChatMessageDao
import com.example.data.local.ChatMessageEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

data class AiChatUiState(
    val selectedModel: ChatModelOption = ChatModelOption.GEMINI_4_HYDROGEN,
    val systemInstruction: String = VortexAiRepository.DEFAULT_CHAT_SYSTEM_INSTRUCTION,
    val inputMessage: String = "",
    val messages: List<ChatMessageEntity> = emptyList(),
    val streamingReply: String? = null,
    val isGenerating: Boolean = false,
    val errorMessage: String? = null
)

class AiChatViewModel(
    private val aiRepository: VortexAiRepository,
    private val chatDao: ChatMessageDao
) : ViewModel() {

    private val localState = MutableStateFlow(AiChatUiState())

    val uiState: StateFlow<AiChatUiState> = combine(
        localState,
        chatDao.observeMessages()
    ) { local, savedMessages ->
        local.copy(messages = savedMessages)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AiChatUiState()
    )

    fun selectModel(option: ChatModelOption) {
        localState.update { it.copy(selectedModel = option) }
    }

    fun updateSystemInstruction(instruction: String) {
        localState.update { it.copy(systemInstruction = instruction) }
    }

    fun onInputChange(text: String) {
        localState.update { it.copy(inputMessage = text) }
    }

    fun dismissError() {
        localState.update { it.copy(errorMessage = null) }
    }

    fun clearConversation() {
        viewModelScope.launch {
            chatDao.clearAll()
            localState.update { it.copy(streamingReply = null, errorMessage = null) }
        }
    }

    fun sendMessage() {
        val prompt = localState.value.inputMessage.trim()
        if (prompt.isEmpty() || localState.value.isGenerating) return

        val chosenModel = localState.value.selectedModel
        val sysInstruction = localState.value.systemInstruction

        localState.update {
            it.copy(
                inputMessage = "",
                isGenerating = true,
                streamingReply = "",
                errorMessage = null
            )
        }

        viewModelScope.launch {
            val accumulated = StringBuilder()
            try {
                val priorHistory = chatDao.getAllMessages()
                chatDao.insert(
                    ChatMessageEntity(
                        role = "user",
                        text = prompt,
                        modelName = chosenModel.modelId
                    )
                )

                withTimeout(VortexAiRepository.AI_TIMEOUT_MS) {
                    aiRepository.streamChatMessage(
                        modelId = chosenModel.modelId,
                        systemInstruction = sysInstruction,
                        history = priorHistory,
                        userPrompt = prompt
                    ).collect { chunk ->
                        accumulated.append(chunk)
                        localState.update { it.copy(streamingReply = accumulated.toString()) }
                    }
                }

                val finalReply = accumulated.toString().trim()
                if (finalReply.isNotEmpty()) {
                    chatDao.insert(
                        ChatMessageEntity(
                            role = "model",
                            text = finalReply,
                            modelName = chosenModel.modelId
                        )
                    )
                } else {
                    localState.update { it.copy(errorMessage = "Model returned an empty response.") }
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Chat timed out", e)
                localState.update { it.copy(errorMessage = "Response timed out after 60 seconds. Please retry.") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Gemini chat failed", e)
                localState.update {
                    it.copy(errorMessage = e.localizedMessage ?: "Failed to generate chat response.")
                }
            } finally {
                localState.update { it.copy(isGenerating = false, streamingReply = null) }
            }
        }
    }

    class Factory(
        private val aiRepository: VortexAiRepository,
        private val chatDao: ChatMessageDao
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AiChatViewModel(aiRepository, chatDao) as T
        }
    }

    companion object {
        private const val TAG = "AiChatViewModel"
    }
}
