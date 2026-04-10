package com.liujiaming.embypro

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import java.util.concurrent.ExecutorService
import kotlin.math.abs

@androidx.annotation.OptIn(UnstableApi::class)
class PlayerActivity : AppCompatActivity() {
    private val networkExecutor: ExecutorService = AppExecutors.io
    private val embyApiService by lazy { EmbyApiService(this) }

    private lateinit var playerView: PlayerView
    private lateinit var loadingView: ProgressBar
    private lateinit var gestureText: TextView
    private lateinit var titleText: TextView
    private lateinit var moreButton: ImageButton
    private lateinit var playPauseButton: ImageButton
    private lateinit var centerTimeText: TextView
    private lateinit var topBar: View

    private var player: ExoPlayer? = null
    private lateinit var playbackUrl: String
    private lateinit var accessToken: String
    private lateinit var title: String
    private lateinit var baseUrl: String
    private lateinit var userId: String
    private lateinit var itemId: String
    private var startPositionMs: Long = 0L
    private var currentSpeedIndex = 0
    private val speeds = listOf(1.0f, 1.25f, 1.5f, 2.0f)
    private var playbackPosition = 0L
    private var playWhenReady = true
    private var manualVideoRotation = 0
    private var currentVideoSize: VideoSize? = null
    private var playlistItemIds: ArrayList<String> = arrayListOf()
    private var playlistItemTitles: ArrayList<String> = arrayListOf()
    private var playlistIndex = -1
    private var isSwitchingItem = false
    private var isContinuousPlayEnabled = false

    private lateinit var audioManager: AudioManager
    private var currentBrightness = 0.5f
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var adjustingMode: AdjustMode? = null
    private var gestureStartPositionMs = 0L
    private var gestureSeekPositionMs = 0L
    private var gestureSeekActive = false
    private var longPressSeekDirection = 0
    private var longPressTriggered = false
    private var movedEnoughForGesture = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EdgeToEdgeHelper.enable(this, lightSystemBars = false)
        setContentView(R.layout.activity_player)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        supportActionBar?.hide()

        playbackUrl = intent.getStringExtra(EXTRA_PLAYBACK_URL).orEmpty()
        accessToken = intent.getStringExtra(EXTRA_ACCESS_TOKEN).orEmpty()
        title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        baseUrl = intent.getStringExtra(EXTRA_BASE_URL).orEmpty()
        userId = intent.getStringExtra(EXTRA_USER_ID).orEmpty()
        itemId = intent.getStringExtra(EXTRA_ITEM_ID).orEmpty()
        startPositionMs = intent.getLongExtra(EXTRA_START_POSITION_MS, 0L)
        playlistItemIds = intent.getStringArrayListExtra(EXTRA_PLAYLIST_ITEM_IDS) ?: arrayListOf()
        playlistItemTitles = intent.getStringArrayListExtra(EXTRA_PLAYLIST_ITEM_TITLES) ?: arrayListOf()
        playlistIndex = intent.getIntExtra(EXTRA_PLAYLIST_INDEX, -1)
        playbackPosition = savedInstanceState?.getLong(STATE_PLAYBACK_POSITION) ?: startPositionMs
        playWhenReady = savedInstanceState?.getBoolean(STATE_PLAY_WHEN_READY) ?: true
        currentSpeedIndex = savedInstanceState?.getInt(STATE_SPEED_INDEX) ?: 0
        manualVideoRotation = savedInstanceState?.getInt(STATE_MANUAL_ROTATION) ?: 0
        isContinuousPlayEnabled = savedInstanceState?.getBoolean(STATE_CONTINUOUS_PLAY) ?: false
        itemId = savedInstanceState?.getString(STATE_ITEM_ID) ?: itemId
        playbackUrl = savedInstanceState?.getString(STATE_PLAYBACK_URL) ?: playbackUrl
        title = savedInstanceState?.getString(STATE_TITLE) ?: title
        playlistIndex = savedInstanceState?.getInt(STATE_PLAYLIST_INDEX) ?: playlistIndex

