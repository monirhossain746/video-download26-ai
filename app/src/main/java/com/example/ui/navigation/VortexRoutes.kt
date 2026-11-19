package com.example.ui.navigation

import android.net.Uri

object VortexRoutes {
    const val HOME = "home"
    const val AI_STUDIO = "ai_studio"
    const val BROWSER = "browser"
    const val DOWNLOADS = "downloads"
    const val LIBRARY = "library"
    const val SETTINGS = "settings"
    const val PLAYER = "player/{downloadId}/{streamUrl}/{title}"

    fun playerForDownload(downloadId: Long, title: String): String {
        return "player/$downloadId/${Uri.encode("")}/${Uri.encode(title)}"
    }

    fun playerForStream(streamUrl: String, title: String): String {
        return "player/-1/${Uri.encode(streamUrl)}/${Uri.encode(title)}"
    }
}
