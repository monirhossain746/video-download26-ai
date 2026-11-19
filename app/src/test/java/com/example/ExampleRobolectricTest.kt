package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.ChatMessageEntity
import com.example.data.local.DownloadCategory
import com.example.data.local.VortexDatabase
import com.example.security.SafeFileManager
import com.example.security.UrlSecurityValidator
import com.example.security.UrlValidationResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `app name matches Vortex`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Vortex", appName)
    }

    @Test
    fun `url security validator blocks cleartext http and ssrf targets`() {
        assertTrue(UrlSecurityValidator.validate("http://example.com/video.mp4") is UrlValidationResult.Invalid)
        assertTrue(UrlSecurityValidator.validate("javascript:alert(1)") is UrlValidationResult.Invalid)
        assertTrue(UrlSecurityValidator.validate("https://127.0.0.1/secret.mp4") is UrlValidationResult.Invalid)
        assertTrue(UrlSecurityValidator.validate("https://192.168.1.10/media.mp4") is UrlValidationResult.Invalid)
        assertTrue(UrlSecurityValidator.validate("https://localhost/test.mp4") is UrlValidationResult.Invalid)
        assertTrue(UrlSecurityValidator.validate("https://youtube.com/watch?v=123") is UrlValidationResult.RestrictedPlatform)
        assertTrue(UrlSecurityValidator.validate("https://cdn.example.org/videos/sample.mp4") is UrlValidationResult.Valid)
    }

    @Test
    fun `safe file manager prevents path traversal`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val fileManager = SafeFileManager(context)
        val sanitized = fileManager.sanitizeFileName("../../etc/passwd", "mp4")
        assertFalse(sanitized.contains(".."))
        assertFalse(sanitized.contains("/"))

        val target = fileManager.resolveSafeTargetFile(DownloadCategory.VIDEO, "../evil/movie.mp4")
        assertTrue(target.canonicalPath.startsWith(fileManager.getRootDownloadDir().canonicalPath))
    }

    @Test
    fun `room chat message dao persists and clears conversation`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, VortexDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = db.chatMessageDao()
            dao.insert(ChatMessageEntity(role = "user", text = "Hello", modelName = "gemini-4-hydrogen", createdAt = 1L))
            dao.insert(ChatMessageEntity(role = "model", text = "Hi there!", modelName = "gemini-4-hydrogen", createdAt = 2L))

            val saved = dao.getAllMessages()
            assertEquals(2, saved.size)
            assertEquals("user", saved[0].role)
            assertEquals("Hi there!", saved[1].text)

            dao.clearAll()
            assertTrue(dao.getAllMessages().isEmpty())
        } finally {
            db.close()
        }
    }
}
