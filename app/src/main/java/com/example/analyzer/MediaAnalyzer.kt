package com.example.analyzer

import android.net.Uri
import com.example.data.local.DownloadCategory
import com.example.security.UrlSecurityValidator
import com.example.security.UrlValidationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLDecoder

data class MediaFormatOption(
    val id: String,
    val url: String,
    val resolutionLabel: String,
    val qualityLabel: String,
    val containerExt: String,
    val mimeType: String,
    val estimatedSizeBytes: Long,
    val supportsRanges: Boolean,
    val category: DownloadCategory,
    val isStreamPlaylist: Boolean = false
)

sealed class AnalyzedMediaResult {
    data class Success(
        val originalUrl: String,
        val sourceHost: String,
        val title: String,
        val thumbnailUrl: String?,
        val durationText: String?,
        val formats: List<MediaFormatOption>
    ) : AnalyzedMediaResult()

    data class Restricted(
        val host: String,
        val reason: String
    ) : AnalyzedMediaResult()

    data class Error(
        val message: String
    ) : AnalyzedMediaResult()
}

class MediaAnalyzer(private val okHttpClient: OkHttpClient) {

    suspend fun analyze(rawUrl: String): AnalyzedMediaResult = withContext(Dispatchers.IO) {
        when (val validation = UrlSecurityValidator.validate(rawUrl, checkPlatformPolicy = true)) {
            is UrlValidationResult.Invalid -> return@withContext AnalyzedMediaResult.Error(validation.reason)
            is UrlValidationResult.RestrictedPlatform -> {
                return@withContext AnalyzedMediaResult.Restricted(validation.host, validation.reason)
            }
            is UrlValidationResult.Valid -> {
                inspectValidUrl(validation.normalizedUrl, validation.host)
            }
        }
    }

    private fun inspectValidUrl(url: String, host: String): AnalyzedMediaResult {
        return runCatching {
            val headRequest = Request.Builder().url(url).head().build()
            val headMeta = okHttpClient.newCall(headRequest).execute().use { resp ->
                if (resp.isSuccessful) {
                    HeaderMeta(
                        finalUrl = resp.request.url.toString(),
                        contentType = resp.header("Content-Type")?.substringBefore(';')?.trim()?.lowercase(),
                        contentLength = resp.header("Content-Length")?.toLongOrNull() ?: -1L,
                        acceptRanges = resp.header("Accept-Ranges")?.contains("bytes", ignoreCase = true) == true,
                        contentDisposition = resp.header("Content-Disposition")
                    )
                } else {
                    null
                }
            }

            val meta = headMeta ?: probeWithRangeGet(url)
                ?: return AnalyzedMediaResult.Error("Unable to reach media server or inspect headers.")

            val finalUri = Uri.parse(meta.finalUrl)
            val finalHost = finalUri.host ?: host
            val contentType = meta.contentType.orEmpty()
            val fileName = extractFileName(meta.finalUrl, meta.contentDisposition, contentType)

            // HLS Master / Media Playlist (.m3u8)
            if (contentType.contains("mpegurl") || meta.finalUrl.lowercase().contains(".m3u8")) {
                return parseHlsPlaylist(meta.finalUrl, finalHost, fileName)
            }

            // HTML Web Page -> inspect only standard HTML5 / OpenGraph / direct media tags
            if (contentType.contains("text/html") || contentType.contains("application/xhtml")) {
                return parseHtmlMediaPage(meta.finalUrl, finalHost)
            }

            // Direct downloadable file (Video, Audio, PDF, ZIP, APK, etc.)
            val category = DownloadCategory.fromMimeAndExtension(contentType, fileName)
            val ext = fileName.substringAfterLast('.', "").uppercase().ifEmpty { "FILE" }
            val qualityTuple = when (category) {
                DownloadCategory.VIDEO -> "Original" to "Direct $ext Stream"
                DownloadCategory.AUDIO -> "Audio" to "Direct $ext Audio"
                DownloadCategory.DOCUMENT -> "Document" to "$ext Document"
                DownloadCategory.APK -> "Android Package" to "APK Archive"
                DownloadCategory.OTHER -> "Direct File" to "$ext Binary"
            }

            val format = MediaFormatOption(
                id = "direct_0",
                url = meta.finalUrl,
                resolutionLabel = qualityTuple.first,
                qualityLabel = qualityTuple.second,
                containerExt = ext.lowercase(),
                mimeType = contentType.ifBlank { guessMimeFromExt(ext) },
                estimatedSizeBytes = meta.contentLength.coerceAtLeast(0L),
                supportsRanges = meta.acceptRanges,
                category = category
            )

            AnalyzedMediaResult.Success(
                originalUrl = url,
                sourceHost = finalHost,
                title = fileName.substringBeforeLast('.').replace('_', ' '),
                thumbnailUrl = null,
                durationText = null,
                formats = listOf(format)
            )
        }.getOrElse { err ->
            AnalyzedMediaResult.Error(err.message ?: "Failed to analyze media link.")
        }
    }

