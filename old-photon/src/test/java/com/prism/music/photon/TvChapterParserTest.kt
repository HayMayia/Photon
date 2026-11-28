package com.prism.music.photon

import org.junit.Assert.*
import org.junit.Test

class TvChapterParserTest {
    @Test fun parsesOnlyRealOrderedCreatorTimestamps() {
        assertEquals(listOf(0L, 83000L, 3600000L), TvChapterParser.parse("0:00 Intro<br>1:23 – Main\n1:00:00 Ending").map { it.positionMs })
    }
    @Test fun rejectsMissingZeroUnorderedAndInvalidTimes() {
        assertTrue(TvChapterParser.parse("1:00 Main\n2:00 End").isEmpty())
        assertTrue(TvChapterParser.parse("0:00 Intro\n2:00 Main\n1:00 End").isEmpty())
        assertTrue(TvChapterParser.parse("0:00 Intro\n1:99 Main").isEmpty())
        assertTrue(TvChapterParser.parse("No chapters").isEmpty())
    }
}
