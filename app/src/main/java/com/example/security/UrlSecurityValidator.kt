package com.example.security

import android.net.Uri
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

sealed class UrlValidationResult {
    data class Valid(val normalizedUrl: String, val host: String) : UrlValidationResult()
    data class RestrictedPlatform(val host: String, val reason: String) : UrlValidationResult()
    data class Invalid(val reason: String) : UrlValidationResult()
}

object UrlSecurityValidator {

    private const val MAX_URL_LENGTH = 2048

    private val youtubeHosts = setOf(
        "youtube.com",
        "www.youtube.com",
        "m.youtube.com",
        "music.youtube.com",
        "youtu.be"
    )

    private val drmRestrictedHosts = setOf(
        "netflix.com",
        "www.netflix.com",
        "disneyplus.com",
        "www.disneyplus.com",
        "primevideo.com",
        "www.primevideo.com",
        "hulu.com",
        "www.hulu.com",
        "max.com",
        "www.max.com",
        "spotify.com",
        "open.spotify.com"
    )

    fun validate(rawInput: String, checkPlatformPolicy: Boolean = true): UrlValidationResult {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) {
            return UrlValidationResult.Invalid("Please enter a valid HTTPS media URL.")
        }
        if (trimmed.length > MAX_URL_LENGTH) {
            return UrlValidationResult.Invalid("URL exceeds maximum allowed length ($MAX_URL_LENGTH characters).")
        }
        if (trimmed.any { it.isWhitespace() || it.code < 32 }) {
            return UrlValidationResult.Invalid("URL contains invalid whitespace or control characters.")
        }

        val parsed = runCatching { Uri.parse(trimmed) }.getOrNull()
            ?: return UrlValidationResult.Invalid("Malformed URL syntax.")

        val scheme = parsed.scheme?.lowercase()
        if (scheme != "https") {
            val detail = when (scheme) {
                "http" -> "Cleartext HTTP is blocked for your security. Only encrypted HTTPS URLs are permitted."
                "javascript", "file", "content", "data", "intent" -> "Unsafe URI scheme '$scheme://' is blocked."
                null -> "URL must begin with https://"
                else -> "Unsupported URI scheme '$scheme://'. Only https:// is allowed."
            }
            return UrlValidationResult.Invalid(detail)
        }

        if (!parsed.userInfo.isNullOrEmpty()) {
            return UrlValidationResult.Invalid("URLs containing embedded credentials are blocked for security.")
        }

        val host = parsed.host?.lowercase()?.trimEnd('.')
            ?: return UrlValidationResult.Invalid("URL is missing a valid domain host.")

        if (!isSafePublicHost(host)) {
            return UrlValidationResult.Invalid("Local, private network, or loopback addresses are blocked for security.")
        }

        if (checkPlatformPolicy) {
            if (host in youtubeHosts || host.endsWith(".youtube.com")) {
                return UrlValidationResult.RestrictedPlatform(
                    host = host,
                    reason = "YouTube downloads are restricted under YouTube Terms of Service and official API policies. Vortex does not perform unauthorized YouTube extraction."
                )
            }
            if (host in drmRestrictedHosts || drmRestrictedHosts.any { host.endsWith(".$it") }) {
                return UrlValidationResult.RestrictedPlatform(
                    host = host,
                    reason = "This service uses DRM or subscription access controls. Vortex strictly respects content protection and does not bypass DRM or authentication."
                )
            }
        }

        return UrlValidationResult.Valid(normalizedUrl = parsed.toString(), host = host)
    }

    fun looksLikeDownloadableMediaUrl(url: String): Boolean {
        if (validate(url, checkPlatformPolicy = true) !is UrlValidationResult.Valid) return false
        val path = runCatching { Uri.parse(url).path?.lowercase().orEmpty() }.getOrDefault("")
        val ext = path.substringAfterLast('.', "")
        return ext in setOf(
            "mp4", "mkv", "webm", "mov", "avi", "3gp", "m3u8",
            "mp3", "m4a", "aac", "wav", "flac", "opus",
            "pdf", "zip", "apk"
        )
    }

    internal fun isSafePublicHost(host: String): Boolean {
        if (host.isEmpty() || host.length > 253) return false
        if (host == "localhost" || host.endsWith(".localhost") || host.endsWith(".local") || host.endsWith(".internal")) {
            return false
        }

        val ipLiteral = host.removePrefix("[").removeSuffix("]")
        val isNumericIp = ipLiteral.all { it.isDigit() || it == '.' } || ipLiteral.contains(':')
        if (isNumericIp) {
            val addr = runCatching { InetAddress.getByName(ipLiteral) }.getOrNull() ?: return false
            if (addr.isAnyLocalAddress || addr.isLoopbackAddress || addr.isLinkLocalAddress ||
                addr.isSiteLocalAddress || addr.isMulticastAddress
            ) {
                return false
            }
            if (addr is Inet4Address) {
                val b = addr.address
                val first = b[0].toInt() and 0xFF
                val second = b[1].toInt() and 0xFF
                // Block CGNAT 100.64.0.0/10, 0.0.0.0/8, 169.254.0.0/16
                if (first == 0 || (first == 100 && second in 64..127) || (first == 169 && second == 254)) {
                    return false
                }
            }
            if (addr is Inet6Address) {
                val first = addr.address[0].toInt() and 0xFF
                if ((first and 0xFE) == 0xFC) return false // Unique local fc00::/7
            }
        } else {
            if (!host.contains('.') || host.startsWith('.') || host.contains("..")) return false
            val domainRegex = Regex("^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?)+$")
            if (!domainRegex.matches(host)) return false
        }
        return true
    }
}
