package com.prism.music.photon

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import com.prism.music.connect.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.OutputStream
import java.net.InetAddress

/** Reuses Prism's LAN-only renderer protocols, not its music container. Keep Photon open while casting. */
class PhotonCast(private val context: Context, private val videos: VideoRepository) {
    val devices = MutableStateFlow<List<Renderer>>(emptyList())
    val status = MutableStateFlow("Not connected")
    val connected = MutableStateFlow<Renderer?>(null)
    val busy = MutableStateFlow(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val resolver = Mutex()
    private val operation = Mutex()
    private val http = OkHttpClient()
    private val server = MediaServer(0)
    private var link: RendererLink? = null
    private var scan: Job? = null
    private var listener: NsdManager.DiscoveryListener? = null
    private var multicast: WifiManager.MulticastLock? = null
    private val nsd = context.getSystemService(NsdManager::class.java)

    private fun found(renderer: Renderer) {
        devices.value = (devices.value.filterNot { it.id == renderer.id } + renderer).sortedBy { it.name }
    }
    fun discover() {
        stopDiscovery()
        devices.value = emptyList()
        runCatching {
            multicast = (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)
                .createMulticastLock("PhotonCast").apply { setReferenceCounted(false); acquire() }
        }
        scan = scope.launch(Dispatchers.IO) {
            val job = currentCoroutineContext()
            SsdpSearch.run(5_000, stop = { !job.isActive }) { message, _ ->
                message.location?.let { location -> Describe.fetch(http, location)?.let { renderer -> scope.launch { found(renderer) } } }
            }
        }
        val discovery = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(type: String) = Unit
            override fun onDiscoveryStopped(type: String) = Unit
            override fun onStartDiscoveryFailed(type: String, code: Int) { status.value = "Device discovery failed ($code). Check local-network permission and Wi-Fi." }
            override fun onStopDiscoveryFailed(type: String, code: Int) = Unit
            override fun onServiceLost(info: NsdServiceInfo) = Unit
            override fun onServiceFound(info: NsdServiceInfo) {
                scope.launch {
                    resolver.withLock {
                        val resolved = withTimeoutOrNull(6_000) {
                            suspendCancellableCoroutine<NsdServiceInfo?> { continuation ->
                                @Suppress("DEPRECATION")
                                runCatching { nsd.resolveService(info, object : NsdManager.ResolveListener {
                                    override fun onResolveFailed(service: NsdServiceInfo, code: Int) { if (continuation.isActive) continuation.resumeWith(Result.success(null)) }
                                    override fun onServiceResolved(service: NsdServiceInfo) { if (continuation.isActive) continuation.resumeWith(Result.success(service)) }
                                }) }.onFailure { if (continuation.isActive) continuation.resumeWith(Result.success(null)) }
                            }
                        } ?: return@withLock
                        @Suppress("DEPRECATION")
                        val address = resolved.host ?: return@withLock
                        if (!Lan.isLocal(address)) return@withLock
                        val text = resolved.attributes.mapValues { it.value?.toString(Charsets.UTF_8).orEmpty() }
                        GoogleCastDevices.renderer(resolved.serviceName, address.hostAddress ?: return@withLock, resolved.port, text)?.let(::found)
                    }
                }
            }
        }
        runCatching { nsd.discoverServices(GoogleCastDevices.SERVICE, NsdManager.PROTOCOL_DNS_SD, discovery); listener = discovery }
            .onFailure { status.value = it.message ?: "Device discovery unavailable" }
    }
    fun stopDiscovery() {
        scan?.cancel(); scan = null
        listener?.let { runCatching { nsd.stopServiceDiscovery(it) } }; listener = null
        multicast?.let { runCatching { if (it.isHeld) it.release() } }; multicast = null
    }

