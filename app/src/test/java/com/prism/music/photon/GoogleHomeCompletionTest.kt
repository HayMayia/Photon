package com.prism.music.photon

import org.junit.Assert.*
import org.junit.Test

class GoogleHomeCompletionTest {
    @Test fun confirmedDefaultAccountCompletes() { assertEquals(0, YouTubeSessionNavigation.verifiedIndex("\"0\"")) }
    @Test fun confirmedOtherAccountCompletes() { assertEquals(3, YouTubeSessionNavigation.verifiedIndex("\"3\"")) }
    @Test fun signedOutNullDoesNotComplete() { assertNull(YouTubeSessionNavigation.verifiedIndex("null")) }
    @Test fun missingConfigDoesNotBecomeAccountZero() { assertNull(YouTubeSessionNavigation.verifiedIndex("\"undefined\"")) }
    @Test fun bareNumberIsNotTheExpectedJavascriptProof() { assertNull(YouTubeSessionNavigation.verifiedIndex("0")) }
    @Test fun booleanIsNotAnAccount() { assertNull(YouTubeSessionNavigation.verifiedIndex("true")) }
    @Test fun invalidOrOutOfRangeSelectionDoesNotComplete() {
        for (value in listOf("\"-1\"", "\"10\"", "\"\"", "{}", "[0]", "broken"))
            assertNull(YouTubeSessionNavigation.verifiedIndex(value))
    }
}
