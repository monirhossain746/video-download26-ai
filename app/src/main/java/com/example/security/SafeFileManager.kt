package com.example.security

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.data.local.DownloadCategory
import java.io.File
import java.security.MessageDigest

class SafeFileManager(private val context: Context) {

    fun getRootDownloadDir(): File {
        val external = context.getExternalFilesDir("VortexDownloads")
        val root = external ?: File(context.filesDir, "VortexDownloads")
        if (!root.exists()) root.mkdirs()
        return root
    }

    fun getCategoryDir(category: DownloadCategory): File {
        val dir = File(getRootDownloadDir(), category.name.lowercase())
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun sanitizeFileName(rawName: String, fallbackExt: String = "bin"): String {
        val stripped = rawName
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .replace("..", "_")
            .replace("\u0000", "")
            .trim()
        val safe = stripped.replace(Regex("[^a-zA-Z0-9._\\- ]"), "_")
            .replace(Regex("\\s+"), "_")
            .trim('.', '_', ' ')
        val candidate = if (safe.isBlank()) "vortex_media.$fallbackExt" else safe
        return if (candidate.contains('.')) candidate.take(120) else "${candidate.take(100)}.$fallbackExt"
    }

    fun resolveSafeTargetFile(category: DownloadCategory, desiredName: String): File {
        val dir = getCategoryDir(category)
        val cleanName = sanitizeFileName(desiredName)
        val dotIndex = cleanName.lastIndexOf('.')
        val base = if (dotIndex > 0) cleanName.substring(0, dotIndex) else cleanName
        val ext = if (dotIndex > 0) cleanName.substring(dotIndex) else ""

        var candidate = File(dir, cleanName)
        var counter = 1
        while (candidate.exists() || File("${candidate.absolutePath}.part").exists()) {
            candidate = File(dir, "${base}_($counter)$ext")
            counter++
        }
        assertPathInsideBase(dir, candidate)
        return candidate
    }

    fun renameSafeFile(existingFile: File, newRawName: String): File? {
        val parent = existingFile.parentFile ?: return null
        assertPathInsideBase(getRootDownloadDir(), existingFile)
        val currentExt = existingFile.extension
        val sanitized = sanitizeFileName(
            if (newRawName.contains('.')) newRawName else if (currentExt.isNotEmpty()) "$newRawName.$currentExt" else newRawName
        )
        val target = File(parent, sanitized)
        assertPathInsideBase(getRootDownloadDir(), target)
        if (target.exists()) return null
        return if (existingFile.renameTo(target)) target else null
    }

    fun deleteSafeFile(file: File): Boolean {
        return runCatching {
            assertPathInsideBase(getRootDownloadDir(), file)
            if (file.exists()) file.delete() else true
        }.getOrDefault(false)
    }

    fun getAvailableBytes(): Long {
        return runCatching {
            val stat = StatFs(getRootDownloadDir().absolutePath)
            stat.availableBytes
        }.getOrDefault(0L)
    }

    fun hasEnoughSpace(requiredBytes: Long): Boolean {
        if (requiredBytes <= 0L) return true
        // Keep at least 64 MB buffer free on device
        val bufferBytes = 64L * 1024L * 1024L
        return getAvailableBytes() > (requiredBytes + bufferBytes)
    }

    fun getContentUri(file: File): Uri {
        assertPathInsideBase(getRootDownloadDir(), file)
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    fun exportToPublicDownloads(sourceFile: File, mimeType: String): Boolean {
        if (!sourceFile.exists()) return false
        assertPathInsideBase(getRootDownloadDir(), sourceFile)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false

        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, sourceFile.name)
            put(MediaStore.Downloads.MIME_TYPE, mimeType)
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/Vortex")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
        return runCatching {
            resolver.openOutputStream(uri)?.use { out ->
                sourceFile.inputStream().use { input -> input.copyTo(out) }
            }
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            true
        }.getOrElse {
            resolver.delete(uri, null, null)
            false
        }
    }

    fun computeSha256(file: File): String? {
        if (!file.exists()) return null
        return runCatching {
            assertPathInsideBase(getRootDownloadDir(), file)
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { stream ->
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val read = stream.read(buffer)
                    if (read <= 0) break
                    digest.update(buffer, 0, read)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }.getOrNull()
    }

    private fun assertPathInsideBase(baseDir: File, target: File) {
        val baseCanonical = baseDir.canonicalPath
        val targetCanonical = target.canonicalPath
        require(targetCanonical == baseCanonical || targetCanonical.startsWith("$baseCanonical${File.separator}")) {
            "Path traversal attempt blocked: $targetCanonical"
        }
    }
}
