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
        val dispatcher = Dispatcher().apply { maxRequests = 16; maxRequestsPerHost = 8 }
        val client = OkHttpClient.Builder().dispatcher(dispatcher)
            .connectionPool(ConnectionPool(8, 5, TimeUnit.MINUTES))
            .connectTimeout(4, TimeUnit.SECONDS).readTimeout(6, TimeUnit.SECONDS).callTimeout(8, TimeUnit.SECONDS)
            .cookieJar(okhttp3.CookieJar.NO_COOKIES).build()
        return ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { client })) }
            .memoryCache { MemoryCache.Builder().maxSizeBytes(64L * 1024 * 1024).build() }
            .diskCache { DiskCache.Builder().directory(File(context.cacheDir, "photon_images").absolutePath.toPath()).maxSizeBytes(160L * 1024 * 1024).build() }
            .build()
    }
}
