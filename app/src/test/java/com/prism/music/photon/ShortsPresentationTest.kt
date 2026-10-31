package com.prism.music.photon

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class ShortsPresentationTest {
    private val id = "abcdefghijk"
    @Test fun resolvedPlaylistMetadataReachesTheOpenCallbackWithoutLosingItsTitle() {
        val row = Video(id, "Playlist title", "Creator", "https://i.ytimg.com/landscape.jpg")
        val resolved = row.copy(short = true, thumbnail = "https://i.ytimg.com/portrait.jpg")
        val opened = ShortsPresentation.merge(row, resolved)
        assertTrue(opened.short)
        assertEquals("Playlist title", opened.title)
        assertTrue(opened.thumbnail.endsWith("portrait.jpg"))
        assertTrue(ShortsPresentation.merge(opened, row).short)
        assertEquals(opened.thumbnail, ShortsPresentation.merge(opened, row).thumbnail)
    }
    @Test fun canonicalShortsRouteWorksWithoutPlaylistShortsBadges() {
        val result = ShortsPresentation.fromHtml(id, """<link rel="canonical" href="https://www.youtube.com/shorts/$id"><meta property="og:image" content="https://i.ytimg.com/vi/$id/portrait.jpg">""")!!
        assertTrue(result.first)
        assertTrue(result.second.endsWith("portrait.jpg"))
    }
    @Test fun regularAndUnknownPagesAreNotGuessedFromDuration() {
        assertEquals(false, ShortsPresentation.fromHtml(id, """<link rel="canonical" href="https://www.youtube.com/watch?v=$id">""")!!.first)
        assertNull(ShortsPresentation.fromHtml(id, "<title>Consent required</title>"))
        assertNull(ShortsPresentation.fromHtml(id, """<link rel="canonical" href="https://www.youtube.com/shorts/zzzzzzzzzzz">"""))
    }
    @Test fun unsafeCanonicalAndArtworkAreRejected() {
        assertNull(ShortsPresentation.fromHtml(id, """<link rel="canonical" href="https://evil.example/shorts/$id">"""))
        assertEquals("", ShortsPresentation.safeArtwork("https://i.ytimg.com.evil.example/image.jpg"))
        assertEquals("", ShortsPresentation.safeArtwork("https://user@i.ytimg.com/image.jpg"))
        assertEquals("", ShortsPresentation.safeArtwork("http://yt3.ggpht.com/image.jpg"))
    }
    @Test fun channelArtworkSurvivesCollectionParsing() {
        val page = YouTubeAccountLibraryParser.parse(Json.parseToJsonElement("""{"channelRenderer":{"channelId":"UCabcdefghijklmnopqrstuv","title":{"simpleText":"Creator"},"thumbnail":{"thumbnails":[{"url":"https://yt3.ggpht.com/avatar"}]}}}"""), AccountEntryKind.CHANNEL)
        assertEquals("https://yt3.ggpht.com/avatar", page.items.single().artwork)
    }
    @Test fun modernChannelArtworkSurvivesCollectionParsing() {
        val page = YouTubeAccountLibraryParser.parse(Json.parseToJsonElement("""{"lockupViewModel":{"contentType":"LOCKUP_CONTENT_TYPE_CHANNEL","contentId":"UCabcdefghijklmnopqrstuv","metadata":{"lockupMetadataViewModel":{"title":{"content":"Creator"}}},"contentImage":{"avatarViewModel":{"image":{"sources":[{"url":"https://yt3.googleusercontent.com/avatar"}]}}}}}"""), AccountEntryKind.CHANNEL)
        assertEquals("https://yt3.googleusercontent.com/avatar", page.items.single().artwork)
    }
}
