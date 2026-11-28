package com.prism.music.photon

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import java.io.Closeable
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** NewPipe is synchronous: coroutine cancellation alone does not stop its HTTP sockets. */
internal object ExtractionRequests {
    private class Scope {
        private val calls = mutableSetOf<Call>()
        private var cancelled = false
        @Synchronized fun track(call: Call): Closeable {
            if (cancelled) call.cancel() else calls.add(call)
            return Closeable { synchronized(this) { calls.remove(call) } }
        }
        @Synchronized fun cancel() { cancelled = true; calls.forEach { it.cancel() }; calls.clear() }
    }
    private val current = ThreadLocal<Scope?>()
    fun track(call: Call): Closeable = current.get()?.track(call) ?: Closeable {}
    suspend fun <T> run(block: () -> T): T = suspendCancellableCoroutine { continuation ->
        val scope = Scope()
        continuation.invokeOnCancellation { scope.cancel() }
        Dispatchers.IO.dispatch(continuation.context, Runnable {
            current.set(scope)
            try {
                if (continuation.isActive) {
                    val value = block()
                    if (continuation.isActive) continuation.resume(value)
                }
            } catch (error: Exception) {
                if (continuation.isActive) continuation.resumeWithException(error)
            } finally { current.remove() }
        })
    }
}
