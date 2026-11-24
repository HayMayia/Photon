package com.example

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.example.ui.PhotonApp
import com.example.ui.theme.PhotonTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure optimized Coil memory & disk cache for fast 120fps thumbnail scrolling
        val imageLoader = ImageLoader.Builder(applicationContext)
            .memoryCache {
                MemoryCache.Builder(applicationContext)
                    .maxSizePercent(0.20)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(applicationContext.cacheDir.resolve("photon_thumb_cache"))
                    .maxSizeBytes(64L * 1024L * 1024L)
                    .build()
            }
            .respectCacheHeaders(false)
            .crossfade(true)
            .build()
        Coil.setImageLoader(imageLoader)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        setContent {
            PhotonTheme {
                PhotonApp()
            }
        }
    }
}
