package com.prism.music.photon

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.prism.music.BuildConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest

internal data class PhotonRelease(val version: String, val url: String)
internal data class PhotonUpdateState(val release: PhotonRelease? = null, val busy: Boolean = false, val message: String = "", val permission: Boolean = false)
internal object PhotonReleasePolicy {
    fun newer(remote: String, local: String): Boolean {
        fun parts(s: String) = Regex("\\d+").findAll(s).take(3).map { it.value.toIntOrNull() ?: 0 }.toList().let { it + List((3 - it.size).coerceAtLeast(0)) { 0 } }
        val a = parts(remote); val b = parts(local)
        for (i in 0..2) if (a[i] != b[i]) return a[i] > b[i]
        val remoteBeta = remote.contains("beta", true); val localBeta = local.contains("beta", true)
        if (remoteBeta != localBeta) return !remoteBeta && localBeta
        if (remoteBeta) {
            fun number(s: String) = Regex("beta[ ._-]*(\\d+)", RegexOption.IGNORE_CASE).find(s)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            return number(remote) > number(local)
        }
        return false
    }
    fun trusted(url: String): Boolean = runCatching { val u = java.net.URI(url)
        u.scheme == "https" && u.host == "github.com" && u.userInfo == null && u.port == -1 &&
            u.path.startsWith("/HayMayia/Photon/releases/download/") && u.path.endsWith("/Photon.apk")
    }.getOrDefault(false)
}

/** Public GitHub metadata only; APK identity/version/certificate must match before offering installation. */
internal class PhotonUpdates(private val context: Context) {
    val state = MutableStateFlow(PhotonUpdateState())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val http = OkHttpClient.Builder().callTimeout(5, java.util.concurrent.TimeUnit.MINUTES).build()
    private var lastCheck = 0L
    fun check(force: Boolean = false) {
        if (state.value.busy || !force && System.currentTimeMillis() - lastCheck < 6 * 3_600_000L) return
        state.value = state.value.copy(busy = true, message = "Checking…", permission = false)
        scope.launch {
            try {
                val release = withContext(Dispatchers.IO) {
                    http.newCall(Request.Builder().url("https://api.github.com/repos/HayMayia/Photon/releases?per_page=30")
                        .header("Accept", "application/vnd.github+json").header("User-Agent", "Photon/${BuildConfig.VERSION_NAME}").build()).execute().use { response ->
                        check(response.isSuccessful) { "Update server returned ${response.code}" }
                        val releases = Json.parseToJsonElement(response.body.string()).jsonArray
                        releases.mapNotNull { element ->
                            val r = element.jsonObject
                            if (r["draft"]?.jsonPrimitive?.booleanOrNull == true) return@mapNotNull null
                            val version = r["tag_name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                            val asset = r["assets"]?.jsonArray?.firstOrNull { it.jsonObject["name"]?.jsonPrimitive?.content == "Photon.apk" }?.jsonObject
                            val url = asset?.get("browser_download_url")?.jsonPrimitive?.content ?: return@mapNotNull null
                            PhotonRelease(version, url).takeIf { PhotonReleasePolicy.trusted(url) && PhotonReleasePolicy.newer(version, BuildConfig.VERSION_NAME) }
                        }.firstOrNull()
                    }
                }
                lastCheck = System.currentTimeMillis()
                state.value = PhotonUpdateState(release, message = if (release == null) "You're up to date" else "${release.version} available")
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { state.value = state.value.copy(busy = false, message = "Couldn't check for updates. Try again.") }
        }
    }
    fun permissionIntent() = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
    fun install() {
        val release = state.value.release ?: return
        if (state.value.busy) return
        if (!context.packageManager.canRequestPackageInstalls()) {
            state.value = state.value.copy(permission = true, message = "Allow Photon to install updates, then select Update again.")
            return
        }
        state.value = state.value.copy(busy = true, permission = false, message = "Downloading update…")
        scope.launch {
            try {
                val apk = withContext(Dispatchers.IO) {
                    require(PhotonReleasePolicy.trusted(release.url))
                    val directory = File(context.cacheDir, "photon-updates").apply { mkdirs() }
                    val part = File(directory, "Photon.part")
                    try {
                        http.newCall(Request.Builder().url(release.url).build()).execute().use { response ->
                            check(response.isSuccessful && response.request.url.isHttps)
                            response.body.byteStream().use { input -> part.outputStream().use { output ->
                                val buffer = ByteArray(65536); var total = 0L
                                while (true) { val n = input.read(buffer); if (n < 0) break; total += n; check(total <= 160L * 1024 * 1024); output.write(buffer, 0, n) }
                            } }
                        }
                        verify(part)
                        val result = File(directory, "Photon.apk"); if (result.exists()) check(result.delete()); check(part.renameTo(result)); result
                    } finally { part.delete() }
                }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.photon.updates", apk)
                context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
                state.value = state.value.copy(busy = false, message = "Confirm installation in Android.")
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { state.value = state.value.copy(busy = false, message = "Update couldn't be installed. Your current app and data are unchanged.") }
        }
    }
    @Suppress("DEPRECATION")
    private fun verify(apk: File) {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val candidate = pm.getPackageArchiveInfo(apk.path, flags) ?: error("Invalid APK")
        val installed = pm.getPackageInfo(context.packageName, flags)
        require(candidate.packageName == context.packageName)
        fun code(p: android.content.pm.PackageInfo) = if (Build.VERSION.SDK_INT >= 28) p.longVersionCode else p.versionCode.toLong()
        require(code(candidate) > code(installed))
        fun signers(p: android.content.pm.PackageInfo): Set<String> {
            val signatures = if (Build.VERSION.SDK_INT >= 28) p.signingInfo?.apkContentsSigners else p.signatures
            return signatures.orEmpty().map { signature -> MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).joinToString("") { "%02x".format(it) } }.toSet()
        }
        require(signers(candidate).isNotEmpty() && signers(candidate) == signers(installed))
    }
}