    private fun probeWithRangeGet(url: String): HeaderMeta? {
        val req = Request.Builder()
            .url(url)
            .header("Range", "bytes=0-1023")
            .get()
            .build()
        return okHttpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful && resp.code != 206) return null
            val contentRange = resp.header("Content-Range")
            val totalFromRange = contentRange?.substringAfterLast('/')?.toLongOrNull() ?: -1L
            val length = if (totalFromRange > 0L) totalFromRange else (resp.header("Content-Length")?.toLongOrNull() ?: -1L)
            HeaderMeta(
                finalUrl = resp.request.url.toString(),
                contentType = resp.header("Content-Type")?.substringBefore(';')?.trim()?.lowercase(),
                contentLength = length,
                acceptRanges = resp.code == 206 || resp.header("Accept-Ranges")?.contains("bytes", ignoreCase = true) == true,
                contentDisposition = resp.header("Content-Disposition")
            )
        }
    }

    private fun parseHlsPlaylist(playlistUrl: String, host: String, defaultTitle: String): AnalyzedMediaResult {
        val req = Request.Builder().url(playlistUrl).get().build()
        val bodyText = okHttpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return AnalyzedMediaResult.Error("Playlist returned HTTP ${resp.code}")
            resp.body?.string()?.take(256 * 1024).orEmpty()
        }

        val lines = bodyText.lines()
        val formats = mutableListOf<MediaFormatOption>()
        var pendingResolutionHeight: Int? = null
        var pendingBandwidth: Long = 0L

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.startsWith("#EXT-X-STREAM-INF:")) {
                val resMatch = Regex("RESOLUTION=\\d+x(\\d+)").find(line)
                pendingResolutionHeight = resMatch?.groupValues?.getOrNull(1)?.toIntOrNull()
                val bwMatch = Regex("BANDWIDTH=(\\d+)").find(line)
                pendingBandwidth = bwMatch?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0L
            } else if (line.isNotEmpty() && !line.startsWith("#") && pendingResolutionHeight != null) {
                val resolvedUrl = resolveRelativeUrl(playlistUrl, line)
                if (UrlSecurityValidator.validate(resolvedUrl, checkPlatformPolicy = true) is UrlValidationResult.Valid) {
                    val (resLabel, qualLabel) = FormatUtils.qualityBadgeForHeight(pendingResolutionHeight)
                    val bitrateMbps = if (pendingBandwidth > 0L) {
                        String.format(java.util.Locale.US, " (%.1f Mbps)", pendingBandwidth / 1_000_000.0)
                    } else ""
                    formats.add(
                        MediaFormatOption(
                            id = "hls_${formats.size}",
                            url = resolvedUrl,
                            resolutionLabel = resLabel,
                            qualityLabel = "$qualLabel$bitrateMbps",
                            containerExt = "m3u8",
                            mimeType = "application/vnd.apple.mpegurl",
                            estimatedSizeBytes = 0L,
                            supportsRanges = false,
                            category = DownloadCategory.VIDEO,
                            isStreamPlaylist = true
                        )
                    )
                }
                pendingResolutionHeight = null
                pendingBandwidth = 0L
            }
        }

        val sortedFormats = formats.distinctBy { it.resolutionLabel }.sortedByDescending {
            it.resolutionLabel.removeSuffix("p").toIntOrNull() ?: 0
        }

        val finalFormats = sortedFormats.ifEmpty {
            listOf(
                MediaFormatOption(
                    id = "hls_main",
                    url = playlistUrl,
                    resolutionLabel = "Adaptive",
                    qualityLabel = "HLS Stream",
                    containerExt = "m3u8",
                    mimeType = "application/vnd.apple.mpegurl",
                    estimatedSizeBytes = 0L,
                    supportsRanges = false,
                    category = DownloadCategory.VIDEO,
                    isStreamPlaylist = true
                )
            )
        }

        return AnalyzedMediaResult.Success(
            originalUrl = playlistUrl,
            sourceHost = host,
            title = defaultTitle.substringBeforeLast('.').ifBlank { "HLS Media Stream" },
            thumbnailUrl = null,
            durationText = "Adaptive Stream",
            formats = finalFormats
        )
    }

    private fun parseHtmlMediaPage(pageUrl: String, host: String): AnalyzedMediaResult {
        val req = Request.Builder().url(pageUrl).get().build()
        val html = okHttpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return AnalyzedMediaResult.Error("Page returned HTTP ${resp.code}")
            resp.body?.string()?.take(512 * 1024).orEmpty()
        }

        val title = extractMetaContent(html, "og:title")
            ?: Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                .find(html)?.groupValues?.getOrNull(1)?.trim()?.replace(Regex("\\s+"), " ")
            ?: host

        val thumbnail = extractMetaContent(html, "og:image")?.let { resolveRelativeUrl(pageUrl, it) }
        val ogVideo = extractMetaContent(html, "og:video:secure_url")
            ?: extractMetaContent(html, "og:video:url")
            ?: extractMetaContent(html, "og:video")

        val discoveredUrls = linkedSetOf<Pair<String, Int?>>()
        if (!ogVideo.isNullOrBlank()) {
            discoveredUrls.add(resolveRelativeUrl(pageUrl, ogVideo) to null)
        }

        // Parse <source src="..." size="720" label="720p"> or <video src="...">
        val sourceTagRegex = Regex(
            "<(?:source|video|audio)[^>]+src=[\"']([^\"']+)[\"'][^>]*>",
            RegexOption.IGNORE_CASE
        )
        sourceTagRegex.findAll(html).forEach { match ->
            val tag = match.value
            val src = resolveRelativeUrl(pageUrl, match.groupValues[1])
            val sizeAttr = Regex("(?:size|data-res|label)=[\"'](\\d{3,4})p?[\"']", RegexOption.IGNORE_CASE)
                .find(tag)?.groupValues?.getOrNull(1)?.toIntOrNull()
            discoveredUrls.add(src to sizeAttr)
        }

        // Direct media links in anchors
        val directHrefRegex = Regex(
            "href=[\"']([^\"']+\\.(?:mp4|mkv|webm|mov|mp3|m4a|wav|flac|pdf|zip|apk)(?:\\?[^\"']*)?)[\"']",
            RegexOption.IGNORE_CASE
        )
        directHrefRegex.findAll(html).take(12).forEach { match ->
            val resolved = resolveRelativeUrl(pageUrl, match.groupValues[1])
            discoveredUrls.add(resolved to null)
        }

        val formats = mutableListOf<MediaFormatOption>()
        for ((candidateUrl, heightHint) in discoveredUrls.take(8)) {
            if (UrlSecurityValidator.validate(candidateUrl, checkPlatformPolicy = true) !is UrlValidationResult.Valid) {
                continue
            }
            val probe = runCatching { probeHeadQuietly(candidateUrl) }.getOrNull()
            val fileName = extractFileName(candidateUrl, probe?.contentDisposition, probe?.contentType.orEmpty())
            val mime = probe?.contentType?.takeIf { it.isNotBlank() }
                ?: guessMimeFromExt(fileName.substringAfterLast('.', "mp4"))
            val category = DownloadCategory.fromMimeAndExtension(mime, fileName)
            val ext = fileName.substringAfterLast('.', "mp4").lowercase()
            val (resLabel, qualLabel) = if (heightHint != null) {
                FormatUtils.qualityBadgeForHeight(heightHint)
            } else {
                when (category) {
                    DownloadCategory.VIDEO -> "Original" to "Direct ${ext.uppercase()}"
                    DownloadCategory.AUDIO -> "Audio" to "Direct ${ext.uppercase()}"
                    else -> ext.uppercase() to "Direct File"
                }
            }

            formats.add(
                MediaFormatOption(
                    id = "page_${formats.size}",
                    url = probe?.finalUrl ?: candidateUrl,
                    resolutionLabel = resLabel,
                    qualityLabel = qualLabel,
                    containerExt = ext,
                    mimeType = mime,
                    estimatedSizeBytes = (probe?.contentLength ?: 0L).coerceAtLeast(0L),
                    supportsRanges = probe?.acceptRanges ?: false,
                    category = category,
                    isStreamPlaylist = ext == "m3u8"
                )
            )
        }

        if (formats.isEmpty()) {
            return AnalyzedMediaResult.Error(
                "No directly downloadable authorized media streams were found on this page."
            )
        }

        return AnalyzedMediaResult.Success(
            originalUrl = pageUrl,
            sourceHost = host,
            title = title,
            thumbnailUrl = thumbnail,
            durationText = null,
            formats = formats
        )
    }

    private fun probeHeadQuietly(url: String): HeaderMeta? {
        val req = Request.Builder().url(url).head().build()
        return okHttpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            HeaderMeta(
                finalUrl = resp.request.url.toString(),
                contentType = resp.header("Content-Type")?.substringBefore(';')?.trim()?.lowercase(),
                contentLength = resp.header("Content-Length")?.toLongOrNull() ?: -1L,
                acceptRanges = resp.header("Accept-Ranges")?.contains("bytes", ignoreCase = true) == true,
                contentDisposition = resp.header("Content-Disposition")
            )
        }
    }

    private fun extractMetaContent(html: String, property: String): String? {
        val p1 = Regex(
            "<meta[^>]+(?:property|name)=[\"']${Regex.escape(property)}[\"'][^>]+content=[\"']([^\"']+)[\"'][^>]*>",
            RegexOption.IGNORE_CASE
        ).find(html)?.groupValues?.getOrNull(1)
        if (!p1.isNullOrBlank()) return p1
        return Regex(
            "<meta[^>]+content=[\"']([^\"']+)[\"'][^>]+(?:property|name)=[\"']${Regex.escape(property)}[\"'][^>]*>",
            RegexOption.IGNORE_CASE
        ).find(html)?.groupValues?.getOrNull(1)
    }

    private fun resolveRelativeUrl(baseUrl: String, candidate: String): String {
        val clean = candidate.trim()
        if (clean.startsWith("https://") || clean.startsWith("http://")) return clean
        val base = Uri.parse(baseUrl)
        if (clean.startsWith("//")) return "${base.scheme ?: "https"}:$clean"
        return runCatching {
            java.net.URI(baseUrl).resolve(clean).toString()
        }.getOrDefault(clean)
    }

    private fun extractFileName(url: String, contentDisposition: String?, contentType: String): String {
        if (!contentDisposition.isNullOrBlank()) {
            val match = Regex("filename\\*?=(?:UTF-8''|\"?)([^\";]+)\"?", RegexOption.IGNORE_CASE)
                .find(contentDisposition)?.groupValues?.getOrNull(1)
            if (!match.isNullOrBlank()) {
                return runCatching { URLDecoder.decode(match, "UTF-8") }.getOrDefault(match)
            }
        }
        val segment = Uri.parse(url).lastPathSegment?.takeIf { it.isNotBlank() }
        if (!segment.isNullOrBlank() && segment.contains('.')) return segment
        val ext = when {
            contentType.contains("mp4") -> "mp4"
            contentType.contains("webm") -> "webm"
            contentType.contains("matroska") -> "mkv"
            contentType.contains("mpeg") && contentType.startsWith("audio") -> "mp3"
            contentType.contains("pdf") -> "pdf"
            contentType.contains("zip") -> "zip"
            contentType.contains("android.package-archive") -> "apk"
            else -> "mp4"
        }
        return "${segment ?: "vortex_download"}.$ext"
    }

    private fun guessMimeFromExt(ext: String): String {
        return when (ext.lowercase()) {
            "mp4" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "mov" -> "video/quicktime"
            "avi" -> "video/x-msvideo"
            "3gp" -> "video/3gpp"
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            "aac" -> "audio/aac"
            "wav" -> "audio/wav"
            "flac" -> "audio/flac"
            "opus" -> "audio/opus"
            "pdf" -> "application/pdf"
            "zip" -> "application/zip"
            "apk" -> "application/vnd.android.package-archive"
            else -> "application/octet-stream"
        }
    }

    private data class HeaderMeta(
        val finalUrl: String,
        val contentType: String?,
        val contentLength: Long,
        val acceptRanges: Boolean,
        val contentDisposition: String?
    )
}
