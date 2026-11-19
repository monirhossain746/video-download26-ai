package com.example.ui.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.ai.MusicModelOption
import com.example.ai.VortexAiRepository
import com.example.data.local.DownloadCategory
import com.example.data.local.DownloadDao
import com.example.data.local.DownloadEntity
import com.example.data.local.DownloadStatus
import com.example.security.SafeFileManager
import com.google.firebase.ai.type.AspectRatio
import com.google.firebase.ai.type.LiveSession
import com.google.firebase.ai.type.PublicPreviewAPI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

sealed interface AsyncActionState<out T> {
    data object Idle : AsyncActionState<Nothing>
    data object Loading : AsyncActionState<Nothing>
    data class Success<T>(val data: T) : AsyncActionState<T>
    data class Error(val message: String) : AsyncActionState<Nothing>
}

data class VoiceTranscriptLine(
    val id: Long = System.nanoTime(),
    val speaker: String,
    val text: String
)

data class AiStudioUiState(
    // Image Studio (gemini-nano-banana-2.1)
    val imagePrompt: String = "",
    val selectedAspectRatio: AspectRatio = AspectRatio.SQUARE_1x1,
    val sourceEditBitmap: Bitmap? = null,
    val imageState: AsyncActionState<Bitmap> = AsyncActionState.Idle,
    val savedImageMessage: String? = null,

    // Audio Transcriber (gemini-3.5-transcribe)
    val isRecordingAudio: Boolean = false,
    val recordingSeconds: Int = 0,
    val recordedAudioFile: File? = null,
    val transcribePromptHint: String = "Transcribe this audio accurately with punctuation and speaker cues.",
    val transcribeState: AsyncActionState<String> = AsyncActionState.Idle,

    // Live Voice (gemini-3.8-live)
    val liveVoiceName: String = "FENRIR",
    val liveSystemPrompt: String = VortexAiRepository.DEFAULT_VOICE_SYSTEM_INSTRUCTION,
    val isLiveConnecting: Boolean = false,
    val isLiveConnected: Boolean = false,
    val liveTranscripts: List<VoiceTranscriptLine> = emptyList(),
    val liveError: String? = null,

    // Music Studio (lyria-3-clip-preview / lyria-3-pro-preview)
    val musicPrompt: String = "",
    val selectedMusicModel: MusicModelOption = MusicModelOption.LYRIA_3_CLIP,
    val musicState: AsyncActionState<DownloadEntity> = AsyncActionState.Idle
)

