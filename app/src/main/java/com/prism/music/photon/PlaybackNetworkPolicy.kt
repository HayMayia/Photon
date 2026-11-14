package com.prism.music.photon

import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** An unread prefetched body can legitimately outlive a metadata deadline or a long pause. */
internal object PlaybackNetworkPolicy {
    fun client(metadata: OkHttpClient): OkHttpClient = metadata.newBuilder()
        .dispatcher(Dispatcher()).connectionPool(ConnectionPool())
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .build()
}
