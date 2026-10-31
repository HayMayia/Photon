package com.prism.music.photon

/** Small process-only cache: signed stream/caption URLs are never saved to disk. */
internal class ExpiringVideoCache<T>(private val capacity: Int, private val ttlMs: Long,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 }) {
    init { require(capacity > 0 && ttlMs > 0) }
    private data class Entry<T>(val value: T, val at: Long)
    private val entries = LinkedHashMap<String, Entry<T>>(capacity, 0.75f, true)
    @Synchronized fun get(id: String): T? {
        val entry = entries[id] ?: return null
        if (clock() - entry.at >= ttlMs) { entries.remove(id); return null }
        return entry.value
    }
    @Synchronized fun put(id: String, value: T) {
        entries[id] = Entry(value, clock())
        while (entries.size > capacity) entries.remove(entries.keys.first())
    }
    @Synchronized fun remove(id: String) { entries.remove(id) }
}

internal object VideoSeek {
    fun target(position: Long, duration: Long, forward: Boolean): Long {
        val start = position.coerceIn(0, Long.MAX_VALUE - 10_000)
        val next = (start + if (forward) 10_000 else -10_000).coerceAtLeast(0)
        return if (duration >= 0) next.coerceAtMost(duration) else next
    }
}
