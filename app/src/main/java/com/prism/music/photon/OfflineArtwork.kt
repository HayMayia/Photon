package com.prism.music.photon

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.work.*
import coil3.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File

val LocalDownloadArtwork = staticCompositionLocalOf<Map<String, String>> { emptyMap() }

@Composable
fun VideoThumbnail(video: Video, description: String?, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop) {
    val path = LocalDownloadArtwork.current[video.id]
    AsyncImage(if (path != null) File(path) else video.thumbnail, description, modifier, contentScale = contentScale)
}

@Composable
fun OfflineArtworkProvider(downloads: List<SavedDownload>, content: @Composable () -> Unit) {
    val paths = androidx.compose.runtime.remember(downloads) {
        downloads.mapNotNull { saved -> saved.thumbnailPath?.takeIf { File(it).isFile }?.let { saved.video.id to it } }.toMap()
    }
    CompositionLocalProvider(LocalDownloadArtwork provides paths, content = content)
}

/** Files, not Coil's evictable cache. A missing image never invalidates a successful video download. */
object OfflineArtwork {
    const val MAX_BYTES = 5 * 1024 * 1024
    suspend fun save(app: PhotonApplication, video: Video, target: File): String? = withContext(Dispatchers.IO) {
        if (target.isFile && target.length() > 0) return@withContext target.absolutePath
        if (video.thumbnail.isBlank()) return@withContext null
        val temp = File(target.parentFile, ".${target.name}.${java.util.UUID.randomUUID()}.part")
        try {
            app.videos.artworkHttp.newCall(Request.Builder().url(video.thumbnail).build()).execute().use { response ->
                check(response.isSuccessful) { "Thumbnail HTTP ${response.code}" }
                check(response.body.contentLength() <= MAX_BYTES) { "Thumbnail exceeds size limit" }
                response.body.byteStream().use { input ->
                    temp.outputStream().use { output ->
                        val buffer = ByteArray(16 * 1024)
                        var total = 0
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val size = input.read(buffer)
                            if (size < 0) break
                            total += size
                            check(total <= MAX_BYTES) { "Thumbnail exceeds size limit" }
                            output.write(buffer, 0, size)
                        }
                    }
                }
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(temp.absolutePath, bounds)
            check(bounds.outWidth > 0 && bounds.outHeight > 0) { "Invalid thumbnail image" }
            check(temp.renameTo(target)) { "Could not save thumbnail" }
            target.absolutePath
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { null }
        finally { temp.delete() }
    }
    fun schedule(context: Context, wifiOnly: Boolean) {
        val request = OneTimeWorkRequestBuilder<RepairArtworkWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("photon-offline-artwork", ExistingWorkPolicy.KEEP, request)
    }
}

class RepairArtworkWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val app = applicationContext as PhotonApplication
        var missing = false
        for (download in app.library.state.value.downloads) {
            currentCoroutineContext().ensureActive()
            if (download.thumbnailPath?.let { File(it).isFile } == true) continue
            val videoFile = File(download.path)
            if (!videoFile.isFile) continue
            val path = OfflineArtwork.save(app, download.video, File(videoFile.parentFile, "${videoFile.nameWithoutExtension}.thumbnail"))
            if (path == null) { missing = true; continue }
            var retained = false
            app.library.update { library -> library.copy(downloads = library.downloads.map {
                if (it.path == download.path) { retained = true; it.copy(thumbnailPath = path) } else it
            }) }
            if (!retained) File(path).delete()
        }
        if (missing && runAttemptCount < 2) Result.retry() else Result.success()
    }
}
