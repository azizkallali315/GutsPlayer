package com.gutsplayer

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.webkit.*
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.media.session.MediaButtonReceiver

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private val PERMISSION_REQUEST = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        (application as GutsPlayerApp).mainActivity = this

        webView = findViewById(R.id.webView)
        setupWebView()
        requestPermissions()
    }

    override fun onDestroy() {
        (application as GutsPlayerApp).mainActivity = null
        super.onDestroy()
    }

    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            mediaPlaybackRequiresUserGesture = false  // allow autoplay
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            setSupportMultipleWindows(false)
            cacheMode = WebSettings.LOAD_DEFAULT
        }

        // Inject the JavaScript bridge that lets your HTML talk to the native service
        webView.addJavascriptInterface(MusicBridge(this), "AndroidBridge")

        webView.webChromeClient = object : WebChromeClient() {
            // Allow media autoplay
            override fun onPermissionRequest(request: PermissionRequest?) {
                request?.grant(request.resources)
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                // Inject JS glue so your existing mediaSession code also drives the native bridge
                injectMediaSessionBridge()
            }
        }

        // Load the app from assets
        webView.loadUrl("file:///android_asset/index.html")
    }

    /**
     * Injects JavaScript into your HTML page that intercepts mediaSession calls
     * and forwards them to the native Android bridge.
     * This means your existing navigator.mediaSession code works WITHOUT changes.
     */
    private fun injectMediaSessionBridge() {
        val js = """
            (function() {
                // Tell the native side we're ready
                if (window.AndroidBridge) {
                    AndroidBridge.onPageReady();
                }

                // Intercept audio element play/pause to sync with native service
                function hookAudio() {
                    var audioElements = document.querySelectorAll('audio');
                    audioElements.forEach(function(audio) {
                        if (audio._bridgeHooked) return;
                        audio._bridgeHooked = true;

                        audio.addEventListener('play', function() {
                            var title = (navigator.mediaSession && navigator.mediaSession.metadata)
                                ? navigator.mediaSession.metadata.title : 'GutsPlayer';
                            var artist = (navigator.mediaSession && navigator.mediaSession.metadata)
                                ? navigator.mediaSession.metadata.artist : '';
                            AndroidBridge.onPlay(title, artist);
                        });

                        audio.addEventListener('pause', function() {
                            AndroidBridge.onPause();
                        });

                        audio.addEventListener('ended', function() {
                            AndroidBridge.onEnded();
                        });
                    });
                }

                // Run immediately and watch for new audio elements
                hookAudio();
                var observer = new MutationObserver(hookAudio);
                observer.observe(document.body, { childList: true, subtree: true });

                // Also intercept mediaSession metadata sets so we get title/artist updates
                if ('mediaSession' in navigator) {
                    var origMetadata = Object.getOwnPropertyDescriptor(navigator.mediaSession.__proto__, 'metadata');
                    if (origMetadata && origMetadata.set) {
                        Object.defineProperty(navigator.mediaSession, 'metadata', {
                            set: function(meta) {
                                origMetadata.set.call(navigator.mediaSession, meta);
                                if (meta && window.AndroidBridge) {
                                    AndroidBridge.updateMetadata(
                                        meta.title || '',
                                        meta.artist || '',
                                        meta.album || ''
                                    );
                                }
                            },
                            get: origMetadata.get
                        });
                    }
                }

                // Expose functions so native can call back into JS
                window.nativePrev  = function() { if (window._prevTrack)  window._prevTrack();  };
                window.nativeNext  = function() { if (window._nextTrack)  window._nextTrack();  };
                window.nativePlay  = function() { if (window._playPause)  window._playPause();  };
                window.nativePause = function() { if (window._playPause)  window._playPause();  };

                console.log('[GutsPlayer] Native bridge injected');
            })();
        """.trimIndent()

        webView.evaluateJavascript(js, null)
    }

    // Called FROM native service → INTO WebView JS
    fun callJS(script: String) {
        runOnUiThread {
            webView.evaluateJavascript(script, null)
        }
    }

    private fun requestPermissions() {
        val needed = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.READ_MEDIA_AUDIO)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), PERMISSION_REQUEST)
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        MediaButtonReceiver.handleIntent(
            MusicService.mediaSession, intent
        )
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack()
        else super.onBackPressed()
    }
}
