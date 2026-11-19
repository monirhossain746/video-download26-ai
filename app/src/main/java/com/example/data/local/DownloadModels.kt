package com.example.data.local

enum class DownloadCategory(val label: String) {
    VIDEO("Videos"),
    AUDIO("Music"),
    DOCUMENT("Documents"),
    APK("APK Files"),
    OTHER("Other Files");

    companion object {
        fun fromMimeAndExtension(mimeType: String?, fileName: String): DownloadCategory {
            val lowerMime = mimeType?.lowercase().orEmpty()
            val ext = fileName.substringAfterLast('.', "").lowercase()
            return when {
                lowerMime.startsWith("video/") || ext in setOf("mp4", "mkv", "webm", "mov", "avi", "3gp", "m3u8") -> VIDEO
                lowerMime.startsWith("audio/") || ext in setOf("mp3", "m4a", "aac", "wav", "flac", "opus", "ogg") -> AUDIO
                ext == "apk" || lowerMime == "application/vnd.android.package-archive" -> APK
                ext in setOf("pdf", "doc", "docx", "txt", "csv", "epub", "ppt", "pptx", "xls", "xlsx") ||
                    lowerMime.startsWith("application/pdf") || lowerMime.startsWith("text/") -> DOCUMENT
                else -> OTHER
            }
        }
    }
}

enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED;

    val isActive: Boolean
        get() = this == QUEUED || this == DOWNLOADING
}
