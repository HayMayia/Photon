package com.prism.music.photon

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable
enum class VideoProjection { FLAT, EQUIRECTANGULAR, EQUIRECTANGULAR_TOP_BOTTOM, EQUIRECTANGULAR_LEFT_RIGHT, UNSUPPORTED }

/** Source metadata only: a title, 2:1 image or the word "360" is never evidence. */
internal object ProjectionMetadata {
    private val videos = linkedMapOf<String, Map<Int, VideoProjection>>()
    fun parse(body: String): Pair<String, Map<Int, VideoProjection>>? = runCatching {
        val root = Json.parseToJsonElement(body).jsonObject
        val id = ((root["videoDetails"] as? JsonObject)?.get("videoId") as? JsonPrimitive)?.contentOrNull ?: return@runCatching null
        if (!id.matches(Regex("[A-Za-z0-9_-]{11}"))) return@runCatching null
        val data = root["streamingData"] as? JsonObject ?: return@runCatching null
        val formats = listOf("formats", "adaptiveFormats").flatMap { (data[it] as? JsonArray).orEmpty() }
        id to formats.mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            val itag = (item["itag"] as? JsonPrimitive)?.intOrNull ?: return@mapNotNull null
            val projection = if ((item["projectionType"] as? JsonPrimitive)?.contentOrNull == "EQUIRECTANGULAR") when ((item["stereoLayout"] as? JsonPrimitive)?.contentOrNull) {
                "STEREO_LAYOUT_TOP_BOTTOM" -> VideoProjection.EQUIRECTANGULAR_TOP_BOTTOM
                "STEREO_LAYOUT_LEFT_RIGHT" -> VideoProjection.EQUIRECTANGULAR_LEFT_RIGHT
                null, "STEREO_LAYOUT_MONO" -> VideoProjection.EQUIRECTANGULAR
                else -> VideoProjection.UNSUPPORTED
            } else when ((item["projectionType"] as? JsonPrimitive)?.contentOrNull) {
                "RECTANGULAR" -> VideoProjection.FLAT
                "MESH" -> VideoProjection.UNSUPPORTED
                else -> return@mapNotNull null
            }
            itag to projection
        }.toMap()
    }.getOrNull()
    @Synchronized fun observe(url: String, body: String) {
        val uri = runCatching { java.net.URI(url) }.getOrNull() ?: return
        if (uri.scheme != "https" || uri.host !in setOf("www.youtube.com", "youtubei.googleapis.com", "youtubei.youtube.com") || uri.path != "/youtubei/v1/player") return
        val (id, streams) = parse(body) ?: return
        videos[id] = videos[id].orEmpty() + streams
        while (videos.size > 64) videos.remove(videos.keys.first())
    }
    @Synchronized fun get(id: String, itag: Int) = videos[id]?.get(itag) ?: VideoProjection.FLAT
}

internal object StartupPolicy {
    // Restore the pre-1.1.0.1 startup threshold; steady playback takes priority over 250 ms.
    const val START_BUFFER_MS = 750
    const val REBUFFER_MS = 1500
}
