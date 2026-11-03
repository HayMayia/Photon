package com.prism.music.photon

import kotlinx.serialization.json.*
import java.io.IOException
import java.net.URI

/** Select only the requested playlist's primary content, including modern account list layouts. */
internal object PlaylistContents {
    fun primary(root: JsonObject, expected: String, requestBound: Boolean = false): JsonElement? {
        if (root.containsKey("error") || ((root["responseContext"] as? JsonObject)?.get("mainAppWebResponseContext") as? JsonObject)
                ?.get("loggedOut")?.jsonPrimitive?.booleanOrNull == true) throw IOException("The account playlist request was rejected")
        val contents = root["contents"] as? JsonObject ?: return null
        val lists = GoogleAccountControls.objects(contents).mapNotNull { it["playlistVideoListRenderer"] as? JsonObject }.toList()
        val matching = lists.firstOrNull { it["playlistId"]?.jsonPrimitive?.contentOrNull == expected }
        if (matching != null) return matching
        if (lists.any { it["playlistId"]?.jsonPrimitive?.contentOrNull?.let { id -> id != expected } == true })
            throw IOException("YouTube returned a different playlist; its entries were not substituted")
        lists.singleOrNull()?.let { return it }

        // New account pages use richGrid/sectionList instead of playlistVideoListRenderer.
        // The old selector rejected these even after a successful authenticated VL browse.
        val canonical = ((root["microformat"] as? JsonObject)?.get("microformatDataRenderer") as? JsonObject)
            ?.get("urlCanonical")?.jsonPrimitive?.contentOrNull
        val canonicalId = canonical?.let { url -> runCatching {
            val uri = URI(url)
            if (uri.host !in setOf("www.youtube.com", "youtube.com", "m.youtube.com")) null
            else uri.rawQuery?.split('&')?.firstOrNull { it.startsWith("list=") }?.substringAfter("list=")
        }.getOrNull() }
        if (canonicalId != null && canonicalId != expected) throw IOException("YouTube returned a different playlist identity")
        val browse = (contents["twoColumnBrowseResultsRenderer"] ?: contents["singleColumnBrowseResultsRenderer"]) as? JsonObject
        val tabs = (browse?.get("tabs") as? JsonArray)?.mapNotNull { (it as? JsonObject)?.get("tabRenderer") as? JsonObject }.orEmpty()
        val selected = tabs.firstOrNull { it["selected"]?.jsonPrimitive?.booleanOrNull == true }
            ?: tabs.singleOrNull()?.takeUnless { it["selected"]?.jsonPrimitive?.booleanOrNull == false }
        val endpoint = (selected?.get("endpoint") as? JsonObject)?.get("browseEndpoint") as? JsonObject
        val tabId = endpoint?.get("browseId")?.jsonPrimitive?.contentOrNull
        if (tabId?.startsWith("VL") == true && tabId != "VL$expected") throw IOException("The selected tab belongs to another playlist")
        if (!requestBound && canonicalId != expected && tabId != "VL$expected") return null
        val primary = (selected?.get("content") as? JsonObject) ?: if (browse == null) contents else return null
        return primary["richGridRenderer"] ?: primary["sectionListRenderer"] ?: primary["playlistPanelRenderer"]
    }
}