@OptIn(PublicPreviewAPI::class)
class AiStudioViewModel(
    private val aiRepository: VortexAiRepository,
    private val safeFileManager: SafeFileManager,
    private val downloadDao: DownloadDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(AiStudioUiState())
    val uiState: StateFlow<AiStudioUiState> = _uiState.asStateFlow()

    private var mediaRecorder: MediaRecorder? = null
    private var liveSession: LiveSession? = null

    // --- Image Generation & Editing ---
    fun onImagePromptChange(value: String) {
        _uiState.update { it.copy(imagePrompt = value, savedImageMessage = null) }
    }

    fun selectImageAspectRatio(ratio: AspectRatio) {
        _uiState.update { it.copy(selectedAspectRatio = ratio) }
    }

    fun loadSourceBitmapFromUri(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val bitmap = runCatching {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            }.getOrNull()
            if (bitmap != null) {
                val scaled = scaleBitmapIfNeeded(bitmap, 1536)
                _uiState.update { it.copy(sourceEditBitmap = scaled) }
            }
        }
    }

    fun clearSourceEditBitmap() {
        _uiState.update { it.copy(sourceEditBitmap = null) }
    }

    fun generateOrEditImage() {
        val prompt = _uiState.value.imagePrompt.trim()
        if (prompt.isEmpty()) {
            _uiState.update { it.copy(imageState = AsyncActionState.Error("Enter a prompt to generate or edit an image.")) }
            return
        }
        val inputBmp = _uiState.value.sourceEditBitmap
        val ratio = _uiState.value.selectedAspectRatio

        viewModelScope.launch {
            _uiState.update { it.copy(imageState = AsyncActionState.Loading, savedImageMessage = null) }
            try {
                val result = aiRepository.generateOrEditImage(prompt, inputBmp, ratio)
                _uiState.update { it.copy(imageState = AsyncActionState.Success(result)) }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Image generation timed out", e)
                _uiState.update { it.copy(imageState = AsyncActionState.Error("Image generation timed out after 60 seconds.")) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Image generation failed", e)
                _uiState.update {
                    it.copy(imageState = AsyncActionState.Error(e.localizedMessage ?: "Image generation failed."))
                }
            }
        }
    }

    fun saveGeneratedImageToLibrary(bitmap: Bitmap) {
        viewModelScope.launch(Dispatchers.IO) {
            val title = _uiState.value.imagePrompt.take(40).ifBlank { "Vortex_AI_Image" }
            val fileName = safeFileManager.sanitizeFileName("$title.png", "png")
            val target = safeFileManager.resolveSafeTargetFile(DownloadCategory.OTHER, fileName)
            FileOutputStream(target).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            val size = target.length()
            downloadDao.insert(
                DownloadEntity(
                    url = "https://vortex.local/ai-image",
                    finalUrl = "https://vortex.local/ai-image",
                    title = title,
                    fileName = target.name,
                    localFilePath = target.absolutePath,
                    mimeType = "image/png",
                    category = DownloadCategory.OTHER.name,
                    resolutionLabel = "${bitmap.width}x${bitmap.height}",
                    totalBytes = size,
                    downloadedBytes = size,
                    status = DownloadStatus.COMPLETED.name
                )
            )
            _uiState.update { it.copy(savedImageMessage = "Saved '${target.name}' to Library.") }
        }
    }

    // --- Audio Recording & Transcription (gemini-3.5-transcribe) ---
    fun startMicRecording(context: Context) {
        if (_uiState.value.isRecordingAudio) return
        val outFile = File(context.cacheDir, "vortex_mic_${System.currentTimeMillis()}.m4a")
        runCatching {
            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(96_000)
                setAudioSamplingRate(16_000)
                setOutputFile(outFile.absolutePath)
                prepare()
                start()
            }
            mediaRecorder = recorder
            _uiState.update {
                it.copy(
                    isRecordingAudio = true,
                    recordingSeconds = 0,
                    recordedAudioFile = outFile,
                    transcribeState = AsyncActionState.Idle
                )
            }
            viewModelScope.launch {
                while (_uiState.value.isRecordingAudio) {
                    delay(1000L)
                    _uiState.update { st ->
                        if (st.isRecordingAudio) st.copy(recordingSeconds = st.recordingSeconds + 1) else st
                    }
                }
            }
        }.onFailure { err ->
            Log.e(TAG, "Microphone start failed", err)
            _uiState.update {
                it.copy(
                    isRecordingAudio = false,
                    transcribeState = AsyncActionState.Error(err.localizedMessage ?: "Failed to access microphone.")
                )
            }
        }
    }

    fun stopMicRecordingAndTranscribe() {
        val file = _uiState.value.recordedAudioFile
        runCatching {
            mediaRecorder?.stop()
            mediaRecorder?.release()
        }
        mediaRecorder = null
        _uiState.update { it.copy(isRecordingAudio = false) }
        if (file != null && file.exists() && file.length() > 0L) {
            transcribeFile(file, "audio/mp4")
        } else {
            _uiState.update { it.copy(transcribeState = AsyncActionState.Error("No audio was captured.")) }
        }
    }

    fun transcribeFile(file: File, mimeType: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(transcribeState = AsyncActionState.Loading) }
            try {
                val bytes = withContext(Dispatchers.IO) {
                    if (!file.exists()) throw IllegalStateException("Audio file not found.")
                    if (file.length() > 18L * 1024L * 1024L) {
                        throw IllegalStateException("Audio file exceeds the 18 MB inline limit.")
                    }
                    file.readBytes()
                }
                val transcript = aiRepository.transcribeAudio(
                    audioBytes = bytes,
                    mimeType = mimeType,
                    instruction = _uiState.value.transcribePromptHint
                )
                _uiState.update { it.copy(transcribeState = AsyncActionState.Success(transcript)) }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Transcription timed out", e)
                _uiState.update { it.copy(transcribeState = AsyncActionState.Error("Transcription timed out after 60 seconds.")) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Transcription failed", e)
                _uiState.update {
                    it.copy(transcribeState = AsyncActionState.Error(e.localizedMessage ?: "Audio transcription failed."))
                }
            }
        }
    }

    // --- Live Voice Conversations (gemini-3.8-live) ---
    fun selectLiveVoice(voiceName: String) {
        _uiState.update { it.copy(liveVoiceName = voiceName) }
    }

    fun startLiveVoiceSession() {
        if (_uiState.value.isLiveConnecting || _uiState.value.isLiveConnected) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLiveConnecting = true, liveError = null) }
            try {
                val model = aiRepository.createLiveVoiceModel(
                    voiceName = _uiState.value.liveVoiceName,
                    systemInstruction = _uiState.value.liveSystemPrompt
                )
                val session = model.connect()
                liveSession = session
                _uiState.update {
                    it.copy(
                        isLiveConnecting = false,
                        isLiveConnected = true,
                        liveTranscripts = emptyList()
                    )
                }
                session.startAudioConversation(
                    transcriptHandler = { userTranscript, modelTranscript ->
                        val userText = userTranscript?.text?.takeIf { it.isNotBlank() }
                        val modelText = modelTranscript?.text?.takeIf { it.isNotBlank() }
                        if (userText != null || modelText != null) {
                            _uiState.update { st ->
                                val updated = st.liveTranscripts.toMutableList()
                                if (userText != null) {
                                    updated.add(VoiceTranscriptLine(speaker = "You", text = userText))
                                }
                                if (modelText != null) {
                                    updated.add(VoiceTranscriptLine(speaker = "Gemini Live", text = modelText))
                                }
                                st.copy(liveTranscripts = updated.takeLast(50))
                            }
                        }
                    },
                    goAwayHandler = {
                        _uiState.update {
                            it.copy(liveError = "Live session is wrapping up soon (time limit reached).")
                        }
                    }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Live Voice failed", e)
                _uiState.update {
                    it.copy(
                        isLiveConnecting = false,
                        isLiveConnected = false,
                        liveError = e.localizedMessage ?: "Could not connect to Gemini Live."
                    )
                }
            }
        }
    }

    fun stopLiveVoiceSession() {
        val session = liveSession
        liveSession = null
        _uiState.update { it.copy(isLiveConnecting = false, isLiveConnected = false) }
        viewModelScope.launch {
            runCatching {
                session?.stopAudioConversation()
                session?.close()
            }
        }
    }

    // --- Music Generation (lyria-3-clip-preview / lyria-3-pro-preview) ---
    fun onMusicPromptChange(value: String) {
        _uiState.update { it.copy(musicPrompt = value) }
    }

    fun selectMusicModel(option: MusicModelOption) {
        _uiState.update { it.copy(selectedMusicModel = option) }
    }

    fun generateMusic() {
        val prompt = _uiState.value.musicPrompt.trim()
        if (prompt.isEmpty()) {
            _uiState.update { it.copy(musicState = AsyncActionState.Error("Describe the genre, mood, and instruments to compose.")) }
            return
        }
        val modelOption = _uiState.value.selectedMusicModel
        viewModelScope.launch {
            _uiState.update { it.copy(musicState = AsyncActionState.Loading) }
            try {
                val audioResult = aiRepository.generateMusic(modelOption, prompt)
                val ext = when {
                    audioResult.mimeType.contains("wav") -> "wav"
                    audioResult.mimeType.contains("ogg") || audioResult.mimeType.contains("opus") -> "opus"
                    else -> "mp3"
                }
                val entity = withContext(Dispatchers.IO) {
                    val title = prompt.take(36).ifBlank { "Lyria_Track" }
                    val fileName = safeFileManager.sanitizeFileName("$title.$ext", ext)
                    val targetFile = safeFileManager.resolveSafeTargetFile(DownloadCategory.AUDIO, fileName)
                    targetFile.writeBytes(audioResult.bytes)
                    val size = targetFile.length()
                    val record = DownloadEntity(
                        url = "https://vortex.local/ai-music/${modelOption.modelId}",
                        finalUrl = "https://vortex.local/ai-music/${modelOption.modelId}",
                        title = title,
                        fileName = targetFile.name,
                        localFilePath = targetFile.absolutePath,
                        mimeType = audioResult.mimeType,
                        category = DownloadCategory.AUDIO.name,
                        resolutionLabel = modelOption.displayName,
                        totalBytes = size,
                        downloadedBytes = size,
                        status = DownloadStatus.COMPLETED.name
                    )
                    val insertedId = downloadDao.insert(record)
                    record.copy(id = insertedId)
                }
                _uiState.update { it.copy(musicState = AsyncActionState.Success(entity)) }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Music generation timed out", e)
                _uiState.update { it.copy(musicState = AsyncActionState.Error("Music generation timed out after 60 seconds.")) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Music generation failed", e)
                _uiState.update {
                    it.copy(musicState = AsyncActionState.Error(e.localizedMessage ?: "Music generation failed."))
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        runCatching {
            mediaRecorder?.release()
            liveSession?.stopAudioConversation()
        }
    }

    private fun scaleBitmapIfNeeded(src: Bitmap, maxDim: Int): Bitmap {
        val w = src.width
        val h = src.height
        if (w <= maxDim && h <= maxDim) return src
        val ratio = minOf(maxDim.toFloat() / w, maxDim.toFloat() / h)
        return Bitmap.createScaledBitmap(src, (w * ratio).toInt(), (h * ratio).toInt(), true)
    }

    class Factory(
        private val aiRepository: VortexAiRepository,
        private val safeFileManager: SafeFileManager,
        private val downloadDao: DownloadDao
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AiStudioViewModel(aiRepository, safeFileManager, downloadDao) as T
        }
    }

    companion object {
        private const val TAG = "AiStudioViewModel"
    }
}
