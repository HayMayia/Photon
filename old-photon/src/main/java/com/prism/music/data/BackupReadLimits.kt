package com.prism.music.data

import java.io.InputStream
import java.io.OutputStream

/** Streaming uncompressed-byte budget: ZIP metadata/declared sizes are not trusted. */
internal class BackupReadLimits(private var remaining: Long) {
    private var entries = 0
    init { require(remaining >= 0) { "Not enough free space to restore this backup" } }
    fun nextEntry() { require(++entries <= 100_000) { "Backup contains too many entries" } }
    fun copy(input: InputStream, output: OutputStream, entryLimit: Long = Long.MAX_VALUE) {
        var entryBytes = 0L
        val buffer = ByteArray(8192)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) return
            if (n == 0) continue
            require(n.toLong() <= remaining && n.toLong() <= entryLimit - entryBytes) {
                "Backup is too large or there is not enough free space"
            }
            output.write(buffer, 0, n)
            remaining -= n
            entryBytes += n
        }
    }
}
