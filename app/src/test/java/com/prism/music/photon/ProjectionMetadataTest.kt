package com.prism.music.photon

import org.junit.Assert.*
import org.junit.Test

class ProjectionMetadataTest {
    private fun body(type: String = "EQUIRECTANGULAR", layout: String = "STEREO_LAYOUT_MONO") = """{"videoDetails":{"videoId":"abcdefghijk","title":"Actual source"},"streamingData":{"formats":[{"itag":18,"projectionType":"RECTANGULAR"}],"adaptiveFormats":[{"itag":137,"projectionType":"$type","stereoLayout":"$layout"}]}}"""
    @Test fun bindsProjectionToActualStreamInsteadOfWholeVideo() {
        val result = ProjectionMetadata.parse(body())!!
        assertEquals("abcdefghijk", result.first)
        assertEquals(VideoProjection.FLAT, result.second[18])
        assertEquals(VideoProjection.EQUIRECTANGULAR, result.second[137])
    }
    @Test fun preservesStereoLayoutsAndNeverInventsThem() {
        assertEquals(VideoProjection.EQUIRECTANGULAR_TOP_BOTTOM, ProjectionMetadata.parse(body(layout = "STEREO_LAYOUT_TOP_BOTTOM"))!!.second[137])
        assertEquals(VideoProjection.EQUIRECTANGULAR_LEFT_RIGHT, ProjectionMetadata.parse(body(layout = "STEREO_LAYOUT_LEFT_RIGHT"))!!.second[137])
        assertEquals(VideoProjection.UNSUPPORTED, ProjectionMetadata.parse(body(layout = "UNRECOGNIZED"))!!.second[137])
    }
    @Test fun meshIsNotFlattenedOrGuessedAsEquirectangular() {
        assertEquals(VideoProjection.UNSUPPORTED, ProjectionMetadata.parse(body(type = "MESH"))!!.second[137])
    }
    @Test fun titleAndResolutionAreNotSphericalEvidence() {
        val source = """{"videoDetails":{"videoId":"abcdefghijk","title":"360 VR video"},"streamingData":{"formats":[{"itag":18,"width":3840,"height":1920}]}}"""
        assertTrue(ProjectionMetadata.parse(source)!!.second.isEmpty())
    }
    @Test fun rejectsInvalidAndMissingVideoIdentity() {
        assertNull(ProjectionMetadata.parse(body().replace("abcdefghijk", "bad")))
        assertNull(ProjectionMetadata.parse("{}")); assertNull(ProjectionMetadata.parse("invalid"))
    }
    @Test fun observerAcceptsOnlyRealPlayerResponsesAndRetainsProofWhenNextClientOmitsIt() {
        ProjectionMetadata.observe("https://www.youtube.com/youtubei/v1/player", body())
        assertEquals(VideoProjection.EQUIRECTANGULAR, ProjectionMetadata.get("abcdefghijk", 137))
        ProjectionMetadata.observe("https://www.youtube.com/youtubei/v1/player", body(type = "UNKNOWN"))
        assertEquals(VideoProjection.EQUIRECTANGULAR, ProjectionMetadata.get("abcdefghijk", 137))
        ProjectionMetadata.observe("https://evil.example/youtubei/v1/player", body().replace("abcdefghijk", "otherVideo1"))
        assertEquals(VideoProjection.FLAT, ProjectionMetadata.get("otherVideo1", 137))
    }
    @Test fun selectsOnlyNextRealItemAndNeverWrapsOrRepeats() {
        val a = Video("abcdefghijk", "A", "Creator"); val b = Video("otherVideo1", "B", "Creator")
        assertEquals(b, StartupPolicy.next(listOf(a, b), a.id))
        assertNull(StartupPolicy.next(listOf(a, b), b.id))
        assertNull(StartupPolicy.next(listOf(a), "unknown"))
        assertNull(StartupPolicy.next(listOf(a, a), a.id))
    }
    @Test fun startupThresholdDoesNotReduceRebufferProtection() {
        assertEquals(500, StartupPolicy.START_BUFFER_MS)
        assertTrue(StartupPolicy.REBUFFER_MS >= 1500)
    }
}
