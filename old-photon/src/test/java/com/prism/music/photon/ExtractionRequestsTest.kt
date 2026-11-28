package com.prism.music.photon
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
class ExtractionRequestsTest {
    @Test fun cancelingASelectionCancelsItsSocketsButNotOtherCalls() = runBlocking {
        val client = OkHttpClient()
        val stale = client.newCall(Request.Builder().url("https://example.com/stale").build())
        val other = client.newCall(Request.Builder().url("https://example.com/other").build())
        val registered = CountDownLatch(1); val finish = CountDownLatch(1)
        val job = launch(Dispatchers.Default) {
            ExtractionRequests.run {
                ExtractionRequests.track(stale).use { registered.countDown(); finish.await(5, TimeUnit.SECONDS) }
            }
        }
        assertTrue(registered.await(5, TimeUnit.SECONDS))
        job.cancelAndJoin()
        assertTrue(stale.isCanceled()); assertFalse(other.isCanceled())
        finish.countDown()
    }
}
