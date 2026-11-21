package com.prism.music.photon

import com.prism.music.data.stream.YouTubeStreamInterceptor
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class PlaybackNetworkPolicyTest {
    @Test fun mediaDoesNotInheritMetadataDeadlineDispatcherOrPool() {
        val metadata = OkHttpClient.Builder().callTimeout(35, TimeUnit.SECONDS).build()
        val media = PlaybackNetworkPolicy.client(metadata)
        assertEquals(35_000, metadata.callTimeoutMillis)
        assertEquals(0, media.callTimeoutMillis)
        assertEquals(35_000, media.readTimeoutMillis)
        assertEquals(10_000, media.connectTimeoutMillis)
        assertNotSame(metadata.dispatcher, media.dispatcher)
        assertNotSame(metadata.connectionPool, media.connectionPool)
    }
    @Test fun unreadMediaResponseSurvivesPastMetadataDeadline() {
        val releaseBody = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val task = executor.submit {
            server.accept().use { socket ->
                socket.soTimeout = 4_000
                val input = socket.getInputStream().bufferedReader()
                while (!input.readLine().isNullOrEmpty()) { }
                val output = socket.getOutputStream()
                output.write("HTTP/1.1 200 OK\r\nContent-Length: 4\r\nConnection: close\r\n\r\n".toByteArray()); output.flush()
                check(releaseBody.await(5, TimeUnit.SECONDS))
                output.write("data".toByteArray()); output.flush()
            }
        }
        val metadata = OkHttpClient.Builder().callTimeout(250, TimeUnit.MILLISECONDS).build()
        val media = PlaybackNetworkPolicy.client(metadata)
        try {
            media.newCall(Request.Builder().url("http://127.0.0.1:${server.localPort}/media").build()).execute().use { response ->
                // Models a prefetched response waiting behind the player's existing buffer.
                Thread.sleep(500)
                releaseBody.countDown()
                assertEquals("data", response.body.string())
            }
            task.get(5, TimeUnit.SECONDS)
        } finally {
            releaseBody.countDown(); server.close(); executor.shutdownNow()
            media.connectionPool.evictAll(); media.dispatcher.executorService.shutdownNow()
        }
    }
    private fun chunkClient(truncate: Boolean = false, requests: AtomicInteger = AtomicInteger()): OkHttpClient {
        val fake = Interceptor { chain ->
            requests.incrementAndGet()
            val range = chain.request().url.queryParameter("range")!!.split('-')
            val from = range[0].toInt(); val end = range[1].toInt()
            val text = "ABCDEFGHIJKL".substring(from, end + 1).let { if (truncate && from == 0) it.take(1) else it }
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(text.toResponseBody()).build()
        }
        val chunks = PlaybackNetworkPolicy.client(OkHttpClient()).newBuilder().addInterceptor(fake).build()
        // Install transport before the deterministic terminal interceptor; no real CDN requests.
        return chunks.newBuilder().apply { interceptors().clear(); addInterceptor(YouTubeStreamInterceptor(chunks, 4)); addInterceptor(fake) }.build()
    }
    @Test fun rangedChunksJoinExactlyOnceWithoutMissingOrRepeatingBytes() {
        val requests = AtomicInteger()
        val client = chunkClient(requests = requests)
        try {
            client.newCall(Request.Builder().url("https://rr1.googlevideo.com/videoplayback?clen=12&c=ANDROID").build()).execute().use {
                assertEquals("ABCDEFGHIJKL", it.body.string())
            }
            assertEquals(3, requests.get())
        } finally { client.dispatcher.executorService.shutdownNow(); client.connectionPool.evictAll() }
    }
    @Test fun earlyChunkEofFailsInsteadOfRefetchingTheSameBytesForever() {
        val client = chunkClient(truncate = true)
        try {
            client.newCall(Request.Builder().url("https://rr1.googlevideo.com/videoplayback?clen=12&c=ANDROID").build()).execute().use { response ->
                assertThrows(java.io.EOFException::class.java) { response.body.string() }
            }
        } finally { client.dispatcher.executorService.shutdownNow(); client.connectionPool.evictAll() }
    }
}
