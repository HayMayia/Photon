package com.prism.music.photon

import android.os.Build
import com.prism.music.BuildConfig

/** Local, bounded, URL-free playback events. Never records credentials, cookies or stream URLs. */
object PhotonDiagnostics {
    private val events = ArrayDeque<String>()
    @Synchronized fun event(message: String) {
        events.addLast("${System.currentTimeMillis()}: $message")
        while (events.size > 30) events.removeFirst()
    }
    @Synchronized fun report(): String = buildString {
        appendLine("Photon device test report")
        appendLine("App: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Android API: ${Build.VERSION.SDK_INT}")
        appendLine("Test case: ")
        appendLine("Expected: ")
        appendLine("Actual: ")
        appendLine("Reproduction steps: ")
        appendLine("Playback events (this app process only):")
        events.forEach { appendLine(it) }
        appendLine("No account credentials, stream URLs, SSID or device identifiers are included.")
    }
}
