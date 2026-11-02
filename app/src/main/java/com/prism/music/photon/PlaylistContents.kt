package com.prism.music.photon

import kotlinx.serialization.json.*
import java.io.IOException

/** Never substitute a preview/sidebar for the entries of the requested playlist. */
internal object PlaylistContents {
    fun primary(root: JsonObject, expected: String): JsonElement? {
        val contents = root["contents"] ?: return null
        val lists = GoogleAccountControls.objects(contents).mapNotNull { it["playlistVideoListRenderer"] as? JsonObject }.toList()
        val matching = lists.firstOrNull { it["playlistId"]?.jsonPrimitive?.contentOrNull == expected }
        if (matching != null) return matching
        if (lists.any { it["playlistId"]?.jsonPrimitive?.contentOrNull?.let { id -> id != expected } == true })
            throw IOException("YouTube returned a different playlist; its entries were not substituted")
        return lists.singleOrNull()
    }
}
