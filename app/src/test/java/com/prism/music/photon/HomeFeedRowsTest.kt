package com.prism.music.photon

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class HomeFeedRowsTest {
    private fun video(id: String, short: Boolean = false) = Video(id, id, "Creator", short = short)
    @Test fun shortsPairWithoutMovingRecommendationsToTheTop() {
        val input = listOf(video("regular0001"), video("short000001", true), video("short000002", true), video("regular0002"), video("short000003", true))
        val rows = HomeFeedRows.make(input)
        assertEquals(input, rows.flatMap { it.videos })
        assertEquals(listOf(1, 2, 1, 1), rows.map { it.videos.size })
        assertEquals(listOf(false, true, false, true), rows.map { it.portrait })
    }
    @Test fun normalVideosAreNeverForcedIntoPortraitOrPairedWithShorts() {
        val rows = HomeFeedRows.make(listOf(video("short000001", true), video("regular0001"), video("short000002", true)))
        assertEquals(3, rows.size)
        assertEquals(1, rows[1].videos.size)
        assertFalse(rows[1].portrait)
    }
    @Test fun duplicateCardsHaveUniqueStableRowKeys() {
        val short = video("short000001", true)
        val rows = HomeFeedRows.make(listOf(short, short, video("short000002", true), video("regular0001")))
        assertEquals(3, rows.flatMap { it.videos }.size)
        assertEquals(rows.size, rows.map { it.key }.distinct().size)
    }
    @Test fun sidebarMenusAdsAndUnselectedTabsCannotBecomeHomeRecommendations() {
        fun card(id: String) = """{"videoRenderer":{"videoId":"$id","title":{"simpleText":"Server card"}}}"""
        val primary = Json.parseToJsonElement("""{"contents":[${card("abcdefghijk")}],"menu":${card("menuVideo01")},"sidebar":${card("sidebar0001")},"adSlotRenderer":${card("advert00001")},"tabs":[{"selected":false,"content":${card("otherTab001")}}]}""")
        assertEquals(listOf("abcdefghijk"), YouTubeHomeParser.cards(primary).videos.map { it.id })
    }
    @Test fun playlistLockupWithVideoLengthIdCannotMasqueradeAsARecommendation() {
        val primary = Json.parseToJsonElement("""{"lockupViewModel":{"contentId":"abcdefghijk","contentType":"LOCKUP_CONTENT_TYPE_PLAYLIST","metadata":{"lockupMetadataViewModel":{"title":{"content":"Playlist not video"}}}}}""")
        assertTrue(YouTubeHomeParser.cards(primary).videos.isEmpty())
    }
    @Test fun cardMenuCannotSmuggleInOtherVideos() {
        val primary = Json.parseToJsonElement("""{"videoRenderer":{"videoId":"abcdefghijk","title":{"simpleText":"Real card"},"menu":{"videoRenderer":{"videoId":"menuVideo01","title":{"simpleText":"Not a feed card"}}}}}""")
        assertEquals(listOf("abcdefghijk"), YouTubeHomeParser.cards(primary).videos.map { it.id })
    }
}
