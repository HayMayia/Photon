package com.prism.music.photon

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PlaybackQueuePersistenceTest {
    @Test fun recreatingTheQueueStoreKeepsLikedMusicIdentityAndPosition() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        context.getSharedPreferences("photon_transport_queue", 0).edit().clear().commit()
        val a = Video("aaaaaaaaaaa", "A", "Artist", music = true)
        val b = Video("bbbbbbbbbbb", "B", "Artist", music = true)
        val queue = PlaybackQueue(context)
        queue.select(a, listOf(Video("randomvideo", "Random", "Other")), listOf(a,b), "account:3:liked-music", 3, null)
        val restored = PlaybackQueue(context).state.value
        assertEquals("account:3:liked-music", restored.source)
        assertEquals(a.id, restored.currentId)
        assertEquals(b, restored.next())
        assertEquals(listOf(b), restored.upcoming())
        assertTrue(restored.related.isEmpty())
    }
}
