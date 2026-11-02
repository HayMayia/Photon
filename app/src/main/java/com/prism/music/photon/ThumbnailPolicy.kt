package com.prism.music.photon

import java.net.URI
import kotlinx.serialization.json.*

/** Keep portrait/signed/custom artwork intact; avoid multi-megabyte standard max-resolution previews. */
internal object ThumbnailPolicy {
    fun cardUrl(raw: String): String = runCatching {
        val uri = URI(raw)
        if (uri.scheme != "https" || uri.userInfo != null || uri.port !in setOf(-1, 443) || uri.host !in setOf("i.ytimg.com", "img.youtube.com") || uri.rawQuery != null || uri.rawFragment != null) return@runCatching raw
        val path = uri.path
        if (Regex("/vi/[A-Za-z0-9_-]{11}/(maxresdefault|hq720|sddefault)\\.jpg").matches(path))
            "https://${uri.host}${path.substringBeforeLast('/')}/hqdefault.jpg" else raw
    }.getOrDefault(raw)
    fun select(sources: JsonArray?, short: Boolean): String {
        val candidates = sources.orEmpty().mapNotNull { it as? JsonObject }.filter { ShortsPresentation.safeArtwork((it["url"] as? JsonPrimitive)?.contentOrNull.orEmpty()).isNotBlank() }
        val portrait = if (short) candidates.filter { ((it["height"] as? JsonPrimitive)?.intOrNull ?: 0) > ((it["width"] as? JsonPrimitive)?.intOrNull ?: Int.MAX_VALUE) } else emptyList()
        val pool = portrait.ifEmpty { candidates }
        val chosen = pool.filter { ((it["width"] as? JsonPrimitive)?.intOrNull ?: 0) >= 480 }
            .minByOrNull { (it["width"] as? JsonPrimitive)?.intOrNull ?: Int.MAX_VALUE }
            ?: pool.maxByOrNull { (it["width"] as? JsonPrimitive)?.intOrNull ?: 0 }
        return ShortsPresentation.safeArtwork((chosen?.get("url") as? JsonPrimitive)?.contentOrNull.orEmpty())
    }
}
