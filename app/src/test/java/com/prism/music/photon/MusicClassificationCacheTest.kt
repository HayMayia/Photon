package com.prism.music.photon

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class MusicClassificationCacheTest {
    private val id = "abcdefghijk"
    @Test fun membershipAndOpaqueMusicFlagDoNotValidateLegacyCachedVideos() {
        val legacy = LikedMusicSnapshot(7, listOf(Video(id, "Music video", "Music channel", music = true)), schemaVersion = 1)
        assertTrue(MusicClassificationPolicy.verified(legacy, 7).isEmpty())
        assertNull(MusicClassificationPolicy.verdict(legacy, 7, id, 100))
    }
    @Test fun negativeClassificationSurvivesRestartAndPreventsAnotherLookup() {
        val state = LikedMusicSnapshot(7, schemaVersion = 2, classifications = mapOf(id to MusicClassification(false, 100)))
        val restored = Json.decodeFromString<LikedMusicSnapshot>(Json.encodeToString(state))
        assertEquals(false, MusicClassificationPolicy.verdict(restored, 7, id, 101))
        assertNull(MusicClassificationPolicy.verdict(restored, 8, id, 101))
        assertNull(MusicClassificationPolicy.verdict(restored, 7, id, 100 + MusicClassificationPolicy.TTL_MS))
    }
    @Test fun rejectedCachedVideosAreRemovedEvenIfTheirOldMusicFlagWasTrue() {
        val state = LikedMusicSnapshot(7, listOf(Video(id, "Ordinary video", "Creator", music = true)), schemaVersion = 2,
            classifications = mapOf(id to MusicClassification(false, 100)))
        assertTrue(MusicClassificationPolicy.verified(state, 7).isEmpty())
        assertEquals(1, MusicClassificationPolicy.verified(state.copy(classifications = mapOf(id to MusicClassification(true, 100))), 7).size)
    }
    @Test fun genericUserGeneratedVideoIsNotMusicProof() {
        val node = Json.parseToJsonElement("""{"watchEndpoint":{"videoId":"$id","watchEndpointMusicSupportedConfigs":{"watchEndpointMusicConfig":{"musicVideoType":"MUSIC_VIDEO_TYPE_UGC"}}}}""")
        assertNull(YouTubeMusicEvidence.fromRenderer(node, id))
    }
}
