package com.prism.music.photon

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import android.os.Environment
import android.os.StatFs
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.prism.music.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.nio.ByteBuffer

/** Downloads real source streams, then losslessly muxes MP4 + AAC on the device (no FFmpeg binary). */
class VideoDownloadWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    private val app get() = applicationContext as PhotonApplication
    private var lastUpdate = 0L

    override suspend fun getForegroundInfo(): ForegroundInfo = notification(0, "Preparing download")

    private fun notification(percent: Int, message: String): ForegroundInfo {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Video downloads", NotificationManager.IMPORTANCE_LOW))
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(R.drawable.ic_photon_notification)
            .setContentTitle(inputData.getString("title") ?: "Photon download")
            .setContentText(message)
            .setProgress(100, percent, percent == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, "Cancel", WorkManager.getInstance(applicationContext).createCancelPendingIntent(id))
            .build()
        val notificationId = id.hashCode() and Int.MAX_VALUE
        return if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else ForegroundInfo(notificationId, notification)
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val videoId = inputData.getString("video_id") ?: return@withContext Result.failure()
        val requested = inputData.getInt("height", 1080)
        val root = File(applicationContext.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: applicationContext.filesDir, "Photon").apply { mkdirs() }
        val temp = File(root, ".${id}").apply { mkdirs() }
        try {
            setProgress(workDataOf("title" to inputData.getString("title"), "stage" to "Resolving ${requested}p video", "percent" to 0))
            setForeground(notification(0, "Resolving ${requested}p video"))
            val details = app.videos.details(videoId)
            val quality = details.qualities.firstOrNull { it.height == requested && it.downloadable }
                ?: error("${requested}p is no longer available. Open the video and choose another quality.")
            val videoFile = File(temp, "video.mp4")
            fetch(quality.url, videoFile, 0, if (quality.audioUrl != null) 75 else 95)
            val output = File(temp, "complete.mp4")
            if (quality.audioUrl != null) {
                val audioFile = File(temp, "audio.m4a")
                fetch(quality.audioUrl, audioFile, 75, 20)
                setProgress(workDataOf("title" to inputData.getString("title"), "percent" to 97, "stage" to "Merging audio and video"))
                setForeground(notification(97, "Merging audio and video"))
                mux(videoFile, audioFile, output)
            } else {
                check(videoFile.renameTo(output)) { "Could not finalize downloaded video" }
            }
            currentCoroutineContext().ensureActive()
            val target = File(root, "$videoId-${quality.height}p.mp4")
            check(output.length() > 0) { "The download was empty" }
            check(output.renameTo(target)) { "Could not save video; check free storage" }
            app.library.downloaded(SavedDownload(details.video, target.absolutePath, quality.height, target.length()))
            Result.success(workDataOf("path" to target.absolutePath, "height" to quality.height))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(workDataOf("title" to inputData.getString("title"), "error" to (error.message ?: "Download failed").take(600)))
        } finally {
            temp.deleteRecursively()
        }
    }

    private suspend fun fetch(url: String, target: File, base: Int, share: Int) {
        app.videos.streamHttp.newCall(Request.Builder().url(url).build()).execute().use { response ->
            check(response.isSuccessful) { "YouTube refused the download (HTTP ${response.code}). Try opening the video again." }
            val length = response.body.contentLength()
            val available = StatFs(target.parentFile!!.absolutePath).availableBytes
            // Adaptive muxing needs temporary input AND output storage at once.
            check(length <= 0 || available > length * 2 + 32L * 1024 * 1024) { "Not enough free storage for this quality" }
            response.body.byteStream().use { input ->
                target.outputStream().buffered().use { output ->
                    val buffer = ByteArray(128 * 1024)
                    var bytes = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        bytes += count
                        val now = System.currentTimeMillis()
                        if (now - lastUpdate > 750) {
                            val percent = base + if (length > 0) (bytes * share / length).toInt().coerceIn(0, share) else 0
                            val stage = "${bytes / 1024 / 1024} MB downloaded"
                            setProgress(workDataOf("title" to inputData.getString("title"), "percent" to percent, "stage" to stage))
                            setForeground(notification(percent, stage))
                            lastUpdate = now
                        }
                    }
                    check(length <= 0 || bytes == length) { "The connection ended before the download finished" }
                }
            }
        }
    }

    private suspend fun mux(video: File, audio: File, output: File) {
        val extractors = listOf(MediaExtractor(), MediaExtractor())
        var muxer: MediaMuxer? = null
        var started = false
        try {
            extractors[0].setDataSource(video.absolutePath)
            extractors[1].setDataSource(audio.absolutePath)
            val writer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = writer
            val tracks = extractors.mapIndexed { index, extractor ->
                val prefix = if (index == 0) "video/" else "audio/"
                val source = (0 until extractor.trackCount).firstOrNull { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith(prefix) == true }
                    ?: error("The downloaded file has no $prefix track")
                extractor.selectTrack(source)
                val format = extractor.getTrackFormat(source)
                val maxSample = if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) else 0
                writer.addTrack(format) to maxOf(maxSample, 8 * 1024 * 1024)
            }
            writer.start()
            started = true
            for ((index, extractor) in extractors.withIndex()) {
                val (track, capacity) = tracks[index]
                val buffer = ByteBuffer.allocateDirect(capacity)
                val info = MediaCodec.BufferInfo()
                while (true) {
                    currentCoroutineContext().ensureActive()
                    buffer.clear()
                    val size = extractor.readSampleData(buffer, 0)
                    if (size < 0) break
                    check(size <= capacity) { "Video frame exceeds the muxer's buffer" }
                    info.set(0, size, extractor.sampleTime, if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
                    writer.writeSampleData(track, buffer, info)
                    extractor.advance()
                }
            }
            writer.stop()
            started = false
        } finally {
            extractors.forEach { it.release() }
            if (started) runCatching { muxer?.stop() }
            muxer?.release()
        }
    }

    companion object {
        const val TAG = "photon-video-download"
        private const val CHANNEL = "photon-downloads"
        fun enqueue(context: Context, video: Video, height: Int, wifiOnly: Boolean) {
            val request = OneTimeWorkRequestBuilder<VideoDownloadWorker>()
                .setInputData(workDataOf("video_id" to video.id, "title" to video.title, "height" to height))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED).build())
                .addTag(TAG).build()
            WorkManager.getInstance(context).enqueueUniqueWork("photon-${video.id}-$height", ExistingWorkPolicy.KEEP, request)
        }
    }
}
