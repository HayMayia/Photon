package com.example

import com.example.data.PlaybackProgress
import com.example.data.YouTubeMediaItem
import com.example.data.formatDuration
import com.example.data.parseDurationToSeconds
import org.junit.Assert.*
import org.junit.Test

class PhotonModelsTest {
    @Test fun durationFormattingAndParsingRoundTrip() {
        for (seconds in listOf(0, 1, 59, 60, 3599, 3600, 86399)) {
            assertEquals(seconds, parseDurationToSeconds(formatDuration(seconds)))
        }
        assertEquals("0:00", formatDuration(-1))
        assertEquals(0, parseDurationToSeconds("LIVE"))
    }
    @Test fun progressIsBoundedAndHandlesUnknownDuration() {
        assertEquals(0f, PlaybackProgress(10, 0).fraction, 0f)
        assertEquals(0f, PlaybackProgress(-10, 100).fraction, 0f)
        assertEquals(1f, PlaybackProgress(200, 100).fraction, 0f)
        assertEquals(0.5f, PlaybackProgress(50, 100).fraction, 0f)
    }
    @Test fun thumbnailFallbackUsesTheActualVideoId() {
        val item = YouTubeMediaItem(videoId = "abcdefghijk", title = "Title", channelName = "Creator", thumbnailUrl = "")
        assertEquals("https://i.ytimg.com/vi/abcdefghijk/hqdefault.jpg", item.highResThumbnailUrl)
        assertEquals("Creator", item.metadataLine)
    }
}
