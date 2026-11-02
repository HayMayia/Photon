package com.prism.music.photon

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class PlaylistContentsTest {
    private fun root(body: String) = Json.parseToJsonElement("""{"contents":$body}""").jsonObject
    @Test fun onlyTheRequestedPlaylistEntriesAreChosenNotPreviewOrSidebar() {
        val root = root("""{"preview":{"videoRenderer":{"videoId":"preview0001"}},"playlistVideoListRenderer":{"playlistId":"PL_requested","contents":[{"playlistVideoRenderer":{"videoId":"abcdefghijk","title":{"simpleText":"Right song"}}}]}}""")
        val content = PlaylistContents.primary(root, "PL_requested")!!
        assertEquals(listOf("abcdefghijk"), YouTubeAccountLibraryParser.parse(content, AccountEntryKind.VIDEO).items.map { it.id })
    }
    @Test fun anotherPlaylistIsRejectedInsteadOfMasqueradingAsTheRequestedOne() {
        try { PlaylistContents.primary(root("""{"playlistVideoListRenderer":{"playlistId":"PL_other","contents":[]}}"""), "PL_requested"); fail("Wrong playlist") }
        catch (_: IOException) { }
    }
    @Test fun previewAloneCannotBecomeAPlaylist() {
        assertNull(PlaylistContents.primary(root("""{"videoRenderer":{"videoId":"abcdefghijk","title":{"simpleText":"Preview only"}}}"""), "PL_requested"))
    }
    @Test fun modernPlaylistVideosNeedAProvenWatchTarget() {
        fun card(target: String) = Json.parseToJsonElement("""{"lockupViewModel":{"contentId":"abcdefghijk","rendererContext":{"commandContext":{"onTap":{"innertubeCommand":{"watchEndpoint":{"videoId":"$target"}}}}},"metadata":{"lockupMetadataViewModel":{"title":{"content":"Modern entry"}}}}}""")
        assertEquals(1, YouTubeAccountLibraryParser.parse(card("abcdefghijk"), AccountEntryKind.VIDEO).items.size)
        assertTrue(YouTubeAccountLibraryParser.parse(card("otherVideo1"), AccountEntryKind.VIDEO).items.isEmpty())
    }
}
