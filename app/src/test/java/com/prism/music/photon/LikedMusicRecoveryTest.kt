package com.prism.music.photon

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class LikedMusicRecoveryTest {
    @Test fun oldSingleSongCacheIsRepairedImmediatelyWithoutDiscardingItsSong() {
        val old = LikedMusicSnapshot(1, listOf(Video("abcdefghijk", "Cached", "Creator")), 1000)
        assertTrue(LikedMusicFreshness.needsRefresh(old, 1001))
        assertEquals(1, old.videos.size)
    }
    @Test fun newestLikesAreAddedWithoutDroppingSongsFromOlderPages() {
        val old = Video("abcdefghijk", "Older song", "Creator", music = true)
        val new = Video("otherVideo1", "New like", "Creator", music = true)
        assertEquals(listOf(new, old), LikedMusicHeadUpdates.merge(listOf(old), listOf(new), listOf(new.id)))
        assertEquals(listOf(new, old), LikedMusicHeadUpdates.merge(listOf(new, old), listOf(new), listOf(new.id)))
    }
    @Test fun incompleteScanIsNotFrozenForSixHoursOrRetriedOnEveryOpen() {
        val partial = LikedMusicSnapshot(1, updatedAt = 1000, schemaVersion = 3, scanComplete = false, unresolved = 8)
        assertFalse(LikedMusicFreshness.needsRefresh(partial, 1001))
        assertTrue(LikedMusicFreshness.needsRefresh(partial, 121000))
        assertFalse(LikedMusicFreshness.needsRefresh(partial.copy(scanComplete = true), 121000))
    }
    @Test fun continuationAfter250EntriesIsNotSilentlyDropped() {
        val rows = (0..299).joinToString(",") { """{"playlistVideoRenderer":{"videoId":"video${it.toString().padStart(6, '0')}","title":{"simpleText":"Song $it"}}}""" }
        val page = YouTubeAccountLibraryParser.parse(Json.parseToJsonElement("""{"contents":[$rows,{"continuationItemRenderer":{"continuationEndpoint":{"continuationCommand":{"token":"next-page"}}}}]}"""), AccountEntryKind.VIDEO)
        assertEquals(300, page.items.size)
        assertEquals("next-page", page.continuation)
    }
    @Test fun musicConfigurationIsProofOnlyForItsExactVideo() {
        val node = Json.parseToJsonElement("""{"watchEndpoint":{"videoId":"abcdefghijk","watchEndpointMusicSupportedConfigs":{"watchEndpointMusicConfig":{"musicVideoType":"MUSIC_VIDEO_TYPE_ATV"}}}}""")
        assertEquals(true, YouTubeMusicEvidence.fromRenderer(node, "abcdefghijk"))
        assertNull(YouTubeMusicEvidence.fromRenderer(node, "otherVideo1"))
    }
    @Test fun musicRowsAreNotLostOrInferredFromTitles() {
        val rows = Json.parseToJsonElement("""{"musicResponsiveListItemRenderer":{"playlistItemData":{"videoId":"abcdefghijk"},"flexColumns":[{"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Song"}]}}}],"navigationEndpoint":{"watchEndpoint":{"videoId":"abcdefghijk","watchEndpointMusicSupportedConfigs":{"watchEndpointMusicConfig":{"musicVideoType":"MUSIC_VIDEO_TYPE_ATV"}}}}}}""")
        val page = YouTubeAccountLibraryParser.parse(rows, AccountEntryKind.VIDEO)
        assertEquals(1, page.items.size)
        assertTrue(page.items.single().video!!.music)
        assertEquals(0, page.unsupportedRows)
    }
    @Test fun unrecognizedVideoIsReportedRatherThanCountedAsACompleteEmptyPage() {
        val page = YouTubeAccountLibraryParser.parse(Json.parseToJsonElement("""{"playlistVideoRenderer":{"videoId":"abcdefghijk"}}"""), AccountEntryKind.VIDEO)
        assertEquals(1, page.unsupportedRows)
    }
}
