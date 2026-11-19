package com.example.ai

import android.graphics.Bitmap
import com.example.data.local.ChatMessageEntity
import com.google.firebase.Firebase
import com.google.firebase.ai.LiveGenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.AspectRatio
import com.google.firebase.ai.type.AudioTranscriptionConfig
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.ImageConfig
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.ImageSize
import com.google.firebase.ai.type.InlineDataPart
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.SpeechConfig
import com.google.firebase.ai.type.Voice
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.ai.type.liveGenerationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeout

enum class ChatModelOption(
    val modelId: String,
    val displayName: String,
    val purpose: String
) {
    GEMINI_4_HYDROGEN("gemini-4-hydrogen", "Gemini 4 Hydrogen", "General tasks"),
    GEMINI_4_CARBON("gemini-4-carbon", "Gemini 4 Carbon", "Deep reasoning"),
    GEMINI_4_NEON("gemini-4-neon", "Gemini 4 Neon", "Complex interactive"),
    GEMINI_3_1_FLASH_LITE("gemini-3.1-flash-lite", "Gemini 3.1 Flash-Lite", "Fast responses")
}

enum class MusicModelOption(
    val modelId: String,
    val displayName: String,
    val durationHint: String
) {
    LYRIA_3_CLIP("lyria-3-clip-preview", "Lyria 3 Clip", "Short clip (up to 30s)"),
    LYRIA_3_PRO("lyria-3-pro-preview", "Lyria 3 Pro", "Full-length track")
}

data class GeneratedAudioResult(
    val bytes: ByteArray,
    val mimeType: String,
    val caption: String?
)

class VortexAiRepository {

    fun streamChatMessage(
        modelId: String,
        systemInstruction: String,
        history: List<ChatMessageEntity>,
        userPrompt: String
    ): Flow<String> {
        val model = Firebase.ai.generativeModel(
            modelName = modelId,
            systemInstruction = content {
                text(systemInstruction.ifBlank { DEFAULT_CHAT_SYSTEM_INSTRUCTION })
            }
        )
        val mappedHistory = history.map { msg ->
            content(role = msg.role) { text(msg.text) }
        }
        val chat = model.startChat(history = mappedHistory)
        return chat.sendMessageStream(userPrompt).map { chunk -> chunk.text.orEmpty() }
    }

    suspend fun generateOrEditImage(
        prompt: String,
        inputBitmap: Bitmap?,
        aspectRatio: AspectRatio
    ): Bitmap {
        val config = generationConfig {
            responseModalities = listOf(ResponseModality.IMAGE)
            imageConfig = ImageConfig.builder()
                .setAspectRatio(aspectRatio)
                .setImageSize(ImageSize.SIZE_1K)
                .build()
        }
        val model = Firebase.ai.generativeModel(
            modelName = IMAGE_MODEL_ID,
            generationConfig = config
        )

        val response = withTimeout(AI_TIMEOUT_MS) {
            if (inputBitmap != null) {
                model.generateContent(
                    content {
                        image(inputBitmap)
                        text(prompt)
                    }
                )
            } else {
                model.generateContent(prompt)
            }
        }

        val imagePart = response.candidates.firstOrNull()
            ?.content?.parts?.filterIsInstance<ImagePart>()
            ?.firstOrNull()

        return imagePart?.image
            ?: throw IllegalStateException("Model returned no image output.")
    }

    suspend fun transcribeAudio(
        audioBytes: ByteArray,
        mimeType: String,
        instruction: String
    ): String {
        val model = Firebase.ai.generativeModel(modelName = TRANSCRIBE_MODEL_ID)
        val response = withTimeout(AI_TIMEOUT_MS) {
            model.generateContent(
                content {
                    inlineData(audioBytes, mimeType)
                    text(instruction)
                }
            )
        }
        return response.text?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Transcription returned empty text.")
    }

    suspend fun generateMusic(
        modelOption: MusicModelOption,
        prompt: String
    ): GeneratedAudioResult {
        val config = generationConfig {
            responseModalities = listOf(ResponseModality.AUDIO)
        }
        val model = Firebase.ai.generativeModel(
            modelName = modelOption.modelId,
            generationConfig = config
        )
        val response = withTimeout(AI_TIMEOUT_MS) {
            model.generateContent(prompt)
        }
        val parts = response.candidates.firstOrNull()?.content?.parts.orEmpty()
        val audioPart = parts.filterIsInstance<InlineDataPart>()
            .firstOrNull { it.mimeType.startsWith("audio/") }
            ?: throw IllegalStateException("Model '${modelOption.modelId}' did not return audio data.")
        val caption = response.text?.takeIf { it.isNotBlank() }
        return GeneratedAudioResult(
            bytes = audioPart.inlineData,
            mimeType = audioPart.mimeType,
            caption = caption
        )
    }

    @OptIn(PublicPreviewAPI::class)
    fun createLiveVoiceModel(
        voiceName: String,
        systemInstruction: String
    ): LiveGenerativeModel {
        return Firebase.ai(backend = GenerativeBackend.googleAI()).liveModel(
            modelName = LIVE_VOICE_MODEL_ID,
            generationConfig = liveGenerationConfig {
                responseModality = ResponseModality.AUDIO
                speechConfig = SpeechConfig(voice = Voice(voiceName))
                inputAudioTranscription = AudioTranscriptionConfig()
                outputAudioTranscription = AudioTranscriptionConfig()
            },
            systemInstruction = content {
                text(systemInstruction.ifBlank { DEFAULT_VOICE_SYSTEM_INSTRUCTION })
            }
        )
    }

    companion object {
        const val AI_TIMEOUT_MS = 60_000L
        const val IMAGE_MODEL_ID = "gemini-nano-banana-2.1"
        const val TRANSCRIBE_MODEL_ID = "gemini-3.5-transcribe"
        const val LIVE_VOICE_MODEL_ID = "gemini-3.8-live"

        const val DEFAULT_CHAT_SYSTEM_INSTRUCTION =
            "You are Vortex AI, an intelligent media, productivity, and technical assistant inside Vortex Video Downloader. Be clear, accurate, and helpful."

        const val DEFAULT_VOICE_SYSTEM_INSTRUCTION =
            "You are Vortex Live, a friendly, concise real-time voice assistant."
    }
}
