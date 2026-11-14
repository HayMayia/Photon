package com.prism.music.photon

import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20.*
import android.opengl.GLSurfaceView
import android.view.Surface
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
internal fun VrHeadsetDialog(player: MediaController?, projection: VideoProjection = VideoProjection.FLAT, close: () -> Unit) {
    val context = LocalContext.current
    val activity = generateSequence(context) { (it as? android.content.ContextWrapper)?.baseContext }
        .filterIsInstance<android.app.Activity>().firstOrNull()
    DisposableEffect(activity) {
        val before = activity?.requestedOrientation
        if (activity != null && !context.isPhotonTv()) activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose { if (activity != null && before != null && !context.isPhotonTv()) activity.requestedOrientation = before }
    }
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (projection == VideoProjection.FLAT) "VR viewer · 2D" else "VR viewer · 360°", Modifier.padding(12.dp), color = Color.White)
                TextButton(onClick = close) { Text("Exit VR") }
            }
            PanoramaSurface(player, projection, stereo = true, modifier = Modifier.weight(1f).fillMaxWidth())
            Text(if (projection == VideoProjection.FLAT) "Landscape headset viewer · flat 2D" else "Drag to look around · source 360°", Modifier.padding(12.dp), color = Color.White)
        }
    }
}

/** A single decoder surface owns both eye views; controller changes recreate ownership safely. */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
internal fun PanoramaSurface(player: MediaController?, projection: VideoProjection, stereo: Boolean, modifier: Modifier,
    onView: (PanoramaVideoView) -> Unit = {}) {
    if (projection == VideoProjection.UNSUPPORTED) {
        Box(modifier.background(Color.Black), contentAlignment = androidx.compose.ui.Alignment.Center) {
            Text("This spherical projection is not supported yet.", color = Color.White)
        }
        return
    }
    var failure by remember(player, projection, stereo) { mutableStateOf<String?>(null) }
    if (failure != null) {
        Box(modifier.background(Color.Black), contentAlignment = androidx.compose.ui.Alignment.Center) { Text(failure.orEmpty(), color = Color.White) }
        return
    }
    val owner = LocalLifecycleOwner.current
    var view by remember(player, projection, stereo) { mutableStateOf<PanoramaVideoView?>(null) }
    val callback by rememberUpdatedState(onView)
    key(player, projection, stereo) {
        if (player != null) AndroidView(factory = { context -> PanoramaVideoView(context, player, projection, stereo) { failure = it }.also { view = it; callback(it) } },
            modifier = modifier, update = { callback(it) }, onRelease = { it.release(); view = null })
        else Box(modifier.background(Color.Black))
    }
    DisposableEffect(owner, view) {
        val current = view
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) current?.onResume()
            if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) current?.onPause()
        }
        owner.lifecycle.addObserver(observer)
        if (owner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) current?.onResume()
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
internal class PanoramaVideoView(context: android.content.Context, private val player: MediaController,
    private val projection: VideoProjection, private val stereo: Boolean, private val failure: (String) -> Unit) : GLSurfaceView(context), GLSurfaceView.Renderer {
    @Volatile private var active = true
    @Volatile private var aspect = player.videoSize.let { if (it.height > 0) it.width.toFloat() * it.pixelWidthHeightRatio / it.height else 16f / 9f }
    @Volatile private var yaw = 0f
    @Volatile private var pitch = 0f
    private var texture: SurfaceTexture? = null
    private var surface: Surface? = null
    private var program = 0
    private var textureId = 0
    private var widthPixels = 1
    private var heightPixels = 1
    private val matrix = FloatArray(16)
    private val positions = ByteBuffer.allocateDirect(8 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
    // SurfaceTexture supplies the transform; do not vertically flip the image a second time.
    private val uv = ByteBuffer.allocateDirect(8 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f)); position(0) }
    private val listener = object : androidx.media3.common.Player.Listener {
        override fun onVideoSizeChanged(size: androidx.media3.common.VideoSize) { if (size.height > 0) aspect = size.width.toFloat() * size.pixelWidthHeightRatio / size.height; requestRender() }
    }
    private val gestures = android.view.GestureDetector(context, object : android.view.GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: android.view.MotionEvent) = true
        override fun onSingleTapUp(e: android.view.MotionEvent): Boolean { performClick(); return true }
        override fun onScroll(a: android.view.MotionEvent?, b: android.view.MotionEvent, dx: Float, dy: Float): Boolean {
            if (projection == VideoProjection.FLAT) return false
            yaw = (yaw + dx / width.coerceAtLeast(1) * 3.1415927f) % (2f * 3.1415927f)
            pitch = (pitch + dy / height.coerceAtLeast(1) * 1.5707964f).coerceIn(-1.5f, 1.5f)
            requestRender(); return true
        }
    })
    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        if (event.actionMasked == android.view.MotionEvent.ACTION_DOWN) parent?.requestDisallowInterceptTouchEvent(true)
        if (event.actionMasked == android.view.MotionEvent.ACTION_UP || event.actionMasked == android.view.MotionEvent.ACTION_CANCEL) parent?.requestDisallowInterceptTouchEvent(false)
        return gestures.onTouchEvent(event)
    }
    override fun performClick(): Boolean { super.performClick(); return true }
    init { keepScreenOn = true; setEGLContextClientVersion(2); setRenderer(this); renderMode = RENDERMODE_WHEN_DIRTY; player.addListener(listener) }
    private fun shader(type: Int, source: String): Int = glCreateShader(type).also {
        glShaderSource(it, source); glCompileShader(it)
        val status = IntArray(1); glGetShaderiv(it, GL_COMPILE_STATUS, status, 0)
        check(status[0] != 0) { "Video shader could not compile" }
    }
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        try { createScene() } catch (_: RuntimeException) { fail() }
    }
    private fun fail() { active = false; post { failure("Viewer unavailable. Close and reopen playback.") } }
    private fun createScene() {
        val oldSurface = surface; val oldTexture = texture
        program = glCreateProgram()
        val vertex = shader(GL_VERTEX_SHADER, "attribute vec2 p;attribute vec2 uv;varying vec2 t;varying vec2 screen;void main(){gl_Position=vec4(p,0.,1.);t=uv;screen=p;}")
        val fragment = shader(GL_FRAGMENT_SHADER, """#extension GL_OES_EGL_image_external : require
precision mediump float;
uniform samplerExternalOES image;uniform mat4 m;uniform int spherical;uniform int layout;uniform float eye;uniform float target;uniform float yaw;uniform float pitch;
varying vec2 t;varying vec2 screen;
void main(){vec2 coord=t;
if(spherical==1){vec3 r=normalize(vec3(screen.x*target*0.8390996,screen.y*0.8390996,-1.));
float cp=cos(pitch);float sp=sin(pitch);r=vec3(r.x,cp*r.y-sp*r.z,sp*r.y+cp*r.z);
float cy=cos(yaw);float sy=sin(yaw);r=vec3(cy*r.x-sy*r.z,r.y,sy*r.x+cy*r.z);
coord=vec2(0.5+atan(r.x,-r.z)/6.2831853,0.5+asin(clamp(r.y,-1.,1.))/3.1415927);
if(layout==1)coord.y=(coord.y+(1.-eye))*0.5;
if(layout==2)coord.x=(coord.x+eye)*0.5;
}gl_FragColor=texture2D(image,(m*vec4(coord,0.,1.)).xy);}
""")
        glAttachShader(program, vertex); glAttachShader(program, fragment); glLinkProgram(program)
        glDeleteShader(vertex); glDeleteShader(fragment)
        val status = IntArray(1); glGetProgramiv(program, GL_LINK_STATUS, status, 0); check(status[0] != 0) { "Video shader could not link" }
        val ids = IntArray(1); glGenTextures(1, ids, 0); textureId = ids[0]
        glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
        glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
        glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
        glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
        val input = SurfaceTexture(textureId).also { it.setOnFrameAvailableListener { if (active) requestRender() } }
        texture = input
        val output = Surface(input); surface = output
        post {
            if (active) player.setVideoSurface(output) else { output.release(); input.release() }
            oldSurface?.release(); oldTexture?.release()
        }
    }
    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) { widthPixels = width; heightPixels = height }
    override fun onDrawFrame(gl: GL10?) {
        glClearColor(0f, 0f, 0f, 1f); glClear(GL_COLOR_BUFFER_BIT)
        if (!active) return
        try { texture?.updateTexImage(); texture?.getTransformMatrix(matrix) } catch (_: RuntimeException) { fail(); return }
        glUseProgram(program); glActiveTexture(GL_TEXTURE0); glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        glUniform1i(glGetUniformLocation(program, "image"), 0); glUniformMatrix4fv(glGetUniformLocation(program, "m"), 1, false, matrix, 0)
        val eyes = if (stereo) 2 else 1
        val eyeWidth = widthPixels / eyes
        val target = eyeWidth.toFloat() / heightPixels.coerceAtLeast(1)
        val spherical = projection != VideoProjection.FLAT
        glUniform1i(glGetUniformLocation(program, "spherical"), if (spherical) 1 else 0)
        glUniform1i(glGetUniformLocation(program, "layout"), when (projection) { VideoProjection.EQUIRECTANGULAR_TOP_BOTTOM -> 1; VideoProjection.EQUIRECTANGULAR_LEFT_RIGHT -> 2; else -> 0 })
        glUniform1f(glGetUniformLocation(program, "target"), target)
        glUniform1f(glGetUniformLocation(program, "yaw"), yaw); glUniform1f(glGetUniformLocation(program, "pitch"), pitch)
        val sx = if (!spherical && aspect < target) aspect / target else 1f
        val sy = if (!spherical && aspect > target) target / aspect else 1f
        positions.clear(); positions.put(floatArrayOf(-sx, -sy, sx, -sy, -sx, sy, sx, sy)); positions.position(0)
        val p = glGetAttribLocation(program, "p"); val t = glGetAttribLocation(program, "uv")
        glEnableVertexAttribArray(p); glEnableVertexAttribArray(t)
        glVertexAttribPointer(p, 2, GL_FLOAT, false, 0, positions); glVertexAttribPointer(t, 2, GL_FLOAT, false, 0, uv)
        for (eye in 0 until eyes) {
            glUniform1f(glGetUniformLocation(program, "eye"), eye.toFloat())
            glViewport(eye * eyeWidth, 0, if (eye == eyes - 1) widthPixels - eye * eyeWidth else eyeWidth, heightPixels)
            glDrawArrays(GL_TRIANGLE_STRIP, 0, 4)
        }
    }
    fun release() {
        active = false; keepScreenOn = false; player.removeListener(listener)
        surface?.let { player.clearVideoSurface(it) }
        queueEvent { surface?.release(); texture?.release(); glDeleteProgram(program); glDeleteTextures(1, intArrayOf(textureId), 0) }
        onPause()
    }
}