    suspend fun cast(renderer: Renderer, watch: WatchState): Boolean = operation.withLock {
        busy.value = true
        status.value = "Connecting to ${renderer.name}…"
        var candidate: RendererLink? = null
        try {
            val video = watch.video ?: error("Open a video first")
            val host = renderer.host ?: error("Device has no address")
            check(withContext(Dispatchers.IO) { Lan.isLocal(InetAddress.getByName(host)) }) { "Casting is limited to your local network" }
            link?.let { it.stop(); it.close() }; link = null; connected.value = null
            val source = if (watch.localPath != null) {
                val file = File(watch.localPath)
                check(file.isFile) { "Downloaded file is missing" }
                FileBody("video/mp4", file.length()) { offset -> file.inputStream().apply { channel.position(offset) } }
            } else {
                val details = videos.details(video.id)
                check(!details.live) { "Live casting is not supported yet" }
                val quality = details.qualities.firstOrNull { it.audioUrl == null && it.mime == "video/mp4" }
                    ?: error("No combined MP4 stream is available. Download first, then cast the merged offline copy.")
                networkBody(quality.url)
            }
            check(withContext(Dispatchers.IO) { server.start() }) { "Could not open the local media server" }
            server.newSession()
            val address = withContext(Dispatchers.IO) { RendererControl.localAddressFor(host) } ?: error("No local route to this device")
            val url = server.publish("${video.id}.mp4", source, address)
            val target: RendererLink = if (renderer.kind == RendererKind.GOOGLE_CAST) GoogleCastLink(renderer) else RendererControl(http, renderer)
            candidate = target
            val metadata = Didl.song(video.id, video.title, video.channel, null, video.thumbnail, url, ProtocolInfo.of("video/mp4", false), video.duration * 1000, source.length)
                .replace("object.item.audioItem.musicTrack", "object.item.videoItem")
            target.load(url, metadata, CastMedia(video.title, video.channel, null, video.thumbnail, "video/mp4", video.duration * 1000))
            target.play()
            link = target; connected.value = renderer
            status.value = "Casting to ${renderer.name}"
            true
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) { runCatching { candidate?.close() }; server.stop() }
            throw cancelled
        } catch (error: Exception) {
            runCatching { candidate?.close() }; server.stop()
            status.value = error.message ?: "Casting failed"
            false
        } finally { busy.value = false }
    }

    private suspend fun networkBody(url: String): CastBody = withContext(Dispatchers.IO) {
        val contentLength = videos.streamHttp.newCall(Request.Builder().url(url).build()).execute().use { response ->
            check(response.isSuccessful) { "YouTube refused the casting source" }
            response.body.contentLength()
        }
        check(contentLength > 0) { "The source length is unknown. Download it first to cast." }
        object : CastBody {
            override val mime = "video/mp4"
            override val length = contentLength
            override val converted = false
            override fun write(start: Long, end: Long, out: OutputStream) {
                videos.streamHttp.newCall(Request.Builder().url(url).header("Range", "bytes=$start-$end").build()).execute().use { response ->
                    check(response.isSuccessful) { "Casting source returned HTTP ${response.code}" }
                    response.body.byteStream().use { input ->
                        val buffer = ByteArray(64 * 1024)
                        var remaining = end - start + 1
                        while (remaining > 0) {
                            val size = input.read(buffer, 0, minOf(remaining, buffer.size.toLong()).toInt())
                            check(size > 0) { "Casting stream ended early" }
                            out.write(buffer, 0, size); remaining -= size
                        }
                    }
                }
            }
        }
    }
    fun pause() = command { it.pause() }
    fun play() = command { it.play() }
    fun stop() = command { target -> try { target.stop() } finally { target.close(); link = null; connected.value = null; server.stop(); status.value = "Not connected" } }
    private fun command(action: suspend (RendererLink) -> Unit) {
        scope.launch { operation.withLock { try { link?.let { action(it) } } catch (error: Exception) { status.value = error.message ?: "Device did not respond" } } }
    }
}
