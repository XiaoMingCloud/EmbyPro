package com.liujiaming.embypro

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.View
import android.view.ViewPropertyAnimator
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player

/**
 * Activity for music playback with queue management.
 * Provides play/pause, seek, previous/next controls and favorite toggle.
 * Integrates with MusicPlaybackService for background playback.
 */
class MusicPlayerActivity : AppCompatActivity() {
    private val musicRepository by lazy { MusicRepository(this) }
    private val sessionStore by lazy { ServerSessionStore(this) }
    private val serverRepository by lazy { ServerRepository(this) }
    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var coverImageView: ImageView
    private lateinit var titleTextView: TextView
    private lateinit var subtitleTextView: TextView
    private lateinit var queueTitleTextView: TextView
    private lateinit var playPauseButton: ImageButton
    private lateinit var previousButton: ImageButton
    private lateinit var nextButton: ImageButton
    private lateinit var favoriteButton: ImageButton
    private lateinit var elapsedTextView: TextView
    private lateinit var durationTextView: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var loadingIndicator: ProgressBar

    private lateinit var connection: ServerConnection
    private var libraryId: String? = null
    private var queueTitle: String = ""
    private var queueIds: ArrayList<String> = arrayListOf()
    private var queueTitles: ArrayList<String> = arrayListOf()
    private var queueSubtitles: ArrayList<String> = arrayListOf()
    private var queueImages: ArrayList<String> = arrayListOf()
    private var currentIndex: Int = 0

    private var player: Player? = null
    private var playbackService: MusicPlaybackService? = null
    private var isServiceBound = false
    private var currentPlaybackItemId: String = ""
    private var currentPlaybackPositionMs: Long = 0L
    private var shouldResumeAfterLoad = true
    private var isSeekingFromUser = false
    private var isCurrentFavorite = false
    private var favoriteRequestInFlight = false
    private var loadingAnimator: AnimatorSet? = null

