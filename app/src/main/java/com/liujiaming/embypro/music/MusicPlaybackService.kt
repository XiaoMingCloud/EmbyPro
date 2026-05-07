package com.liujiaming.embypro

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Foreground service for background music playback using ExoPlayer.
 * Manages audio session, media session for system controls, and playback notifications.
 * Supports play/pause, seek, and stop operations via notification and media buttons.
 */
class MusicPlaybackService : Service() {
    private val binder = LocalBinder()
    private val sleepTimerHandler = Handler(Looper.getMainLooper())

    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaSession
    private var sleepTimerDeadlineMs: Long? = null
    private var currentArtworkUrl: String? = null
    private var currentArtworkBitmap: Bitmap? = null

    private val sleepTimerRunnable = Runnable {
        if (sleepTimerDeadlineMs == null) return@Runnable
        stopPlaybackAndSelf(StopReason.SLEEP_TIMER)
    }

    override fun onCreate() {
        super.onCreate()
        activeService = this
        createNotificationChannel()
        initializePlayer()
        initializeMediaSession()
        notifyStateChanged()
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE_PLAYBACK -> {
                togglePlayback()
            }
            ACTION_STOP -> {
                stopPlaybackAndSelf()
            }
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        clearSleepTimer(notify = false)
        player.removeListener(playerListener)
        player.release()
        mediaSession.release()
        stopForegroundCompat(removeNotification = true)
        if (activeService === this) {
            activeService = null
        }
        notifyStateChanged()
        super.onDestroy()
    }

    /**
     * Returns the ExoPlayer instance for direct control.
     */
    fun getPlayer(): ExoPlayer {
        return player
    }

    /**
     * Checks if there is active media in the playlist.
     */
    fun hasActivePlayback(): Boolean {
        return ::player.isInitialized && player.mediaItemCount > 0
    }

    /**
     * Toggles between play and pause states.
     */
    fun togglePlayback() {
        if (!hasActivePlayback()) return
        if (player.isPlaying) {
            player.pause()
        } else {
            player.play()
        }
        notifyStateChanged()
    }

    /**
     * Seeks to the specified position in the current media.
     * Position is clamped to valid range [0, duration].
     */
    fun seekTo(positionMs: Long) {
        if (!hasActivePlayback()) return
        val duration = player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
        player.seekTo(positionMs.coerceIn(0L, duration))
        notifyStateChanged()
    }

    /**
     * Starts or replaces a sleep timer. Playback will stop automatically when it expires.
     */
    fun startSleepTimer(durationMs: Long) {
        if (durationMs <= 0L) {
            clearSleepTimer()
            return
        }
        sleepTimerHandler.removeCallbacks(sleepTimerRunnable)
        sleepTimerDeadlineMs = SystemClock.elapsedRealtime() + durationMs
        sleepTimerHandler.postDelayed(sleepTimerRunnable, durationMs)
        notifyStateChanged()
    }

    /**
     * Clears the current sleep timer if one exists.
     */
    fun clearSleepTimer(notify: Boolean = true) {
        sleepTimerHandler.removeCallbacks(sleepTimerRunnable)
        sleepTimerDeadlineMs = null
        if (notify) {
            notifyStateChanged()
        }
    }

    /**
     * Returns remaining time for the active sleep timer, or null if not set.
     */
    fun getSleepTimerRemainingMs(): Long? {
        val deadline = sleepTimerDeadlineMs ?: return null
        val remaining = deadline - SystemClock.elapsedRealtime()
        return if (remaining > 0L) remaining else 0L
    }

    /**
     * Stops playback, clears media queue, and stops the service.
     */
    fun stopPlaybackAndSelf(reason: StopReason = StopReason.USER) {
        lastStopReason = reason
        clearSleepTimer(notify = false)
        if (::player.isInitialized) {
            player.playWhenReady = false
            player.pause()
            player.stop()
            player.clearMediaItems()
        }
        stopForegroundCompat(removeNotification = true)
        notifyStateChanged()
        stopSelf()
    }

