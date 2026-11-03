package com.prism.music.photon

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class LikedMusicRowsTest {
    private fun row(id: String, music: Boolean = false) = AccountEntry(id, id, AccountEntryKind.VIDEO, Video(id, id, "Creator", music = music))
    @Test fun existingAndNewLikesUseTheSameReadOnlyGoogleShelf() {
        assertEquals(AccountShelf.LIKED.path, AccountShelf.LIKED_MUSIC.path)
        assertEquals("/playlist?list=LL", AccountShelf.LIKED_MUSIC.path)
    }
    @Test fun musicOnlyAndNoNeedForManualPlaylistMembership() = runBlocking {
        val result = LikedMusicRows.classify(listOf(row("musicVideo1"), row("regularVid1"))) { it.id == "musicVideo1" }
        assertEquals(listOf("musicVideo1"), result.items.map { it.id })
        assertTrue(result.items.single().video!!.music)
        assertEquals(0, result.unresolved)
    }
    @Test fun orderIsPreservedWhenMetadataFinishesOutOfOrder() = runBlocking {
        val result = LikedMusicRows.classify(listOf(row("firstVideo1"), row("secondVideo"))) {
            if (it.id == "firstVideo1") delay(30)
            true
        }
        assertEquals(listOf("firstVideo1", "secondVideo"), result.items.map { it.id })
    }
    @Test fun unsupportedMetadataDoesNotBecomeNonMusicAndDoesNotBlockOtherSongs() = runBlocking {
        val result = LikedMusicRows.classify(listOf(row("failedVideo"), row("knownMusic1", true), row("unknownVid1"))) {
            if (it.id == "failedVideo") error("Unavailable metadata") else null
        }
        assertEquals(listOf("knownMusic1"), result.items.map { it.id })
        assertEquals(2, result.unresolved)
        assertTrue(LikedMusicRows.message(false, result.unresolved, 3)!!.contains("Refresh"))
    }
    @Test fun duplicateGoogleRowsAreNotDuplicated() = runBlocking {
        val result = LikedMusicRows.classify(listOf(row("sameVideo01", true), row("sameVideo01", true))) { error("Already source-proven") }
        assertEquals(1, result.items.size)
    }
    @Test fun metadataConcurrencyIsBounded() = runBlocking {
        val running = AtomicInteger(); val highest = AtomicInteger()
        LikedMusicRows.classify((1..10).map { row("id$it") }) {
            val count = running.incrementAndGet(); highest.updateAndGet { maxOf(it, count) }
            delay(10); running.decrementAndGet(); true
        }
        assertTrue(highest.get() <= 2)
    }
    @Test fun cancellationIsNotReportedAsNonMusic() = runBlocking {
        try {
            LikedMusicRows.classify(listOf(row("cancelVideo"))) { throw CancellationException("Account changed") }
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }
    }
    @Test fun categoryMustBelongToTheRequestedVideo() {
        val player = Json.parseToJsonElement("""{"videoDetails":{"videoId":"abcdefghijk"},"microformat":{"playerMicroformatRenderer":{"category":"Music"}}}""").jsonObject
        assertEquals(true, YouTubeMusicCategory.parse(player, "abcdefghijk"))
        assertNull(YouTubeMusicCategory.parse(player, "otherVideo1"))
    }
    @Test fun noCategoryIsUnknownNotFalse() {
        val missing = Json.parseToJsonElement("""{"videoDetails":{"videoId":"abcdefghijk"}}""").jsonObject
        assertNull(YouTubeMusicCategory.parse(missing, "abcdefghijk"))
        val regular = Json.parseToJsonElement("""{"videoDetails":{"videoId":"abcdefghijk"},"microformat":{"playerMicroformatRenderer":{"category":"Education"}}}""").jsonObject
        assertEquals(false, YouTubeMusicCategory.parse(regular, "abcdefghijk"))
    }
    @Test fun checkingOlderLikesIsNotPresentedAsAnEmptyCompletedList() {
        assertNotNull(LikedMusicRows.message(true, 0, 50))
        assertNull(LikedMusicRows.message(false, 0, 50))
    }
}
