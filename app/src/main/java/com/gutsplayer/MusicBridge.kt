package com.gutsplayer

import android.content.Context
import android.content.Intent
import android.webkit.JavascriptInterface

/**
 * This is the glue between your HTML/JS app and the native Android MusicService.
 * Methods here are callable from JavaScript as: AndroidBridge.methodName(...)
 */
class MusicBridge(private val context: Context) {

    @JavascriptInterface
    fun onPageReady() {
        // Start the service as soon as the page loads so it's ready
        val intent = Intent(context, MusicService::class.java).apply {
            action = MusicService.ACTION_INIT
        }
        context.startForegroundService(intent)
    }

    @JavascriptInterface
    fun onPlay(title: String, artist: String) {
        val intent = Intent(context, MusicService::class.java).apply {
            action = MusicService.ACTION_PLAY
            putExtra("title", title)
            putExtra("artist", artist)
        }
        context.startForegroundService(intent)
    }

    @JavascriptInterface
    fun onPause() {
        val intent = Intent(context, MusicService::class.java).apply {
            action = MusicService.ACTION_PAUSE
        }
        context.startForegroundService(intent)
    }

    @JavascriptInterface
    fun onEnded() {
        // Song ended — trigger next in JS
        (context as? MainActivity)?.callJS("window.nativeNext && window.nativeNext();")
    }

    @JavascriptInterface
    fun updateMetadata(title: String, artist: String, album: String) {
        val intent = Intent(context, MusicService::class.java).apply {
            action = MusicService.ACTION_UPDATE_META
            putExtra("title", title)
            putExtra("artist", artist)
            putExtra("album", album)
        }
        context.startForegroundService(intent)
    }
}
