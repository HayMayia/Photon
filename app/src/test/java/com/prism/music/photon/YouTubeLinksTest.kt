package com.prism.music.photon

import android.app.Application
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class YouTubeLinksTest {
    private val id = "dQw4w9WgXcQ"
    @Test fun watchLinkWithTimestamp() { assertEquals(id, YouTubeLinks.videoId("https://www.youtube.com/watch?v=$id&t=42")) }
    @Test fun shortenedLink() { assertEquals(id, YouTubeLinks.videoId("https://youtu.be/$id?si=abc")) }
    @Test fun shortsLink() { assertEquals(id, YouTubeLinks.videoId("https://youtube.com/shorts/$id")) }
    @Test fun liveLink() { assertEquals(id, YouTubeLinks.videoId("https://m.youtube.com/live/$id")) }
    @Test fun sharedSentence() { assertEquals(id, YouTubeLinks.videoId("Watch this: https://youtu.be/$id")) }
    @Test fun noUntrustedHost() { assertNull(YouTubeLinks.videoId("https://youtube.com.evil.example/watch?v=$id")) }
    @Test fun noUserInfoTrick() { assertNull(YouTubeLinks.videoId("https://youtube.com@evil.example/watch?v=$id")) }
    @Test fun noArbitraryDownloadUrl() { assertNull(YouTubeLinks.videoId("https://example.com/video.mp4")) }
    @Test fun noPlaylistAsVideo() { assertNull(YouTubeLinks.videoId("https://youtube.com/playlist?list=PL123")) }
    @Test fun noInvalidId() { assertNull(YouTubeLinks.videoId("https://youtube.com/watch?v=../../etc/passwd")) }
    @Test fun noBlankOrSearchPhrase() { assertNull(YouTubeLinks.videoId("")); assertNull(YouTubeLinks.videoId("science and technology")) }
}