    private val stateListener = MusicLibraryStateListener { state ->
        if (libraryId != null && state.currentLibraryId != null && state.currentLibraryId != libraryId) {
            Toast.makeText(this, getString(R.string.music_list_partition_changed), Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_BUFFERING) {
                startLoadingAnimation()
            } else {
                stopLoadingAnimation()
            }
            if (playbackState == Player.STATE_ENDED) {
                switchToIndex(currentIndex + 1, true)
            }
            syncControls()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            syncControls()
            if (isPlaying) {
                mainHandler.post(progressUpdater)
            } else {
                mainHandler.removeCallbacks(progressUpdater)
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val itemId = mediaItem?.mediaId
            if (!itemId.isNullOrBlank()) {
                currentPlaybackItemId = itemId
                currentIndex = queueIds.indexOf(itemId).takeIf { it >= 0 } ?: currentIndex
                MusicPlayerSessionStore.updateCurrentItem(itemId)
            }
            syncMetadataFromCurrentItem(mediaItem)
            syncControls()
        }

        override fun onPlayerError(error: PlaybackException) {
            Toast.makeText(
                this@MusicPlayerActivity,
                userFriendlyPlaybackErrorMessage(error),
                Toast.LENGTH_SHORT
            ).show()
            syncControls()
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val localBinder = service as? MusicPlaybackService.LocalBinder ?: return
            playbackService = localBinder.getService()
            isServiceBound = true
            attachPlayer(playbackService?.getPlayer())

            val currentPlayer = player
            val requestedItemId = queueIds.getOrNull(currentIndex).orEmpty()
            val activeItem = currentPlayer?.currentMediaItem
            val isRequestedItemAlreadyActive = requestedItemId.isNotBlank() && activeItem?.mediaId == requestedItemId
            if (currentPlayer == null || currentPlayer.mediaItemCount == 0 || !isRequestedItemAlreadyActive) {
                switchToIndex(currentIndex, true, resetPosition = false)
            } else {
                val activeIndex = activeItem?.mediaId?.let { queueIds.indexOf(it) } ?: -1
                if (activeIndex >= 0) {
                    currentIndex = activeIndex
                    MusicPlayerSessionStore.updateCurrentItem(activeItem?.mediaId)
                }
                syncMetadataFromCurrentItem(activeItem)
                syncControls()
                refreshFavoriteState(activeItem?.mediaId)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            detachPlayer()
            playbackService = null
            isServiceBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EdgeToEdgeHelper.enable(this, lightSystemBars = GlobalThemeStore(this).loadTheme().lightSystemBars)
        setContentView(R.layout.activity_music_player)
        supportActionBar?.hide()
        GlobalThemeManager.apply(this)

        connection = requireServerConnection(sessionStore, serverRepository) ?: return
        libraryId = intent.getStringExtra(EXTRA_LIBRARY_ID)
        queueTitle = intent.getStringExtra(EXTRA_QUEUE_TITLE).orEmpty()
        queueIds = intent.getStringArrayListExtra(EXTRA_QUEUE_IDS) ?: arrayListOf()
        queueTitles = intent.getStringArrayListExtra(EXTRA_QUEUE_TITLES) ?: arrayListOf()
        queueSubtitles = intent.getStringArrayListExtra(EXTRA_QUEUE_SUBTITLES) ?: arrayListOf()
        queueImages = intent.getStringArrayListExtra(EXTRA_QUEUE_IMAGES) ?: arrayListOf()
        currentIndex = intent.getIntExtra(EXTRA_QUEUE_INDEX, 0).coerceIn(0, (queueIds.lastIndex).coerceAtLeast(0))
        MusicPlayerSessionStore.record(
            connection = connection,
            libraryId = libraryId,
            queueTitle = queueTitle,
            queueIds = queueIds,
            queueTitles = queueTitles,
            queueSubtitles = queueSubtitles,
            queueImages = queueImages,
            queueIndex = currentIndex
        )

        if (queueIds.isEmpty()) {
            Toast.makeText(this, getString(R.string.server_data_missing), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        coverImageView = findViewById(R.id.musicPlayerCover)
        titleTextView = findViewById(R.id.musicPlayerTitle)
        subtitleTextView = findViewById(R.id.musicPlayerSubtitle)
        queueTitleTextView = findViewById(R.id.musicPlayerQueueTitle)
        playPauseButton = findViewById(R.id.musicPlayerPlayPauseButton)
        previousButton = findViewById(R.id.musicPlayerPreviousButton)
        nextButton = findViewById(R.id.musicPlayerNextButton)
        favoriteButton = findViewById(R.id.musicPlayerFavoriteButton)
        elapsedTextView = findViewById(R.id.musicPlayerElapsedText)
        durationTextView = findViewById(R.id.musicPlayerDurationText)
        seekBar = findViewById(R.id.musicPlayerSeekBar)
        loadingIndicator = findViewById(R.id.musicPlayerLoadingIndicator)

        findViewById<ImageButton>(R.id.musicPlayerBackButton).setDebouncedClickListener { finish() }
        playPauseButton.setDebouncedClickListener { togglePlayPause() }
        previousButton.setDebouncedClickListener { switchToIndex(currentIndex - 1, true) }
        nextButton.setDebouncedClickListener { switchToIndex(currentIndex + 1, true) }
        favoriteButton.setDebouncedClickListener { toggleFavorite() }
        queueTitleTextView.text = queueTitle
        updateFavoriteIcon()

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    elapsedTextView.text = formatMillis(progress.toLong())
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isSeekingFromUser = true
                stopLoadingAnimation()
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val target = seekBar?.progress?.toLong() ?: 0L
                player?.seekTo(target)
                isSeekingFromUser = false
            }
        })

        requestNotificationPermissionIfNeeded()
    }

    override fun onStart() {
        super.onStart()
        MusicLibraryRepository.subscribe(stateListener)

        val serviceIntent = Intent(this, MusicPlaybackService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onStop() {
        MusicLibraryRepository.unsubscribe(stateListener)
        detachPlayer()
        if (isServiceBound) {
            unbindService(serviceConnection)
            isServiceBound = false
        }
        playbackService = null
        super.onStop()
    }

    private fun attachPlayer(servicePlayer: Player?) {
        if (servicePlayer == null || player === servicePlayer) return
        detachPlayer()
        player = servicePlayer
        servicePlayer.addListener(playerListener)
    }

    private fun detachPlayer() {
        val currentPlayer = player
        if (currentPlayer != null) {
            reportPlaybackProgress(currentPlayer.currentPosition)
            currentPlayer.removeListener(playerListener)
        }
        mainHandler.removeCallbacks(progressUpdater)
        player = null
    }

    private fun switchToIndex(index: Int, playWhenReady: Boolean, resetPosition: Boolean = true) {
        if (index !in queueIds.indices) {
            syncControls()
            return
        }

        val currentPlayer = player ?: return
        val previousPosition = currentPlayer.currentPosition.takeIf { it > 0L } ?: currentPlaybackPositionMs
        reportPlaybackProgress(previousPosition)

        currentIndex = index
        shouldResumeAfterLoad = playWhenReady
        if (resetPosition) {
            currentPlaybackPositionMs = 0L
        }
        syncStaticMetadata()
        favoriteButton.isEnabled = false
        startLoadingAnimation()

        AppExecutors.io.execute {
            val result = musicRepository.fetchAudioPlayback(connection, queueIds[index])
            runOnUiThread {
                result.onSuccess { playback ->
                    currentPlaybackItemId = playback.itemId
                    currentPlaybackPositionMs = if (resetPosition) {
                        playback.playbackPositionMs
                    } else {
                        currentPlaybackPositionMs
                    }
                    titleTextView.text = playback.title
                    subtitleTextView.text = playback.subtitle
                    bindCover(playback.coverImageUrl)
                    isCurrentFavorite = playback.isFavorite
                    favoriteButton.isEnabled = true
                    updateFavoriteIcon()

                    val metadata = MediaMetadata.Builder()
                        .setTitle(playback.title)
                        .setArtist(playback.subtitle)
                        .setArtworkUri(playback.coverImageUrl?.toUri())
                        .build()
                    val mediaItem = MediaItem.Builder()
                        .setMediaId(playback.itemId)
                        .setUri(playback.playbackUrl)
                        .setMediaMetadata(metadata)
                        .build()

                    currentPlayer.setMediaItem(mediaItem)
                    currentPlayer.prepare()
                    currentPlayer.seekTo(currentPlaybackPositionMs)
                    currentPlayer.playWhenReady = shouldResumeAfterLoad
                    syncControls()
                }.onFailure { error ->
                    stopLoadingAnimation()
                    favoriteButton.isEnabled = true
                    Toast.makeText(
                        this,
                        userFriendlyErrorMessage(error, R.string.player_error),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun syncStaticMetadata() {
        titleTextView.text = queueTitles.getOrNull(currentIndex).orEmpty().ifBlank { getString(R.string.untitled_media) }
        subtitleTextView.text = queueSubtitles.getOrNull(currentIndex).orEmpty()
        bindCover(queueImages.getOrNull(currentIndex).orEmpty().ifBlank { null })
    }

    private fun syncMetadataFromCurrentItem(mediaItem: MediaItem?) {
        if (mediaItem == null) {
            syncStaticMetadata()
            return
        }
        val metadata = mediaItem.mediaMetadata
        val title = metadata.title?.toString().orEmpty()
        val artist = metadata.artist?.toString().orEmpty()
        titleTextView.text = title.ifBlank {
            queueTitles.getOrNull(currentIndex).orEmpty().ifBlank { getString(R.string.untitled_media) }
        }
        subtitleTextView.text = artist.ifBlank { queueSubtitles.getOrNull(currentIndex).orEmpty() }
        val artworkUrl = metadata.artworkUri?.toString().orEmpty()
        bindCover(artworkUrl.ifBlank { queueImages.getOrNull(currentIndex).orEmpty().ifBlank { null } })
    }

    private fun bindCover(url: String?) {
        if (url.isNullOrBlank()) {
            AppIconPlaceholder.apply(coverImageView, cornerRadiusDp = 24f)
            return
        }
        EmbyImageLoader.load(
            imageView = coverImageView,
            url = url,
            token = connection.accessToken,
            onFailure = {
                AppIconPlaceholder.apply(coverImageView, cornerRadiusDp = 24f)
            }
        )
    }

    private fun toggleFavorite() {
        val itemId = currentPlaybackItemId.ifBlank { queueIds.getOrNull(currentIndex).orEmpty() }
        if (itemId.isBlank() || favoriteRequestInFlight) return

        val target = !isCurrentFavorite
        favoriteRequestInFlight = true
        favoriteButton.isEnabled = false
        AppExecutors.io.execute {
            val result = musicRepository.setFavoriteState(connection, itemId, target)
            runOnUiThread {
                favoriteRequestInFlight = false
                favoriteButton.isEnabled = true
                result.onSuccess {
                    isCurrentFavorite = target
                    updateFavoriteIcon()
                    Toast.makeText(
                        this,
                        if (isCurrentFavorite) getString(R.string.favorite_added) else getString(R.string.favorite_removed),
                        Toast.LENGTH_SHORT
                    ).show()
                }.onFailure { error ->
                    Toast.makeText(
                        this,
                        userFriendlyErrorMessage(error, R.string.favorite_update_failed),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun refreshFavoriteState(itemId: String?) {
        if (itemId.isNullOrBlank()) return
        favoriteButton.isEnabled = false
        AppExecutors.io.execute {
            val result = musicRepository.fetchAudioPlayback(connection, itemId)
            runOnUiThread {
                favoriteButton.isEnabled = true
                result.onSuccess { playback ->
                    if (currentPlaybackItemId == playback.itemId || player?.currentMediaItem?.mediaId == playback.itemId) {
                        isCurrentFavorite = playback.isFavorite
                        updateFavoriteIcon()
                    }
                }
            }
        }
    }

    private fun updateFavoriteIcon() {
        favoriteButton.setImageResource(
            if (isCurrentFavorite) R.drawable.ic_favorite_heart_filled else R.drawable.ic_favorite_heart_outline
        )
        favoriteButton.clearColorFilter()
    }

    private fun togglePlayPause() {
        val currentPlayer = player ?: return
        if (currentPlayer.isPlaying) {
            currentPlayer.pause()
            reportPlaybackProgress(currentPlayer.currentPosition)
        } else {
            currentPlayer.play()
        }
        syncControls()
    }

    private fun syncControls() {
        val currentPlayer = player
        val isPlaying = currentPlayer?.isPlaying == true
        playPauseButton.setImageResource(
            if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        )
        previousButton.isEnabled = currentIndex > 0
        nextButton.isEnabled = currentIndex < queueIds.lastIndex
        if (currentPlayer != null && !isSeekingFromUser) {
            val duration = currentPlayer.duration.takeIf { it > 0 } ?: 0L
            val position = currentPlayer.currentPosition.coerceAtLeast(0L)
            seekBar.max = duration.toInt().coerceAtLeast(1)
            seekBar.progress = position.toInt().coerceAtMost(seekBar.max)
            elapsedTextView.text = formatMillis(position)
            durationTextView.text = formatMillis(duration)
        }
    }

    private fun reportPlaybackProgress(positionMs: Long) {
        if (currentPlaybackItemId.isBlank()) return
        AppExecutors.io.execute {
            musicRepository.updatePlaybackProgress(connection, currentPlaybackItemId, positionMs)
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            return
        }
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            NOTIFICATION_PERMISSION_REQUEST_CODE
        )
    }

    private fun formatMillis(positionMs: Long): String {
        val totalSeconds = (positionMs / 1000).coerceAtLeast(0L)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }
    
    private fun startLoadingAnimation() {
        if (loadingAnimator?.isRunning == true) return
        loadingIndicator.visibility = View.VISIBLE
        seekBar.alpha = 0.56f
        seekBar.scaleY = 0.92f
        val alphaAnimator = ObjectAnimator.ofFloat(seekBar, View.ALPHA, 0.52f, 1f).apply {
            duration = 760L
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
        }
        val scaleAnimator = ObjectAnimator.ofFloat(seekBar, View.SCALE_Y, 0.9f, 1.08f).apply {
            duration = 760L
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
        }
        loadingAnimator = AnimatorSet().apply {
            playTogether(alphaAnimator, scaleAnimator)
            start()
        }
    }
    
    private fun stopLoadingAnimation() {
        loadingAnimator?.cancel()
        loadingAnimator = null
        seekBar.alpha = 1f
        seekBar.scaleY = 1f
        loadingIndicator.visibility = View.GONE
    }

    private val progressUpdater = object : Runnable {
        override fun run() {
            syncControls()
            mainHandler.postDelayed(this, 500L)
        }
    }

    companion object {
        const val EXTRA_LIBRARY_ID = "extra_library_id"
        const val EXTRA_QUEUE_TITLE = "extra_queue_title"
        const val EXTRA_QUEUE_IDS = "extra_queue_ids"
        const val EXTRA_QUEUE_TITLES = "extra_queue_titles"
        const val EXTRA_QUEUE_SUBTITLES = "extra_queue_subtitles"
        const val EXTRA_QUEUE_IMAGES = "extra_queue_images"
        const val EXTRA_QUEUE_INDEX = "extra_queue_index"

        private const val NOTIFICATION_PERMISSION_REQUEST_CODE = 2001
    }
}