        if (playbackUrl.isBlank()) {
            Toast.makeText(this, getString(R.string.playback_url_missing), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        currentBrightness = window.attributes.screenBrightness.takeIf { it >= 0f } ?: 0.5f

        playerView = findViewById(R.id.playerView)
        loadingView = findViewById(R.id.playerLoading)
        gestureText = findViewById(R.id.playerGestureText)
        titleText = findViewById(R.id.playerTitleText)
        moreButton = findViewById(R.id.playerMoreButton)
        playPauseButton = playerView.findViewById(androidx.media3.ui.R.id.exo_play_pause)
        centerTimeText = playerView.findViewById(R.id.playerCenterTimeText)
        topBar = findViewById<ImageButton>(R.id.playerBackButton).parent as View

        playerView.controllerShowTimeoutMs = 1800
        playerView.controllerHideOnTouch = true
        playerView.controllerAutoShow = false
        playerView.setShowFastForwardButton(false)
        playerView.setShowNextButton(false)
        playerView.setShowPreviousButton(false)
        playerView.setShowRewindButton(false)
        playPauseButton.setImageResource(R.drawable.exo_styled_controls_play)
        playPauseButton.imageTintList = null
        playPauseButton.setDebouncedClickListener {
            player?.play()
            playerView.hideController()
        }
        updatePlayPauseButtonLayout()

        titleText.text = title
        findViewById<ImageButton>(R.id.playerBackButton).setDebouncedClickListener { finish() }
        moreButton.setDebouncedClickListener { showPlayerMenu() }

        playerView.setOnTouchListener { _, event -> handleTouch(event) }
        playerView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> applyVideoRotation() }
        EdgeToEdgeHelper.applyInsets(topBar, applyTop = true)
        applyImmersivePlayback(playWhenReady)
    }

    override fun onStart() {
        super.onStart()
        activeInstance = this
        PlayerCache.cleanupExpiredPrefetch(this, protectedItemIds = setOf(itemId))
        initializePlayer()
    }

    override fun onStop() {
        super.onStop()
        if (activeInstance === this && !isInPictureInPictureMode) {
            activeInstance = null
        }
        if (!isInPictureInPictureMode) {
            releasePlayer()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (activeInstance === this) {
            activeInstance = null
        }
        releasePlayer()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        maybeEnterPictureInPicture(autoEnter = true)
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (isInPictureInPictureMode) {
            playerView.hideController()
            topBar.visibility = View.GONE
            loadingView.visibility = View.GONE
            gestureText.visibility = View.GONE
            updatePictureInPictureParams()
        } else {
            syncPlaybackControls()
        }
    }

    override fun finish() {
        setResult(
            RESULT_OK,
            Intent()
                .putExtra(RESULT_ITEM_ID, itemId)
                .putExtra(RESULT_PLAYLIST_INDEX, playlistIndex)
        )
        super.finish()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(STATE_PLAYBACK_POSITION, player?.currentPosition ?: playbackPosition)
        outState.putBoolean(STATE_PLAY_WHEN_READY, player?.playWhenReady ?: playWhenReady)
        outState.putInt(STATE_SPEED_INDEX, currentSpeedIndex)
        outState.putInt(STATE_MANUAL_ROTATION, manualVideoRotation)
        outState.putBoolean(STATE_CONTINUOUS_PLAY, isContinuousPlayEnabled)
        outState.putString(STATE_ITEM_ID, itemId)
        outState.putString(STATE_PLAYBACK_URL, playbackUrl)
        outState.putString(STATE_TITLE, title)
        outState.putInt(STATE_PLAYLIST_INDEX, playlistIndex)
    }

    private fun initializePlayer() {
        if (player != null) return

        val httpFactory = DefaultHttpDataSource.Factory().apply {
            if (accessToken.isNotBlank()) {
                setDefaultRequestProperties(mapOf("X-Emby-Token" to accessToken))
            }
        }
        val upstreamFactory = DefaultDataSource.Factory(this, httpFactory)

        val cacheFactory = CacheDataSource.Factory()
            .setCache(PlayerCache.get(this))
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        val exoPlayer = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(cacheFactory))
            .build()

        playerView.player = exoPlayer
        playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
        player = exoPlayer

        exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                loadingView.visibility = if (playbackState == Player.STATE_BUFFERING) View.VISIBLE else View.GONE
                if (playbackState == Player.STATE_READY && exoPlayer.playWhenReady) {
                    playerView.hideController()
                } else if (playbackState == Player.STATE_ENDED) {
                    handlePlaybackEnded()
                }
                syncPlaybackControls()
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                currentVideoSize = videoSize
                applyVideoRotation()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                syncPlaybackControls()
                if (isPlaying) {
                    playerView.hideController()
                }
                updatePictureInPictureParams()
            }

