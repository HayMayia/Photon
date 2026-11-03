package com.prism.music.photon

import org.junit.Assert.*
import org.junit.Test

class AccountGridPolicyTest {
    @Test fun confirmedShortsShareATwoColumnRow() {
        assertEquals(1, AccountGridPolicy.span(true, true))
        assertEquals(2, AccountGridPolicy.span(true, false))
        assertEquals(2, AccountGridPolicy.span(false, false))
        assertEquals(2, AccountGridPolicy.span(false, true))
    }
    @Test fun lateShortsMetadataChangesSpanWithoutChangingIdentity() {
        val source = Video("abcdefghijk", "A long title which wraps under its own thumbnail", "Creator", "")
        val resolved = ShortsPresentation.merge(source, source.copy(short = true))
        assertEquals(source.id, resolved.id)
        assertEquals(2, AccountGridPolicy.span(true, source.short))
        assertEquals(1, AccountGridPolicy.span(true, resolved.short))
    }
    @Test fun updateBannerVersionUsesReadableBetaName() {
        assertEquals("v1.0.1 Beta", PhotonReleasePolicy.displayVersion("v1.0.1-beta"))
        assertTrue(PhotonReleasePolicy.newer("v1.0.1-beta", "1.0.0 Beta"))
        assertFalse(PhotonReleasePolicy.newer("v1.0.1-beta", "1.0.1 Beta"))
    }
}
