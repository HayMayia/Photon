package com.example.player

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

interface YouTubePlayerListener {
    fun onPlayerReady()
    fun onPlaybackStateChanged(isPlaying: Boolean, isBuffering: Boolean, ended: Boolean)
    fun onProgressUpdate(currentSec: Int, durationSec: Int)
    fun onPlayerError(message: String)
}

/**
 * First-party mobile YouTube WebView player (`m.youtube.com/watch?v=...`).
 *
 * Unlike `/embed/` IFrames (which reject official music/VEVO tracks with Error 150/152/153),
 * loading `m.youtube.com/watch` directly plays YouTube videos and songs natively,
 * isolates the full-bleed `<video>` surface on pure `#000000` AMOLED black, and syncs
 * Play/Pause/Seek/Speed/Progress directly against the HTML5 `<video>` DOM element.
 */
class YouTubePlayerController(
    context: Context,
    private val listener: YouTubePlayerListener
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var currentVideoId: String? = null
    private var desiredAutoplay: Boolean = true
    private var desiredSpeed: Float = 1.0f

    val webView: WebView = createWebView(context)

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(context: Context): WebView {
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        // Pre-seed consent cookie so YouTube does not interrupt playback with a cookie wall
        cookieManager.setCookie("https://m.youtube.com", "SOCS=CAESEwgDEgk0ODE3Nzk3MjQaAmVuIAEaBgiA_LyaBg; Path=/; Domain=.youtube.com")
        cookieManager.setCookie("https://www.youtube.com", "SOCS=CAESEwgDEgk0ODE3Nzk3MjQaAmVuIAEaBgiA_LyaBg; Path=/; Domain=.youtube.com")
        cookieManager.flush()

        return WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.BLACK)
            cookieManager.setAcceptThirdPartyCookies(this, true)

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = false
                useWideViewPort = true
                loadWithOverviewMode = true
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                cacheMode = WebSettings.LOAD_DEFAULT
                builtInZoomControls = false
                displayZoomControls = false
                setSupportZoom(false)
                userAgentString =
                    "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
            }

            webChromeClient = object : WebChromeClient() {
                override fun getDefaultVideoPoster(): android.graphics.Bitmap {
                    // Pure AMOLED black poster instead of Android's gray default play poster
                    return android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888).apply {
                        eraseColor(Color.BLACK)
                    }
                }
            }

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    val uri = request?.url ?: return true
                    val scheme = uri.scheme.orEmpty().lowercase()
                    if (scheme != "http" && scheme != "https") {
                        // Block intent:// and vnd.youtube:// app-switch links
                        return true
                    }
                    val host = uri.host.orEmpty().lowercase()
                    val allowed = host.endsWith("youtube.com") ||
                        host.endsWith("youtu.be") ||
                        host.endsWith("googlevideo.com") ||
                        host.endsWith("google.com")
                    return !allowed
                }

                override fun onPageCommitVisible(view: WebView?, url: String?) {
                    super.onPageCommitVisible(view, url)
                    if (url != null && url.contains("youtube.com/watch")) {
                        injectIsolationCssOnly()
                    }
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    if (url == null) return
                    if (url.contains("consent.youtube.com")) {
                        evalJs(
                            """
                            (function() {
                              var btn = document.querySelector('form button[type="submit"], button[aria-label*="Accept"], button[aria-label*="Agree"]');
                              if (btn) btn.click();
                            })();
                            """.trimIndent()
                        )
                        return
                    }
                    if (url.contains("youtube.com/watch")) {
                        injectIsolationAndBridge()
                        listener.onPlayerReady()
                    }
                }
            }

            addJavascriptInterface(JsBridge(), "PhotonBridge")
            loadDataWithBaseURL(
                "https://m.youtube.com",
                "<html><body style='margin:0;background:#000000;'></body></html>",
                "text/html",
                "UTF-8",
                null
            )
        }
    }

    fun loadVideo(videoId: String, autoplay: Boolean = true) {
        val safeId = sanitizeId(videoId)
        if (safeId.isBlank()) return
        currentVideoId = safeId
        desiredAutoplay = autoplay
        mainHandler.post {
            try {
                webView.onResume()
                webView.resumeTimers()
                webView.evaluateJavascript(
                    "if(window.__photonInterval){clearInterval(window.__photonInterval);window.__photonInterval=null;}",
                    null
                )
                webView.loadUrl("https://m.youtube.com/watch?v=$safeId")
            } catch (_: Throwable) {
                listener.onPlayerError("Unable to load video stream.")
            }
        }
    }

    fun play() {
        desiredAutoplay = true
        evalJs("window.__photonPlay && window.__photonPlay();")
    }

    fun pause() {
        desiredAutoplay = false
        evalJs("window.__photonPause && window.__photonPause();")
    }

    fun seekTo(seconds: Int) {
        evalJs("window.__photonSeek && window.__photonSeek(${seconds.coerceAtLeast(0)});")
    }

    fun setPlaybackRate(rate: Float) {
        desiredSpeed = rate
        evalJs("window.__photonSetRate && window.__photonSetRate($rate);")
    }

    fun destroy() {
        mainHandler.post {
            try {
                webView.stopLoading()
                webView.loadUrl("about:blank")
                webView.removeJavascriptInterface("PhotonBridge")
                (webView.parent as? ViewGroup)?.removeView(webView)
                webView.destroy()
            } catch (_: Throwable) {
            }
        }
    }

    private fun injectIsolationCssOnly() {
        evalJs(buildIsolationCssScript())
    }

    private fun injectIsolationAndBridge() {
        val script = buildBridgeScript(autoplay = desiredAutoplay, speed = desiredSpeed)
        evalJs(script)
    }

    private fun evalJs(script: String) {
        mainHandler.post {
            try {
                webView.evaluateJavascript(script, null)
            } catch (_: Throwable) {
            }
        }
    }

    private fun sanitizeId(id: String): String =
        id.filter { it.isLetterOrDigit() || it == '_' || it == '-' }

    private inner class JsBridge {
        @JavascriptInterface
        fun onStateChange(isPlaying: Boolean, isBuffering: Boolean, ended: Boolean) {
            mainHandler.post {
                listener.onPlaybackStateChanged(
                    isPlaying = isPlaying,
                    isBuffering = isBuffering,
                    ended = ended
                )
            }
        }

        @JavascriptInterface
        fun onProgress(currentSec: Double, durationSec: Double) {
            mainHandler.post {
                listener.onProgressUpdate(
                    currentSec = currentSec.toInt().coerceAtLeast(0),
                    durationSec = durationSec.toInt().coerceAtLeast(0)
                )
            }
        }

        @JavascriptInterface
        fun onError(message: String) {
            mainHandler.post {
                listener.onPlayerError(message)
            }
        }
    }

    companion object {
        private const val ISOLATION_CSS = """
            html, body {
              margin: 0 !important;
              padding: 0 !important;
              background: #000000 !important;
              overflow: hidden !important;
              height: 100% !important;
              width: 100% !important;
            }
            ytm-mobile-topbar-renderer,
            .mobile-topbar-header,
            ytm-pivot-bar-renderer,
            ytm-single-column-watch-next-results-renderer,
            ytm-item-section-renderer,
            ytm-mealbar-promo-renderer,
            ytm-upsell-dialog-renderer,
            tp-yt-paper-dialog,
            .watch-below-the-player,
            #header-bar,
            .ytm-autonav-bar,
            .open-app-button {
              display: none !important;
              visibility: hidden !important;
            }
            #player-container-id,
            .player-container,
            #player,
            #movie_player,
            .html5-video-player {
              position: fixed !important;
              top: 0 !important;
              left: 0 !important;
              right: 0 !important;
              bottom: 0 !important;
              width: 100vw !important;
              height: 100vh !important;
              max-height: 100vh !important;
              z-index: 99999 !important;
              background: #000000 !important;
            }
            .html5-video-container,
            video.video-stream,
            video {
              position: fixed !important;
              top: 0 !important;
              left: 0 !important;
              width: 100vw !important;
              height: 100vh !important;
              object-fit: contain !important;
              background: #000000 !important;
            }
            .ytp-unmute,
            .ytm-unmute-button {
              display: none !important;
            }
        """

        private fun buildIsolationCssScript(): String = """
            (function() {
              try {
                var styleId = 'photon-isolation-css';
                if (!document.getElementById(styleId)) {
                  var st = document.createElement('style');
                  st.id = styleId;
                  st.textContent = `$ISOLATION_CSS`;
                  (document.head || document.documentElement).appendChild(st);
                }
              } catch (_) {}
            })();
        """.trimIndent()

        private fun buildBridgeScript(autoplay: Boolean, speed: Float): String = """
            (function() {
              try {
                // 1. Inject AMOLED isolation CSS so ONLY the real YouTube player fills the 16:9 viewport
                var styleId = 'photon-isolation-css';
                if (!document.getElementById(styleId)) {
                  var st = document.createElement('style');
                  st.id = styleId;
                  st.textContent = `$ISOLATION_CSS`;
                  (document.head || document.documentElement).appendChild(st);
                }

                // 2. Keep page awake & prevent background visibility throttling inside app
                try {
                  Object.defineProperty(document, 'hidden', { get: function() { return false; }, configurable: true });
                  Object.defineProperty(document, 'visibilityState', { get: function() { return 'visible'; }, configurable: true });
                } catch (_) {}

                window.__photonAutoplay = ${if (autoplay) "true" else "false"};
                window.__photonRate = $speed;
                window.__photonEndedSent = false;
                window.__photonLastStateKey = '';
                window.__photonLastProgressKey = '';

                function emitState(playing, buffering, ended) {
                  var key = playing + '|' + buffering + '|' + ended;
                  if (key === window.__photonLastStateKey && !ended) return;
                  window.__photonLastStateKey = key;
                  if (window.PhotonBridge) {
                    window.PhotonBridge.onStateChange(playing, buffering, ended);
                  }
                }

                function emitProgress(cur, dur) {
                  var key = Math.floor(cur) + '|' + Math.floor(dur);
                  if (key === window.__photonLastProgressKey) return;
                  window.__photonLastProgressKey = key;
                  if (window.PhotonBridge) {
                    window.PhotonBridge.onProgress(cur, dur);
                  }
                }

                function getVideo() {
                  return document.querySelector('video.html5-main-video') || document.querySelector('video');
                }

                function isAdPlaying() {
                  return !!document.querySelector('.ad-showing, .ad-interrupting');
                }

                function tryUnmuteAndPlay(v) {
                  if (!v) return;
                  try {
                    if (v.muted) {
                      v.muted = false;
                      var unmuteBtn = document.querySelector('.ytp-unmute, .ytm-unmute-button, button[aria-label*="Unmute"]');
                      if (unmuteBtn) { try { unmuteBtn.click(); } catch (_) {} }
                    }
                    v.volume = 1.0;
                    if (window.__photonRate && v.playbackRate !== window.__photonRate) {
                      v.playbackRate = window.__photonRate;
                    }
                    var p = v.play();
                    if (p && typeof p.catch === 'function') {
                      p.catch(function() {
                        var btn = document.querySelector('.ytp-large-play-button, button[aria-label*="Play"]');
                        if (btn) btn.click();
                      });
                    }
                  } catch (_) {}
                }

                window.__photonPlay = function() {
                  window.__photonAutoplay = true;
                  var v = getVideo();
                  if (v) {
                    tryUnmuteAndPlay(v);
                  } else {
                    var btn = document.querySelector('.ytp-large-play-button, button[aria-label*="Play"]');
                    if (btn) btn.click();
                  }
                };

                window.__photonPause = function() {
                  window.__photonAutoplay = false;
                  var v = getVideo();
                  if (v) v.pause();
                };

                window.__photonSeek = function(sec) {
                  var v = getVideo();
                  if (v && isFinite(sec)) {
                    v.currentTime = sec;
                    window.__photonLastProgressKey = '';
                    emitProgress(sec, isFinite(v.duration) ? v.duration : 0);
                  }
                };

                window.__photonSetRate = function(rate) {
                  window.__photonRate = rate;
                  var v = getVideo();
                  if (v && isFinite(rate)) {
                    v.playbackRate = rate;
                  }
                };

                function bindVideo(v) {
                  if (!v || v.__photonBound) return;
                  v.__photonBound = true;
                  window.__photonEndedSent = false;

                  v.addEventListener('playing', function() {
                    window.__photonEndedSent = false;
                    if (v.muted) v.muted = false;
                    if (window.__photonRate && v.playbackRate !== window.__photonRate) {
                      v.playbackRate = window.__photonRate;
                    }
                    emitState(true, false, false);
                  });

                  v.addEventListener('pause', function() {
                    if (!v.ended) {
                      emitState(false, false, false);
                    }
                  });

                  v.addEventListener('waiting', function() {
                    emitState(false, true, false);
                  });

                  v.addEventListener('ended', function() {
                    if (!isAdPlaying() && !window.__photonEndedSent) {
                      window.__photonEndedSent = true;
                      emitState(false, false, true);
                    }
                  });

                  if (window.__photonAutoplay) {
                    tryUnmuteAndPlay(v);
                  }
                }

                if (window.__photonInterval) clearInterval(window.__photonInterval);
                var autoPlayAttempts = 0;

                window.__photonInterval = setInterval(function() {
                  var skipBtn = document.querySelector('.ytp-skip-ad-button, .ytp-ad-skip-button');
                  if (skipBtn && skipBtn.offsetParent !== null) {
                    try { skipBtn.click(); } catch (_) {}
                  }

                  var v = getVideo();
                  if (v) {
                    bindVideo(v);
                    if (window.__photonAutoplay && (v.paused || v.muted) && !v.ended && autoPlayAttempts < 20) {
                      autoPlayAttempts++;
                      tryUnmuteAndPlay(v);
                    }
                    if (!isAdPlaying()) {
                      var cur = isFinite(v.currentTime) ? v.currentTime : 0;
                      var dur = isFinite(v.duration) ? v.duration : 0;
                      emitProgress(cur, dur);
                      var playing = !v.paused && !v.ended && v.readyState >= 3;
                      var buffering = !v.paused && !v.ended && v.readyState < 3;
                      emitState(playing, buffering, false);
                    }
                  }
                }, 400);
              } catch (err) {
                if (window.PhotonBridge) window.PhotonBridge.onError(String(err));
              }
            })();
        """.trimIndent()
    }
}
