package com.gutsplayer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.media.MediaBrowserServiceCompat
import androidx.media.session.MediaButtonReceiver

class MusicService : MediaBrowserServiceCompat() {

    companion object {
        const val ACTION_INIT        = "com.gutsplayer.INIT"
        const val ACTION_PLAY        = "com.gutsplayer.PLAY"
        const val ACTION_PAUSE       = "com.gutsplayer.PAUSE"
        const val ACTION_UPDATE_META = "com.gutsplayer.UPDATE_META"

        const val CHANNEL_ID         = "gutsplayer_channel"
        const val NOTIFICATION_ID    = 101

        // Shared so MainActivity can forward media button intents
        lateinit var mediaSession: MediaSessionCompat
    }

    private lateinit var notificationManager: NotificationManager
    private var isPlaying = false
    private var currentTitle  = "GutsPlayer"
    private var currentArtist = ""
    private var currentAlbum  = ""

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
        setupMediaSession()
    }

    private fun setupMediaSession() {
        // The session callback forwards button presses back into the WebView JS
        val callback = object : MediaSessionCompat.Callback() {
            override fun onPlay() {
                isPlaying = true
                updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
                callWebView("window.nativePlay && window.nativePlay();")
                showNotification()
            }

            override fun onPause() {
                isPlaying = false
                updatePlaybackState(PlaybackStateCompat.STATE_PAUSED)
                callWebView("window.nativePause && window.nativePause();")
                showNotification()
            }

            override fun onSkipToNext() {
                callWebView("window.nativeNext && window.nativeNext();")
            }

            override fun onSkipToPrevious() {
                callWebView("window.nativePrev && window.nativePrev();")
            }

            override fun onStop() {
                isPlaying = false
                stopForeground(true)
                stopSelf()
            }
        }

        mediaSession = MediaSessionCompat(this, "GutsPlayer").apply {
            setCallback(callback)
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
            isActive = true
        }

        // Required for MediaBrowserServiceCompat
        sessionToken = mediaSession.sessionToken

        updatePlaybackState(PlaybackStateCompat.STATE_NONE)
    }

    private fun updatePlaybackState(state: Int) {
        val actions = PlaybackStateCompat.ACTION_PLAY or
                      PlaybackStateCompat.ACTION_PAUSE or
                      PlaybackStateCompat.ACTION_PLAY_PAUSE or
                      PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                      PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                      PlaybackStateCompat.ACTION_STOP

        val playbackState = PlaybackStateCompat.Builder()
            .setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1f)
            .setActions(actions)
            .build()

        mediaSession.setPlaybackState(playbackState)
    }

    private fun updateMetadata(title: String, artist: String, album: String) {
        val metadata = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE,  title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM,  album)
            .putBitmap(
                MediaMetadataCompat.METADATA_KEY_ALBUM_ART,
                BitmapFactory.decodeResource(resources, R.drawable.ic_music_note)
            )
            .build()
        mediaSession.setMetadata(metadata)
    }

    private fun showNotification() {
        val notification = buildNotification()
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(): Notification {
        // Tapping the notification opens the app
        val openAppIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Media button pending intents
        val prevIntent = MediaButtonReceiver.buildMediaButtonPendingIntent(
            this, PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)
        val playPauseIntent = MediaButtonReceiver.buildMediaButtonPendingIntent(
            this, PlaybackStateCompat.ACTION_PLAY_PAUSE)
        val nextIntent = MediaButtonReceiver.buildMediaButtonPendingIntent(
            this, PlaybackStateCompat.ACTION_SKIP_TO_NEXT)

        val playPauseIcon = if (isPlaying)
            android.R.drawable.ic_media_pause
        else
            android.R.drawable.ic_media_play

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(currentTitle)
            .setContentText(currentArtist)
            .setSubText(currentAlbum)
            .setSmallIcon(R.drawable.ic_music_note)
            .setLargeIcon(BitmapFactory.decodeResource(resources, R.drawable.ic_music_note))
            .setContentIntent(openAppIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            // ← These are the lock screen / notification shade controls
            .addAction(android.R.drawable.ic_media_previous, "Previous", prevIntent)
            .addAction(playPauseIcon, if (isPlaying) "Pause" else "Play", playPauseIntent)
            .addAction(android.R.drawable.ic_media_next, "Next", nextIntent)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2) // show all 3 in compact
                    .setShowCancelButton(true)
                    .setCancelButtonIntent(
                        MediaButtonReceiver.buildMediaButtonPendingIntent(
                            this, PlaybackStateCompat.ACTION_STOP
                        )
                    )
            )
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Forward media button events to the session
        MediaButtonReceiver.handleIntent(mediaSession, intent)

        when (intent?.action) {
            ACTION_INIT -> {
                // Start a silent foreground notification immediately so the service doesn't crash
                startForeground(NOTIFICATION_ID, buildNotification())
            }
            ACTION_PLAY -> {
                isPlaying = true
                currentTitle  = intent.getStringExtra("title")  ?: currentTitle
                currentArtist = intent.getStringExtra("artist") ?: currentArtist
                updateMetadata(currentTitle, currentArtist, currentAlbum)
                updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
                showNotification()
            }
            ACTION_PAUSE -> {
                isPlaying = false
                updatePlaybackState(PlaybackStateCompat.STATE_PAUSED)
                showNotification()
            }
            ACTION_UPDATE_META -> {
                currentTitle  = intent.getStringExtra("title")  ?: currentTitle
                currentArtist = intent.getStringExtra("artist") ?: currentArtist
                currentAlbum  = intent.getStringExtra("album")  ?: currentAlbum
                updateMetadata(currentTitle, currentArtist, currentAlbum)
                if (isPlaying) showNotification()
            }
        }

        return START_STICKY
    }

    private fun callWebView(js: String) {
        // Find the MainActivity and call JS through it
        // We use a broadcast-like approach via application context cast
        val app = application as? GutsPlayerApp
        app?.mainActivity?.callJS(js)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Music Playback",
                NotificationManager.IMPORTANCE_LOW  // LOW = no sound, but persistent
            ).apply {
                description = "GutsPlayer music controls"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    // MediaBrowserServiceCompat required overrides
    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): BrowserRoot {
        return BrowserRoot("root", null)
    }

    override fun onLoadChildren(parentId: String, result: Result<MutableList<MediaBrowserCompat.MediaItem>>) {
        result.sendResult(mutableListOf())
    }

    override fun onDestroy() {
        mediaSession.release()
        super.onDestroy()
    }
}
