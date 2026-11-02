package com.prism.music.photon

import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20.*
import android.opengl.GLSurfaceView
import android.view.Surface
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/** One decoder surface, drawn into both eyes. This is a flat 2D viewer, not invented stereo/360. */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
internal fun VrHeadsetDialog(player: MediaController?, close: () -> Unit) {
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("VR viewer · 2D", Modifier.padding(12.dp), color = Color.White)
                TextButton(onClick = close) { Text("Exit VR") }
            }
            if (player != null) AndroidView(factory = { context -> StereoVideoView(context, player) }, modifier = Modifier.weight(1f).fillMaxWidth(), onRelease = { it.release() })
            else Box(Modifier.weight(1f).fillMaxWidth()) { Text("Connecting player…", color = Color.White) }
            Text("Rotate your phone for a headset. Same original image in both eyes; no 3D/360 conversion.", Modifier.padding(12.dp), color = Color.White)
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
private class StereoVideoView(context: android.content.Context, private val player: MediaController?) : GLSurfaceView(context), GLSurfaceView.Renderer {
    @Volatile private var active = true
    @Volatile private var aspect = player?.videoSize?.let { if (it.height > 0) it.width.toFloat() * it.pixelWidthHeightRatio / it.height else 16f / 9f } ?: 16f / 9f
    private var texture: SurfaceTexture? = null
    private var surface: Surface? = null
    private var program = 0
    private var textureId = 0
    private var widthPixels = 1
    private var heightPixels = 1
    private val matrix = FloatArray(16)
    private val positions = ByteBuffer.allocateDirect(8 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
    private val uv = ByteBuffer.allocateDirect(8 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(floatArrayOf(0f, 1f, 1f, 1f, 0f, 0f, 1f, 0f)); position(0) }
    private val listener = object : androidx.media3.common.Player.Listener {
        override fun onVideoSizeChanged(size: androidx.media3.common.VideoSize) { if (size.height > 0) aspect = size.width.toFloat() * size.pixelWidthHeightRatio / size.height; requestRender() }
    }
    init { keepScreenOn = true; setEGLContextClientVersion(2); setRenderer(this); renderMode = RENDERMODE_WHEN_DIRTY; player?.addListener(listener) }
    private fun shader(type: Int, source: String): Int = glCreateShader(type).also { glShaderSource(it, source); glCompileShader(it) }
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        surface?.release(); texture?.release()
        program = glCreateProgram()
        glAttachShader(program, shader(GL_VERTEX_SHADER, "attribute vec2 p;attribute vec2 uv;uniform mat4 m;varying vec2 t;void main(){gl_Position=vec4(p,0.,1.);t=(m*vec4(uv,0.,1.)).xy;}"))
        glAttachShader(program, shader(GL_FRAGMENT_SHADER, "#extension GL_OES_EGL_image_external : require\nprecision mediump float;uniform samplerExternalOES image;varying vec2 t;void main(){gl_FragColor=texture2D(image,t);}"))
        glLinkProgram(program)
        val ids = IntArray(1); glGenTextures(1, ids, 0); textureId = ids[0]
        glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
        glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
        glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
        glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
        texture = SurfaceTexture(textureId).also { it.setOnFrameAvailableListener { if (active) requestRender() } }
        val output = Surface(texture); surface = output
        post { if (active) player?.setVideoSurface(output) }
    }
    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) { widthPixels = width; heightPixels = height }
    override fun onDrawFrame(gl: GL10?) {
        glClearColor(0f, 0f, 0f, 1f); glClear(GL_COLOR_BUFFER_BIT)
        if (!active) return
        texture?.updateTexImage(); texture?.getTransformMatrix(matrix)
        glUseProgram(program); glActiveTexture(GL_TEXTURE0); glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        glUniform1i(glGetUniformLocation(program, "image"), 0); glUniformMatrix4fv(glGetUniformLocation(program, "m"), 1, false, matrix, 0)
        val eyeWidth = widthPixels / 2
        val target = eyeWidth.toFloat() / heightPixels.coerceAtLeast(1)
        val sx = if (aspect < target) aspect / target else 1f
        val sy = if (aspect > target) target / aspect else 1f
        positions.clear(); positions.put(floatArrayOf(-sx, -sy, sx, -sy, -sx, sy, sx, sy)); positions.position(0)
        val p = glGetAttribLocation(program, "p"); val t = glGetAttribLocation(program, "uv")
        glEnableVertexAttribArray(p); glEnableVertexAttribArray(t)
        glVertexAttribPointer(p, 2, GL_FLOAT, false, 0, positions); glVertexAttribPointer(t, 2, GL_FLOAT, false, 0, uv)
        for (eye in 0..1) { glViewport(eye * eyeWidth, 0, eyeWidth, heightPixels); glDrawArrays(GL_TRIANGLE_STRIP, 0, 4) }
    }
    fun release() {
        active = false; player?.removeListener(listener)
        surface?.let { player?.clearVideoSurface(it) }
        queueEvent { surface?.release(); texture?.release(); glDeleteProgram(program); glDeleteTextures(1, intArrayOf(textureId), 0) }
        onPause()
    }
}
