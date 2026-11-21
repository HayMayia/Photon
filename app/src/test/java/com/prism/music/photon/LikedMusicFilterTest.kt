package com.prism.music.photon

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class LikedMusicFilterTest {
    private fun entry(id: String, short: Boolean) = AccountEntry(id, id, AccountEntryKind.VIDEO,
        Video(id, id, "Creator", short = short, music = true))
    @Test fun allKeepsEveryNativeSongInSourceOrder() {
        val rows = listOf(entry("abcdefghijk", false), entry("otherVideo1", true))
        assertSame(rows, LikedMusicFilter.ALL.apply(rows))
        assertEquals(listOf(rows[1]), LikedMusicFilter.SHORTS.apply(rows))
        assertEquals(listOf(rows[0]), LikedMusicFilter.FULL_LENGTH.apply(rows))
        assertEquals(2, rows.size)
    }
    @Test fun formatCorrectionWaitsForStableLayoutCommit() {
        val original = entry("abcdefghijk", true).copy(layoutShort = false)
        assertTrue(LikedMusicFilter.SHORTS.apply(listOf(original)).isEmpty())
        val committed = AccountLibraryRows.commitFormats(listOf(original))
        assertEquals(committed, LikedMusicFilter.SHORTS.apply(committed))
    }
    @Test fun nativeMusicRowsRetainExplicitShortsEvidence() {
        val root = Json.parseToJsonElement("""{"musicResponsiveListItemRenderer":{"playlistItemData":{"videoId":"abcdefghijk"},"title":{"simpleText":"Native song"},"navigationEndpoint":{"reelWatchEndpoint":{"videoId":"abcdefghijk"}}}}""")
        val rows = YouTubeAccountLibraryParser.parse(root, AccountEntryKind.VIDEO).items
        assertTrue(rows.single().video!!.short)
        assertEquals(1, LikedMusicFilter.SHORTS.apply(rows).size)
    }
}