            override fun onPlayerError(error: PlaybackException) {
                Toast.makeText(this@PlayerActivity, error.message ?: getString(R.string.player_error), Toast.LENGTH_SHORT).show()
            }
        })

        exoPlayer.setMediaItem(MediaItem.fromUri(Uri.parse(playbackUrl)))
        exoPlayer.prepare()
        exoPlayer.seekTo(playbackPosition)
        exoPlayer.playbackParameters = PlaybackParameters(speeds[currentSpeedIndex])
        exoPlayer.playWhenReady = playWhenReady
        PlayerCache.markPlayed(this, itemId)
        prefetchUpcomingVideosIfNeeded()
        applyVideoRotation()
        syncPlaybackControls()
    }

    private fun releasePlayer() {
        if (player == null) return
        playbackPosition = player?.currentPosition ?: playbackPosition
        playWhenReady = player?.playWhenReady ?: playWhenReady
        reportPlaybackProgress(playbackPosition)
        player?.release()
        player = null
    }

    private fun applySpeed(index: Int) {
        currentSpeedIndex = index.coerceIn(speeds.indices)
        val speed = speeds[currentSpeedIndex]
        player?.playbackParameters = PlaybackParameters(speed)
        showGestureLabel(getString(R.string.playback_speed_label, speed), 900)
    }

    private fun toggleContinuousPlay() {
        isContinuousPlayEnabled = !isContinuousPlayEnabled
        showGestureLabel(
            getString(if (isContinuousPlayEnabled) R.string.continuous_enabled else R.string.continuous_disabled),
            900
        )
        if (isContinuousPlayEnabled) {
            prefetchUpcomingVideosIfNeeded()
        }
    }

    private fun showPlayerMenu() {
        val popupMenu = PopupMenu(this, moreButton)
        popupMenu.menuInflater.inflate(R.menu.player_more_menu, popupMenu.menu)
        popupMenu.menu.findItem(R.id.actionToggleContinuous)?.title =
            getString(if (isContinuousPlayEnabled) R.string.continuous_on else R.string.continuous_off)
        popupMenu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.actionToggleContinuous -> {
                    toggleContinuousPlay()
                    true
                }

                R.id.actionRotateVideo -> {
                    rotateVideo()
                    true
                }

                R.id.actionPictureInPicture -> {
                    maybeEnterPictureInPicture(autoEnter = false)
                    true
                }

                R.id.actionSpeed1x -> {
                    applySpeed(0)
                    true
                }

                R.id.actionSpeed125x -> {
                    applySpeed(1)
                    true
                }

                R.id.actionSpeed15x -> {
                    applySpeed(2)
                    true
                }

                R.id.actionSpeed2x -> {
                    applySpeed(3)
                    true
                }

                else -> false
            }
        }
        popupMenu.show()
    }

    private fun rotateVideo() {
        manualVideoRotation = (manualVideoRotation + 90) % 360
        applyVideoRotation()
        showGestureLabel(getString(R.string.rotate_video_label, manualVideoRotation), 900)
    }

    private fun handleTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                initialTouchX = event.x
                initialTouchY = event.y
                adjustingMode = null
                gestureStartPositionMs = player?.currentPosition ?: 0L
                gestureSeekActive = false
                gestureSeekPositionMs = gestureStartPositionMs
                longPressTriggered = false
                movedEnoughForGesture = false
                longPressSeekDirection = if (initialTouchX < playerView.width / 2f) -1 else 1
                gestureText.removeCallbacks(longPressStartRunnable)
                gestureText.postDelayed(longPressStartRunnable, ViewConfiguration.getLongPressTimeout().toLong())
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - initialTouchX
                val dy = event.y - initialTouchY
                if (longPressTriggered) {
                    return true
                }
                if (abs(dx) > 24f || abs(dy) > 24f) {
                    movedEnoughForGesture = true
                    gestureText.removeCallbacks(longPressStartRunnable)
                }
                if (adjustingMode == null) {
                    if (abs(dx) > abs(dy) && abs(dx) > 24f) {
                        adjustingMode = AdjustMode.SEEK
                    } else if (abs(dy) > abs(dx) && abs(dy) > 24f) {
                        adjustingMode = if (isPortraitPlayback()) {
                            AdjustMode.SWITCH_ITEM
                        } else {
                            if (initialTouchX < playerView.width / 2f) {
                                AdjustMode.BRIGHTNESS
                            } else {
                                AdjustMode.VOLUME
                            }
                        }
                    }
                }

                when (adjustingMode) {
                    AdjustMode.SEEK -> adjustSeek(dx / playerView.width)
                    AdjustMode.SWITCH_ITEM -> previewSwitchItem(dy)
                    AdjustMode.LONG_PRESS_SEEK -> Unit
                    AdjustMode.BRIGHTNESS -> adjustBrightness(-dy / playerView.height)
                    AdjustMode.VOLUME -> adjustVolume(-dy / playerView.height)
                    null -> Unit
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                gestureText.removeCallbacks(longPressStartRunnable)
                stopLongPressSeek()
                if (event.actionMasked == MotionEvent.ACTION_UP && !movedEnoughForGesture && !longPressTriggered && adjustingMode == null) {
                    handleSingleTap()
                    return true
                }
                if (adjustingMode == AdjustMode.SEEK && gestureSeekActive) {
                    player?.seekTo(gestureSeekPositionMs)
                } else if (adjustingMode == AdjustMode.SWITCH_ITEM) {
                    completeSwitchItem(event.y - initialTouchY)
                }
                gestureText.postDelayed({ gestureText.visibility = View.GONE }, 500)
                adjustingMode = null
                gestureSeekActive = false
            }
        }
        return adjustingMode != null
    }

    private fun adjustSeek(deltaFraction: Float) {
        val duration = player?.duration?.takeIf { it > 0 } ?: return
        val deltaMs = (duration * deltaFraction).toLong()
        gestureSeekPositionMs = (gestureStartPositionMs + deltaMs).coerceIn(0L, duration)
        gestureSeekActive = true
        showGestureLabel(getString(R.string.seek_position_label, formatMillis(gestureSeekPositionMs)), 700)
    }

    private fun adjustBrightness(delta: Float) {
        currentBrightness = (currentBrightness + delta).coerceIn(0.05f, 1.0f)
        window.attributes = window.attributes.apply { screenBrightness = currentBrightness }
        showGestureLabel(getString(R.string.brightness_label, (currentBrightness * 100).toInt()))
    }

    private fun adjustVolume(delta: Float) {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val target = (current + delta * max).toInt().coerceIn(0, max)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
        showGestureLabel(getString(R.string.volume_label, (target * 100) / max))
    }

    private fun previewSwitchItem(dy: Float) {
        val threshold = playerView.height * 0.12f
        if (dy <= -threshold) {
            showGestureLabel(getString(R.string.next_video_hint), 400)
        } else if (dy >= threshold) {
            showGestureLabel(getString(R.string.previous_video_hint), 400)
        }
    }

    private fun completeSwitchItem(totalDy: Float) {
        val threshold = playerView.height * 0.12f
        when {
            totalDy <= -threshold -> switchPlaylistItem(1)
            totalDy >= threshold -> switchPlaylistItem(-1)
        }
    }

    private fun startLongPressSeek() {
        longPressTriggered = true
        adjustingMode = AdjustMode.LONG_PRESS_SEEK
        playerView.hideController()
        performLongPressSeekTick()
    }

    private fun performLongPressSeekTick() {
        val currentPlayer = player ?: return
        val duration = currentPlayer.duration.takeIf { it > 0 } ?: return
        val targetPosition = (currentPlayer.currentPosition + longPressSeekDirection * LONG_PRESS_SEEK_MS)
            .coerceIn(0L, duration)
        currentPlayer.seekTo(targetPosition)
        val label = if (longPressSeekDirection > 0) {
            getString(R.string.long_press_fast_forward, formatMillis(targetPosition))
        } else {
            getString(R.string.long_press_rewind, formatMillis(targetPosition))
        }
        showGestureLabel(label, 250)
        gestureText.removeCallbacks(longPressSeekRunnable)
        gestureText.postDelayed(longPressSeekRunnable, LONG_PRESS_REPEAT_MS)
    }

    private fun stopLongPressSeek() {
        gestureText.removeCallbacks(longPressSeekRunnable)
        if (longPressTriggered) {
            gestureText.postDelayed({ gestureText.visibility = View.GONE }, 300)
        }
        longPressTriggered = false
    }

    private fun handleSingleTap() {
        val currentPlayer = player ?: return
        if (currentPlayer.isPlaying) {
            currentPlayer.pause()
            reportPlaybackProgress(currentPlayer.currentPosition)
            playerView.showController()
        } else {
            currentPlayer.play()
            playerView.hideController()
        }
        syncPlaybackControls()
    }

    private fun handlePictureInPictureControl(action: String) {
        val currentPlayer = player ?: return
        when (action) {
            ACTION_PIP_PAUSE -> currentPlayer.pause()
            ACTION_PIP_PLAY,
            ACTION_PIP_TOGGLE -> {
                if (currentPlayer.isPlaying) {
                    currentPlayer.pause()
                } else {
                    currentPlayer.play()
                }
            }
        }
        syncPlaybackControls()
        updatePictureInPictureParams()
    }

    private fun switchPlaylistItem(direction: Int) {
        if (isSwitchingItem) return
        if (playlistItemIds.isEmpty() || playlistIndex !in playlistItemIds.indices) {
            showGestureLabel(getString(R.string.playlist_unavailable), 900)
            return
        }

        val targetIndex = playlistIndex + direction
        if (targetIndex !in playlistItemIds.indices) {
            showGestureLabel(
                getString(if (direction > 0) R.string.no_next_video else R.string.no_previous_video),
                900
            )
            return
        }

        isSwitchingItem = true
        loadingView.visibility = View.VISIBLE
        val targetItemId = playlistItemIds[targetIndex]
        networkExecutor.execute {
            val result = embyApiService.fetchVideoDetail(baseUrl, userId, accessToken, targetItemId)
            runOnUiThread {
                isSwitchingItem = false
                result.onSuccess { detail ->
                    playlistIndex = targetIndex
                    itemId = detail.itemId
                    playbackUrl = detail.playbackUrl.orEmpty()
                    title = detail.title
                    titleText.text = title
                    playbackPosition = detail.playbackPositionTicks / 10_000L
                    playWhenReady = true
                    swapToMedia(detail.playbackUrl.orEmpty())
                    PlayerCache.markPlayed(this, itemId)
                    prefetchUpcomingVideosIfNeeded()
                }.onFailure { error ->
                    loadingView.visibility = View.GONE
                    Toast.makeText(
                        this,
                        error.message ?: getString(R.string.player_error),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun handlePlaybackEnded() {
        if (isContinuousPlayEnabled && playlistItemIds.isNotEmpty() && playlistIndex in playlistItemIds.indices && playlistIndex < playlistItemIds.lastIndex) {
            switchPlaylistItem(1)
        } else if (!isContinuousPlayEnabled) {
            player?.let { currentPlayer ->
                reportPlaybackProgress(0L)
                playbackPosition = 0L
                currentPlayer.seekTo(0L)
                currentPlayer.playWhenReady = true
                currentPlayer.play()
                playerView.hideController()
                syncPlaybackControls()
            }
        } else {
            finish()
        }
    }

    private fun swapToMedia(url: String) {
        if (url.isBlank()) {
            loadingView.visibility = View.GONE
            Toast.makeText(this, getString(R.string.playback_url_missing), Toast.LENGTH_SHORT).show()
            return
        }
        val currentPlayer = player ?: run {
            playbackUrl = url
            initializePlayer()
            return
        }
        playbackUrl = url
        currentPlayer.setMediaItem(MediaItem.fromUri(Uri.parse(url)))
        currentPlayer.prepare()
        currentPlayer.seekTo(playbackPosition.coerceAtLeast(0L))
        currentPlayer.playbackParameters = PlaybackParameters(speeds[currentSpeedIndex])
        currentPlayer.playWhenReady = true
        playerView.hideController()
        syncPlaybackControls()
    }

    private fun prefetchUpcomingVideosIfNeeded() {
        if (!isContinuousPlayEnabled) return
        if (playlistItemIds.isEmpty() || playlistIndex !in playlistItemIds.indices) return

        val nextIds = ((playlistIndex + 1)..minOf(playlistIndex + 5, playlistItemIds.lastIndex))
            .map { playlistItemIds[it] }
        if (nextIds.isEmpty()) return

        PlayerCache.cleanupExpiredPrefetch(this, protectedItemIds = nextIds.toSet() + itemId)
        networkExecutor.execute {
            val preloadTargets = mutableListOf<Pair<String, String>>()
            nextIds.forEach { nextItemId ->
                val detail = embyApiService.fetchVideoDetail(baseUrl, userId, accessToken, nextItemId).getOrNull()
                val url = detail?.playbackUrl.orEmpty()
                if (url.isNotBlank()) {
                    preloadTargets += nextItemId to url
                }
            }
            PlayerCache.prefetchVideos(this, accessToken, preloadTargets)
        }
    }

    private fun showGestureLabel(text: String, durationMs: Long = 500) {
        gestureText.text = text
        gestureText.visibility = View.VISIBLE
        gestureText.removeCallbacks(hideGestureRunnable)
        gestureText.postDelayed(hideGestureRunnable, durationMs)
    }

    private fun isPortraitPlayback(): Boolean {
        return resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    }

    private fun applyVideoRotation() {
        val surfaceView = playerView.videoSurfaceView ?: return
        surfaceView.rotation = manualVideoRotation.toFloat()

        if (manualVideoRotation % 180 == 0) {
            surfaceView.scaleX = 1f
            surfaceView.scaleY = 1f
            return
        }

        val containerWidth = playerView.width.toFloat()
        val containerHeight = playerView.height.toFloat()
        if (containerWidth <= 0f || containerHeight <= 0f) return

        val scale = minOf(containerWidth / containerHeight, containerHeight / containerWidth)
        surfaceView.scaleX = scale
        surfaceView.scaleY = scale
    }

    private fun syncPlaybackControls() {
        val currentPlayer = player ?: return
        applyImmersivePlayback(currentPlayer.isPlaying)
        val shouldShowResumeButton = !currentPlayer.isPlaying &&
            currentPlayer.playbackState != Player.STATE_BUFFERING &&
            currentPlayer.playbackState != Player.STATE_ENDED
        if (shouldShowResumeButton) {
            playPauseButton.setImageResource(R.drawable.exo_styled_controls_play)
            playPauseButton.imageTintList = null
            updateCenterTimeText()
            centerTimeText.visibility = View.VISIBLE
            centerTimeText.removeCallbacks(centerTimeTicker)
            centerTimeText.post(centerTimeTicker)
        }
        playPauseButton.visibility = if (shouldShowResumeButton) View.VISIBLE else View.INVISIBLE
        centerTimeText.visibility = if (shouldShowResumeButton) View.VISIBLE else View.INVISIBLE
        if (!shouldShowResumeButton) {
            centerTimeText.removeCallbacks(centerTimeTicker)
            playerView.hideController()
        }
    }

    private fun updatePlayPauseButtonLayout() {
        val params = playPauseButton.layoutParams as? FrameLayout.LayoutParams ?: return
        params.gravity = android.view.Gravity.CENTER
        params.marginStart = 0
        params.bottomMargin = 0
        playPauseButton.layoutParams = params
    }

    private fun applyImmersivePlayback(isPlaying: Boolean) {
        if (isInPictureInPictureMode) return
        topBar.visibility = if (isPlaying) View.GONE else View.VISIBLE
        val controller = WindowCompat.getInsetsController(window, window.decorView) ?: return
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (isPlaying) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun reportPlaybackProgress(positionMs: Long) {
        if (baseUrl.isBlank() || userId.isBlank() || accessToken.isBlank() || itemId.isBlank()) return
        networkExecutor.execute {
            embyApiService.updatePlaybackProgress(
                baseUrl = baseUrl,
                userId = userId,
                accessToken = accessToken,
                itemId = itemId,
                playbackPositionMs = positionMs
            )
        }
    }

    private fun formatMillis(positionMs: Long): String {
        val totalSeconds = positionMs / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    private fun updateCenterTimeText() {
        val currentPlayer = player ?: return
        val position = currentPlayer.currentPosition.coerceAtLeast(0L)
        val duration = currentPlayer.duration.takeIf { it > 0 } ?: 0L
        centerTimeText.text = "${formatMillis(position)} / ${formatMillis(duration)}"
    }

    private fun maybeEnterPictureInPicture(autoEnter: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            if (!autoEnter) {
                Toast.makeText(this, getString(R.string.picture_in_picture_not_supported), Toast.LENGTH_SHORT).show()
            }
            return
        }
        if (!packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            if (!autoEnter) {
                Toast.makeText(this, getString(R.string.picture_in_picture_not_supported), Toast.LENGTH_SHORT).show()
            }
            return
        }
        val currentPlayer = player ?: return
        if (autoEnter && !currentPlayer.isPlaying) return

        val videoSize = currentVideoSize
        val ratio = when {
            videoSize != null && videoSize.width > 0 && videoSize.height > 0 ->
                Rational(videoSize.width, videoSize.height)
            else -> Rational(16, 9)
        }
        val params = PictureInPictureParams.Builder()
            .setAspectRatio(ratio)
            .setActions(buildPictureInPictureActions(player?.isPlaying == true))
            .build()
        enterPictureInPictureMode(params)
    }

    private fun updatePictureInPictureParams() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (!isInPictureInPictureMode) return
        val videoSize = currentVideoSize
        val ratio = when {
            videoSize != null && videoSize.width > 0 && videoSize.height > 0 ->
                Rational(videoSize.width, videoSize.height)
            else -> Rational(16, 9)
        }
        setPictureInPictureParams(
            PictureInPictureParams.Builder()
                .setAspectRatio(ratio)
                .setActions(buildPictureInPictureActions(player?.isPlaying == true))
                .build()
        )
    }

    private fun buildPictureInPictureActions(isPlaying: Boolean): List<RemoteAction> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return emptyList()
        val action = if (isPlaying) ACTION_PIP_PAUSE else ACTION_PIP_PLAY
        val titleRes = if (isPlaying) android.R.string.cancel else android.R.string.ok
        val iconRes = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val intent = Intent(this, PlayerPipActionReceiver::class.java).setAction(action)
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            if (isPlaying) 201 else 202,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return listOf(
            RemoteAction(
                Icon.createWithResource(this, iconRes),
                getString(titleRes),
                getString(titleRes),
                pendingIntent
            )
        )
    }

    private enum class AdjustMode {
        SEEK,
        SWITCH_ITEM,
        LONG_PRESS_SEEK,
        BRIGHTNESS,
        VOLUME
    }

    private val hideGestureRunnable = Runnable { gestureText.visibility = View.GONE }
    private val longPressStartRunnable = Runnable { startLongPressSeek() }
    private val longPressSeekRunnable = Runnable { performLongPressSeekTick() }
    private val centerTimeTicker = object : Runnable {
        override fun run() {
            updateCenterTimeText()
            centerTimeText.postDelayed(this, 1000L)
        }
    }

    companion object {
        const val ACTION_PIP_PLAY = "com.liujiaming.embypro.action.PIP_PLAY"
        const val ACTION_PIP_PAUSE = "com.liujiaming.embypro.action.PIP_PAUSE"
        const val ACTION_PIP_TOGGLE = "com.liujiaming.embypro.action.PIP_TOGGLE"
        const val EXTRA_PLAYBACK_URL = "extra_playback_url"
        const val EXTRA_ACCESS_TOKEN = "extra_access_token"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_BASE_URL = "extra_base_url"
        const val EXTRA_USER_ID = "extra_user_id"
        const val EXTRA_ITEM_ID = "extra_item_id"
        const val EXTRA_START_POSITION_MS = "extra_start_position_ms"
        const val EXTRA_PLAYLIST_ITEM_IDS = "extra_playlist_item_ids"
        const val EXTRA_PLAYLIST_ITEM_TITLES = "extra_playlist_item_titles"
        const val EXTRA_PLAYLIST_INDEX = "extra_playlist_index"
        const val RESULT_ITEM_ID = "result_item_id"
        const val RESULT_PLAYLIST_INDEX = "result_playlist_index"
        private const val STATE_PLAYBACK_POSITION = "state_playback_position"
        private const val STATE_PLAY_WHEN_READY = "state_play_when_ready"
        private const val STATE_SPEED_INDEX = "state_speed_index"
        private const val STATE_MANUAL_ROTATION = "state_manual_rotation"
        private const val STATE_CONTINUOUS_PLAY = "state_continuous_play"
        private const val STATE_ITEM_ID = "state_item_id"
        private const val STATE_PLAYBACK_URL = "state_playback_url"
        private const val STATE_TITLE = "state_title"
        private const val STATE_PLAYLIST_INDEX = "state_playlist_index"
        private const val LONG_PRESS_SEEK_MS = 8_000L
        private const val LONG_PRESS_REPEAT_MS = 350L

        @Volatile
        private var activeInstance: PlayerActivity? = null

        fun handlePipControlAction(action: String) {
            activeInstance?.runOnUiThread {
                activeInstance?.handlePictureInPictureControl(action)
            }
        }
    }
}
