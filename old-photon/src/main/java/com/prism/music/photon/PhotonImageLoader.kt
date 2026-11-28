package com.prism.music.photon

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okio.Path.Companion.toPath
import java.io.File
import java.util.concurrent.TimeUnit

/** Public artwork transport is separate from account HTML, extraction and media; no account cookies. */
internal object PhotonImageLoader {
    fun create(context: PlatformContext): ImageLoader {
        val manager = context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val lowRam = manager.isLowRamDevice || manager.memoryClass <= 128
        val memoryBytes = minOf(if (lowRam) 16L else 48L, manager.memoryClass.toLong() / 8).coerceAtLeast(4) * 1024 * 1024
        val dispatcher = Dispatcher().apply { maxRequests = if (lowRam) 4 else 12; maxRequestsPerHost = if (lowRam) 2 else 6 }
        val client = OkHttpClient.Builder().dispatcher(dispatcher)
            .connectionPool(ConnectionPool(8, 5, TimeUnit.MINUTES))
            .connectTimeout(4, TimeUnit.SECONDS).readTimeout(6, TimeUnit.SECONDS).callTimeout(8, TimeUnit.SECONDS)
            .cookieJar(okhttp3.CookieJar.NO_COOKIES).build()
        return ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { client })) }
            .memoryCache { MemoryCache.Builder().maxSizeBytes(memoryBytes).build() }
            .diskCache { DiskCache.Builder().directory(File(context.cacheDir, "photon_images").absolutePath.toPath()).maxSizeBytes(160L * 1024 * 1024).build() }
            .build()
    }
}
