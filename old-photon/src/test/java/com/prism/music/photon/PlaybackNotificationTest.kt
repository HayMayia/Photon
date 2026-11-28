package com.prism.music.photon

import android.Manifest
import android.app.NotificationManager
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Real service/notification-manager path: no Activity and no stream/network preparation. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = PhotonApplication::class)
class PlaybackNotificationTest {
    @Test fun restoredPausedItemPostsMediaControlsWithoutOpeningTheActivity() {
        val app = ApplicationProvider.getApplicationContext<PhotonApplication>()
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val lifecycle = Robolectric.buildService(PhotonPlaybackService::class.java).create()
        val service = lifecycle.get()
        try {
            assertEquals(1, service.sessions.size)
            service.sessions.single().player.setMediaItem(playbackItem(
                Video("aaaaaaaaaaa", "Restored song", "Artist", music = true), localPath = "/restored-song.mp3"))
            repeat(20) { shadowOf(Looper.getMainLooper()).idle() }
            val notifications = shadowOf(app.getSystemService(NotificationManager::class.java)).allNotifications
            assertFalse("A paused/restored media item must have a notification without an Activity", notifications.isEmpty())
            assertTrue(notifications.any { it.actions?.isNotEmpty() == true })
            assertTrue(notifications.any { it.smallIcon != null })
            service.onTaskRemoved(android.content.Intent())
            assertFalse("Swiping the task must not discard an allowed paused music session", shadowOf(service).isStoppedBySelf)
        } finally { lifecycle.destroy() }
    }
}