    /**
     * Initializes ExoPlayer with music-optimized audio attributes.
     * Handles audio focus and headphone unplugging automatically.
     */
    private fun initializePlayer() {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        player = ExoPlayer.Builder(this)
            .build()
            .apply {
                setAudioAttributes(audioAttributes, true)
                setHandleAudioBecomingNoisy(true)
                addListener(playerListener)
            }
    }

    /**
     * Initializes MediaSession for system-level playback control.
     * Handles play, pause, stop, and seek commands from external sources.
     */
    private fun initializeMediaSession() {
        mediaSession = MediaSession(this, "EmbyProMusicPlayback").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() {
                    player.play()
                }

                override fun onPause() {
                    player.pause()
                }

                override fun onStop() {
                    player.pause()
                }

                override fun onSeekTo(pos: Long) {
                    player.seekTo(pos)
                }
            })
            isActive = true
        }
        syncMediaSessionState()
    }

    /**
     * Synchronizes MediaSession state with current player state.
     * Updates playback state and media metadata.
     */
    private fun syncMediaSessionState() {
        val state = when {
            player.isPlaying -> PlaybackState.STATE_PLAYING
            player.playbackState == Player.STATE_BUFFERING -> PlaybackState.STATE_BUFFERING
            else -> PlaybackState.STATE_PAUSED
        }

        val playbackState = PlaybackState.Builder()
            .setActions(
                PlaybackState.ACTION_PLAY or
                    PlaybackState.ACTION_PAUSE or
                    PlaybackState.ACTION_PLAY_PAUSE or
                    PlaybackState.ACTION_SEEK_TO or
                    PlaybackState.ACTION_STOP
            )
            .setState(state, player.currentPosition, if (player.isPlaying) 1f else 0f)
            .build()
        mediaSession.setPlaybackState(playbackState)

        val currentMetadata = player.currentMediaItem?.mediaMetadata ?: MediaMetadata.EMPTY
        val mediaMetadataBuilder = android.media.MediaMetadata.Builder()
            .putString(android.media.MediaMetadata.METADATA_KEY_TITLE, currentMetadata.title?.toString())
            .putString(android.media.MediaMetadata.METADATA_KEY_ARTIST, currentMetadata.artist?.toString())
        currentArtworkBitmap?.let { bitmap ->
            mediaMetadataBuilder
                .putBitmap(android.media.MediaMetadata.METADATA_KEY_ALBUM_ART, bitmap)
                .putBitmap(android.media.MediaMetadata.METADATA_KEY_ART, bitmap)
                .putBitmap(android.media.MediaMetadata.METADATA_KEY_DISPLAY_ICON, bitmap)
        }
        mediaSession.setMetadata(mediaMetadataBuilder.build())
    }

    /**
     * Updates or removes the foreground notification based on playback state.
     */
    private fun updateForegroundNotification() {
        if (player.mediaItemCount == 0) {
            stopForegroundCompat(removeNotification = true)
            return
        }

        val notification = buildNotification()
        startForeground(NOTIFICATION_ID, notification)
    }

    /**
     * Builds the playback notification with media controls.
     * Shows play/pause and stop actions with media style layout.
     */
    private fun buildNotification(): Notification {
        val metadata = player.currentMediaItem?.mediaMetadata
        val title = metadata?.title?.toString().orEmpty().ifBlank { getString(R.string.app_name) }
        val subtitle = metadata?.artist?.toString().orEmpty()
        val isPlaying = player.isPlaying

        val playPauseIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, MusicPlaybackService::class.java).setAction(ACTION_TOGGLE_PLAYBACK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, MusicPlaybackService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val launchIntent = Intent(this, MusicPlayerActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putServerConnection(MusicPlayerSessionStore.currentConnection() ?: return@apply)
            putExtra("extra_queue_ids", ArrayList<String>())
            putExtra("extra_queue_titles", ArrayList<String>())
        }
        val contentIntent = PendingIntent.getActivity(
            this,
            3,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_music_library_song)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setContentIntent(contentIntent)
            .setLargeIcon(currentArtworkBitmap)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setShowWhen(false)
            .setOngoing(isPlaying)
            .addAction(
                Notification.Action.Builder(
                    if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                    if (isPlaying) getString(R.string.pause) else getString(R.string.play),
                    playPauseIntent
                ).build()
            )
            .addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    getString(android.R.string.cancel),
                    stopIntent
                ).build()
            )
            .setStyle(
                Notification.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0)
            )
            .build()
    }

    private fun refreshArtworkIfNeeded() {
        val artworkUrl = player.currentMediaItem?.mediaMetadata?.artworkUri?.toString().orEmpty().ifBlank { null }
        if (artworkUrl == currentArtworkUrl && (artworkUrl == null || currentArtworkBitmap != null)) {
            return
        }

        currentArtworkUrl = artworkUrl
        currentArtworkBitmap?.recycle()
        currentArtworkBitmap = null

        if (artworkUrl == null) {
            syncMediaSessionState()
            updateForegroundNotification()
            return
        }

        EmbyImageLoader.loadBitmap(
            context = applicationContext,
            url = artworkUrl,
            token = MusicPlayerSessionStore.currentConnection()?.accessToken
        ) { bitmap ->
            if (currentArtworkUrl != artworkUrl) return@loadBitmap
            currentArtworkBitmap?.recycle()
            currentArtworkBitmap = bitmap
            syncMediaSessionState()
            updateForegroundNotification()
        }
    }

    /**
     * Creates the notification channel for Android O and above.
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.app_name),
            NotificationManager.IMPORTANCE_LOW
        )
        channel.description = getString(R.string.app_name)
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    /**
     * Stops foreground mode with compatibility for different Android versions.
     */
    private fun stopForegroundCompat(removeNotification: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(
                if (removeNotification) STOP_FOREGROUND_REMOVE else STOP_FOREGROUND_DETACH
            )
        } else {
            @Suppress("DEPRECATION")
            stopForeground(removeNotification)
        }
    }

    /**
     * Listens to player state changes and updates notification and media session.
     */
    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            refreshArtworkIfNeeded()
            syncMediaSessionState()
            updateForegroundNotification()
            notifyStateChanged()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            refreshArtworkIfNeeded()
            syncMediaSessionState()
            updateForegroundNotification()
            notifyStateChanged()
        }

        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            refreshArtworkIfNeeded()
            syncMediaSessionState()
            updateForegroundNotification()
            notifyStateChanged()
        }

        override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
            refreshArtworkIfNeeded()
            syncMediaSessionState()
            updateForegroundNotification()
            notifyStateChanged()
        }
    }

    /**
     * Binder for local service connections.
     */
    inner class LocalBinder : Binder() {
        fun getService(): MusicPlaybackService = this@MusicPlaybackService
    }

    companion object {
        private const val CHANNEL_ID = "music_playback"
        private const val NOTIFICATION_ID = 3001

        private const val ACTION_TOGGLE_PLAYBACK = "com.liujiaming.embypro.action.TOGGLE_PLAYBACK"
        private const val ACTION_STOP = "com.liujiaming.embypro.action.STOP"

        private val mainHandler = Handler(Looper.getMainLooper())
        private val stateListeners = CopyOnWriteArraySet<PlaybackStateListener>()

        @Volatile
        private var activeService: MusicPlaybackService? = null
        @Volatile
        private var lastStopReason: StopReason? = null

        /**
         * Returns the currently active service instance.
         */
        fun activeService(): MusicPlaybackService? = activeService

        fun consumeLastStopReason(): StopReason? {
            val reason = lastStopReason
            lastStopReason = null
            return reason
        }

        /**
         * Registers a listener for playback state changes.
         * Immediately notifies listener of current state.
         */
        fun registerStateListener(listener: PlaybackStateListener) {
            stateListeners.add(listener)
            mainHandler.post { listener.onMusicPlaybackStateChanged() }
        }

        /**
         * Unregisters a previously registered state listener.
         */
        fun unregisterStateListener(listener: PlaybackStateListener) {
            stateListeners.remove(listener)
        }

        /**
         * Notifies all registered listeners of state change on main thread.
         */
        private fun notifyStateChanged() {
            mainHandler.post {
                stateListeners.forEach { it.onMusicPlaybackStateChanged() }
            }
        }
    }

    /**
     * Functional interface for listening to playback state changes.
     */
    fun interface PlaybackStateListener {
        fun onMusicPlaybackStateChanged()
    }

    enum class StopReason {
        USER,
        SLEEP_TIMER
    }
}
