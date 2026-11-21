package com.prism.music.photon

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class ThumbnailPolicyTest {
    @Test fun boundsKnownUnsignedStandardPreviews() {
        for (name in listOf("maxresdefault", "hq720", "sddefault"))
            assertEquals("https://i.ytimg.com/vi/abcdefghijk/hqdefault.jpg", ThumbnailPolicy.cardUrl("https://i.ytimg.com/vi/abcdefghijk/$name.jpg"))
    }
    @Test fun preservesPortraitSignedCustomAndUntrustedPaths() {
        for (url in listOf("https://i.ytimg.com/vi/abcdefghijk/oar2.jpg", "https://i.ytimg.com/vi/abcdefghijk/maxresdefault.jpg?sqp=signed",
            "https://i.ytimg.com/vi_webp/abcdefghijk/maxresdefault.webp", "https://evil.example/vi/abcdefghijk/maxresdefault.jpg",
            "https://i.ytimg.com:8443/vi/abcdefghijk/maxresdefault.jpg", "https://user@i.ytimg.com/vi/abcdefghijk/maxresdefault.jpg", "/local/image.jpg"))
            assertEquals(url, ThumbnailPolicy.cardUrl(url))
    }
    @Test fun choosesBoundedSourceAndRetainsPortrait() {
        val sources = Json.parseToJsonElement("""[{"url":"https://i.ytimg.com/small.jpg","width":120,"height":90},{"url":"https://i.ytimg.com/card.jpg","width":480,"height":360},{"url":"https://i.ytimg.com/huge.jpg","width":1920,"height":1080},{"url":"https://i.ytimg.com/portrait.jpg","width":480,"height":854}]""").jsonArray
        assertEquals("https://i.ytimg.com/card.jpg", ThumbnailPolicy.select(sources, false))
        assertEquals("https://i.ytimg.com/portrait.jpg", ThumbnailPolicy.select(sources, true))
        assertEquals("", ThumbnailPolicy.select(null, true))
    }
    @Test fun rejectsForeignSourcesAndNormalizesSchemeRelativeArtwork() {
        assertEquals("https://i.ytimg.com/card.jpg", ThumbnailPolicy.select(Json.parseToJsonElement("""[{"url":"https://evil.example/card.jpg","width":480},{"url":"//i.ytimg.com/card.jpg","width":640}]""").jsonArray, false))
    }
}
