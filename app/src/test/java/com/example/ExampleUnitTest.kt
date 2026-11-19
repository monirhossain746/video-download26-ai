package com.example

import com.example.ai.ChatModelOption
import com.example.ai.MusicModelOption
import com.example.ai.VortexAiRepository
import com.example.analyzer.FormatUtils
import com.example.data.local.DownloadCategory
import org.junit.Assert.assertEquals
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun `formatSpeed outputs KBps MBps and GBps`() {
        assertEquals("512.0 KB/s", FormatUtils.formatSpeed(512L * 1024L))
        assertEquals("2.50 MB/s", FormatUtils.formatSpeed((2.5 * 1024.0 * 1024.0).toLong()))
        assertEquals("1.50 GB/s", FormatUtils.formatSpeed((1.5 * 1024.0 * 1024.0 * 1024.0).toLong()))
    }

    @Test
    fun `download category classification handles all supported formats`() {
        assertEquals(DownloadCategory.VIDEO, DownloadCategory.fromMimeAndExtension("video/mp4", "clip.mp4"))
        assertEquals(DownloadCategory.VIDEO, DownloadCategory.fromMimeAndExtension(null, "movie.mkv"))
        assertEquals(DownloadCategory.AUDIO, DownloadCategory.fromMimeAndExtension("audio/mpeg", "track.mp3"))
        assertEquals(DownloadCategory.AUDIO, DownloadCategory.fromMimeAndExtension(null, "lossless.flac"))
        assertEquals(DownloadCategory.DOCUMENT, DownloadCategory.fromMimeAndExtension("application/pdf", "manual.pdf"))
        assertEquals(DownloadCategory.APK, DownloadCategory.fromMimeAndExtension(null, "installer.apk"))
        assertEquals(DownloadCategory.OTHER, DownloadCategory.fromMimeAndExtension("application/zip", "bundle.zip"))
    }

    @Test
    fun `ai model catalogs expose requested Gemini and Lyria model IDs`() {
        assertEquals(
            listOf("gemini-4-hydrogen", "gemini-4-carbon", "gemini-4-neon", "gemini-3.1-flash-lite"),
            ChatModelOption.entries.map { it.modelId }
        )
        assertEquals(
            listOf("lyria-3-clip-preview", "lyria-3-pro-preview"),
            MusicModelOption.entries.map { it.modelId }
        )
        assertEquals("gemini-nano-banana-2.1", VortexAiRepository.IMAGE_MODEL_ID)
        assertEquals("gemini-3.5-transcribe", VortexAiRepository.TRANSCRIBE_MODEL_ID)
        assertEquals("gemini-3.8-live", VortexAiRepository.LIVE_VOICE_MODEL_ID)
    }
}
