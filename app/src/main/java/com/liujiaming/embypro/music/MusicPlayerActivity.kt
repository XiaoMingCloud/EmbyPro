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
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewPropertyAnimator
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import android.content.res.ColorStateList
import androidx.appcompat.app.AlertDialog
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
 * Provides play/pause, seek, previous/next controls, favorite toggle, and lyrics display.
 * Integrates with MusicPlaybackService for background playback.
 */
class MusicPlayerActivity : AppCompatActivity() {
    private val musicRepository by lazy { MusicRepository(this) }
    private val mediaRepository by lazy { MediaRepository(this) }
    private val offlineCache by lazy { MusicOfflineCache(this) }
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
    private lateinit var playbackModeButton: ImageButton
    private lateinit var sleepTimerButton: ImageButton
    private lateinit var lyricsQuickButton: ImageButton
    private lateinit var songQuickButton: ImageButton
    private lateinit var favoriteButton: ImageButton
    private lateinit var cacheButton: ImageButton
    private lateinit var deleteButton: ImageButton
    private lateinit var elapsedTextView: TextView
    private lateinit var durationTextView: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var loadingIndicator: ProgressBar
    private lateinit var lyricsScrollView: ScrollView
    private lateinit var lyricsContainer: LinearLayout
    private lateinit var lyricsButton: ImageButton
    private lateinit var songTabButton: TextView
    private lateinit var lyricsTabButton: TextView
    private lateinit var songPage: LinearLayout
    private lateinit var lyricsPage: LinearLayout

    private lateinit var connection: ServerConnection
    private var libraryId: String? = null
    private var queueTitle: String = ""
    private var queueIds: ArrayList<String> = arrayListOf()
    private var queueTitles: ArrayList<String> = arrayListOf()
    private var queueSubtitles: ArrayList<String> = arrayListOf()
    private var queueImages: ArrayList<String> = arrayListOf()
    private var playlistIds: ArrayList<String> = arrayListOf()
    private var playlistTitles: ArrayList<String> = arrayListOf()
    private var playlistSubtitles: ArrayList<String> = arrayListOf()
    private var playlistImages: ArrayList<String> = arrayListOf()
    private var currentIndex: Int = 0
    private var playbackMode: MusicPlaybackMode = MusicPlaybackMode.ORDER

    private var player: Player? = null
    private var playbackService: MusicPlaybackService? = null
    private var isServiceBound = false
    private var currentPlaybackItemId: String = ""
    private var currentPlaybackPositionMs: Long = 0L
    private var currentPlaybackIsLocal = false
    private var shouldResumeAfterLoad = true
    private var isSeekingFromUser = false
    private var isCurrentFavorite = false
    private var isCurrentCached = false
    private var favoriteRequestInFlight = false
    private var deleteRequestInFlight = false
    private var cacheRequestInFlight = false
    private var isSwitchingTrack = false
    private var swipeStartX = 0f
    private var swipeStartY = 0f
    private var loadingAnimator: AnimatorSet? = null

    private var isLyricsVisible = false
    private var lyricsLines: List<LyricLineUiModel> = emptyList()
    private var lyricsLoadInFlight = false
    private var currentLyricIndex = -1
    private var currentContentPage = MusicPlayerContentPage.SONG

    private val playbackStateListener = MusicPlaybackService.PlaybackStateListener {
        runOnUiThread {
            updateSleepTimerUi()
            val stopReason = MusicPlaybackService.consumeLastStopReason()
            if (stopReason == MusicPlaybackService.StopReason.SLEEP_TIMER && !isFinishing) {
                Toast.makeText(
                    this,
                    getString(R.string.music_player_sleep_timer_expired),
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }

    private val sleepTimerUpdater = object : Runnable {
        override fun run() {
            val service = playbackService ?: MusicPlaybackService.activeService()
            if (service?.getSleepTimerRemainingMs() != null) {
                updateSleepTimerUi()
                mainHandler.postDelayed(this, 1000L)
            } else {
                updateSleepTimerUi()
            }
        }
    }

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
                handleQueuePlaybackEnded()
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
            // Load lyrics for the new track
            lyricsLines = emptyList()
            currentLyricIndex = -1
            if (isLyricsVisible && !itemId.isNullOrBlank()) {
                loadLyrics(itemId)
            }
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
                currentPlaybackItemId = activeItem?.mediaId.orEmpty()
                currentPlaybackPositionMs = currentPlayer.currentPosition.coerceAtLeast(0L)
                currentPlaybackIsLocal = activeItem?.localConfiguration?.uri?.scheme == "file"
                favoriteButton.isEnabled = !currentPlaybackIsLocal
                cacheButton.isEnabled = !currentPlaybackIsLocal
                deleteButton.isEnabled = !currentPlaybackIsLocal
                syncMetadataFromCurrentItem(activeItem)
                syncControls()
                refreshFavoriteState(activeItem?.mediaId)
                refreshCacheState(activeItem?.mediaId)
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
        playlistIds = intent.getStringArrayListExtra(EXTRA_PLAYLIST_IDS) ?: ArrayList(queueIds)
        playlistTitles = intent.getStringArrayListExtra(EXTRA_PLAYLIST_TITLES) ?: ArrayList(queueTitles)
        playlistSubtitles = intent.getStringArrayListExtra(EXTRA_PLAYLIST_SUBTITLES) ?: ArrayList(queueSubtitles)
        playlistImages = intent.getStringArrayListExtra(EXTRA_PLAYLIST_IMAGES) ?: ArrayList(queueImages)
        currentIndex = intent.getIntExtra(EXTRA_QUEUE_INDEX, 0).coerceIn(0, (queueIds.lastIndex).coerceAtLeast(0))
        playbackMode = intent.getStringExtra(EXTRA_PLAYBACK_MODE)
            ?.let { runCatching { MusicPlaybackMode.valueOf(it) }.getOrNull() }
            ?: if (intent.getBooleanExtra(EXTRA_SHUFFLE_MODE, false)) {
                MusicPlaybackMode.SHUFFLE
            } else {
                MusicPlaybackMode.ORDER
            }
        recordPlayerSession()

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
        playbackModeButton = findViewById(R.id.musicPlayerModeButton)
        sleepTimerButton = findViewById(R.id.musicPlayerSleepTimerButton)
        lyricsQuickButton = findViewById(R.id.musicPlayerLyricsQuickButton)
        songQuickButton = findViewById(R.id.musicPlayerSongQuickButton)
        favoriteButton = findViewById(R.id.musicPlayerFavoriteButton)
        cacheButton = findViewById(R.id.musicPlayerCacheButton)
        deleteButton = findViewById(R.id.musicPlayerDeleteButton)
        lyricsScrollView = findViewById(R.id.musicPlayerLyricsScroll)
        lyricsContainer = findViewById(R.id.musicPlayerLyricsContainer)
        lyricsButton = findViewById(R.id.musicPlayerLyricsButton)
        songTabButton = findViewById(R.id.musicPlayerSongTabButton)
        lyricsTabButton = findViewById(R.id.musicPlayerLyricsTabButton)
        songPage = findViewById(R.id.musicPlayerSongPage)
        lyricsPage = findViewById(R.id.musicPlayerLyricsPage)
        elapsedTextView = findViewById(R.id.musicPlayerElapsedText)
        durationTextView = findViewById(R.id.musicPlayerDurationText)
        seekBar = findViewById(R.id.musicPlayerSeekBar)
        loadingIndicator = findViewById(R.id.musicPlayerLoadingIndicator)

        findViewById<ImageButton>(R.id.musicPlayerBackButton).setDebouncedClickListener { finish() }
        playPauseButton.setDebouncedClickListener { togglePlayPause() }
        previousButton.setDebouncedClickListener { playPrevious() }
        nextButton.setDebouncedClickListener { playNext() }
        playbackModeButton.setDebouncedClickListener { cyclePlaybackMode() }
        sleepTimerButton.setDebouncedClickListener { showSleepTimerDialog() }
        lyricsQuickButton.setDebouncedClickListener { switchContentPage(MusicPlayerContentPage.LYRICS) }
        songQuickButton.setDebouncedClickListener { switchContentPage(MusicPlayerContentPage.SONG) }
        favoriteButton.setDebouncedClickListener { toggleFavorite() }
        cacheButton.setDebouncedClickListener { triggerProactiveCache() }
        deleteButton.setDebouncedClickListener { showDeleteMusicConfirmDialog() }
        lyricsButton.setDebouncedClickListener { switchContentPage(MusicPlayerContentPage.LYRICS) }
        songTabButton.setDebouncedClickListener { switchContentPage(MusicPlayerContentPage.SONG) }
        lyricsTabButton.setDebouncedClickListener { switchContentPage(MusicPlayerContentPage.LYRICS) }
        lyricsButton.visibility = View.GONE
        queueTitleTextView.text = queueTitle
        updateFavoriteIcon()
        applyBottomControlButtonTint()
        updatePlaybackModeUi()
        updateSleepTimerUi()
        switchContentPage(MusicPlayerContentPage.SONG)

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    elapsedTextView.text = formatMillis(progress.toLong())
                    updateLyricsHighlight(progress.toLong())
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
                updateLyricsHighlight(target)
            }
        })

        requestNotificationPermissionIfNeeded()
    }

    override fun onStart() {
        super.onStart()
        MusicLibraryRepository.subscribe(stateListener)
        MusicPlaybackService.registerStateListener(playbackStateListener)

        val serviceIntent = Intent(this, MusicPlaybackService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onStop() {
        MusicLibraryRepository.unsubscribe(stateListener)
        MusicPlaybackService.unregisterStateListener(playbackStateListener)
        detachPlayer()
        mainHandler.removeCallbacks(sleepTimerUpdater)
        if (isServiceBound) {
            unbindService(serviceConnection)
            isServiceBound = false
        }
        playbackService = null
        super.onStop()
    }

    override fun dispatchTouchEvent(event: MotionEvent?): Boolean {
        if (event != null && !isSwitchingTrack) {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    swipeStartX = event.x
                    swipeStartY = event.y
                }
                MotionEvent.ACTION_UP -> {
                    val diffX = event.x - swipeStartX
                    val diffY = event.y - swipeStartY
                    val absDiffX = kotlin.math.abs(diffX)
                    val absDiffY = kotlin.math.abs(diffY)

                    // Require minimum 120px horizontal movement and horizontal dominance
                    if (absDiffX > 120 && absDiffX > absDiffY * 2) {
                        if (diffX < 0) {
                            playNext()
                        } else {
                            playPrevious()
                        }
                    }
                }
            }
        }
        return super.dispatchTouchEvent(event)
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
        mainHandler.removeCallbacks(sleepTimerUpdater)
        player = null
    }

    private fun recordPlayerSession() {
        MusicPlayerSessionStore.record(
            connection = connection,
            libraryId = libraryId,
            queueTitle = queueTitle,
            queueIds = queueIds,
            queueTitles = queueTitles,
            queueSubtitles = queueSubtitles,
            queueImages = queueImages,
            queueIndex = currentIndex,
            playbackMode = playbackMode,
            playlistIds = playlistIds,
            playlistTitles = playlistTitles,
            playlistSubtitles = playlistSubtitles,
            playlistImages = playlistImages
        )
    }

    private fun cyclePlaybackMode() {
        playbackMode = when (playbackMode) {
            MusicPlaybackMode.ORDER -> MusicPlaybackMode.REPEAT_ONE
            MusicPlaybackMode.REPEAT_ONE -> MusicPlaybackMode.SHUFFLE
            MusicPlaybackMode.SHUFFLE -> MusicPlaybackMode.ORDER
        }
        val currentItemId = currentPlaybackItemId.ifBlank { queueIds.getOrNull(currentIndex).orEmpty() }
        when (playbackMode) {
            MusicPlaybackMode.ORDER -> restorePlaylistOrder(currentItemId)
            MusicPlaybackMode.SHUFFLE -> shuffleQueueKeepingCurrent(currentItemId)
            MusicPlaybackMode.REPEAT_ONE -> Unit
        }
        updatePlaybackModeUi()
        syncControls()
        recordPlayerSession()
        Toast.makeText(this, currentPlaybackModeLabel(), Toast.LENGTH_SHORT).show()
    }

    private fun restorePlaylistOrder(currentItemId: String) {
        queueIds = ArrayList(playlistIds)
        queueTitles = ArrayList(playlistTitles)
        queueSubtitles = ArrayList(playlistSubtitles)
        queueImages = ArrayList(playlistImages)
        currentIndex = if (currentItemId.isNotBlank()) {
            queueIds.indexOf(currentItemId).takeIf { it >= 0 } ?: currentIndex.coerceIn(0, (queueIds.lastIndex).coerceAtLeast(0))
        } else {
            currentIndex.coerceIn(0, (queueIds.lastIndex).coerceAtLeast(0))
        }
    }

    private fun shuffleQueueKeepingCurrent(currentItemId: String) {
        val entries = playlistIds.indices.map { index ->
            MusicQueueEntry(
                id = playlistIds[index],
                title = playlistTitles.getOrNull(index).orEmpty(),
                subtitle = playlistSubtitles.getOrNull(index).orEmpty(),
                imageUrl = playlistImages.getOrNull(index).orEmpty()
            )
        }.toMutableList()
        if (entries.isEmpty()) return

        val anchor = entries.firstOrNull { it.id == currentItemId }
        if (anchor != null) {
            entries.remove(anchor)
            entries.shuffle()
            entries.add(0, anchor)
            currentIndex = 0
        } else {
            entries.shuffle()
            currentIndex = currentIndex.coerceIn(0, (entries.lastIndex).coerceAtLeast(0))
        }

        queueIds = ArrayList(entries.map { it.id })
        queueTitles = ArrayList(entries.map { it.title })
        queueSubtitles = ArrayList(entries.map { it.subtitle })
        queueImages = ArrayList(entries.map { it.imageUrl })
    }

    private fun playPrevious() {
        if (queueIds.size <= 1) {
            syncControls()
            return
        }
        if (currentIndex > 0) {
            switchToIndex(currentIndex - 1, true)
            return
        }
        switchToIndex(queueIds.lastIndex, true)
    }

    private fun playNext() {
        if (queueIds.size <= 1) {
            syncControls()
            return
        }
        if (currentIndex < queueIds.lastIndex) {
            switchToIndex(currentIndex + 1, true)
            return
        }
        when (playbackMode) {
            MusicPlaybackMode.SHUFFLE -> reshuffleQueueForContinuousPlay()
            else -> switchToIndex(0, true)
        }
    }

    private fun replayCurrentTrackFromStart() {
        val currentPlayer = player ?: return
        currentPlayer.seekTo(0L)
        currentPlayer.playWhenReady = true
        currentPlayer.play()
        syncControls()
    }

    private fun updatePlaybackModeUi() {
        val (iconRes, labelRes) = when (playbackMode) {
            MusicPlaybackMode.ORDER -> R.drawable.ic_music_mode_order to R.string.music_player_mode_order
            MusicPlaybackMode.REPEAT_ONE -> R.drawable.ic_music_mode_repeat_one to R.string.music_player_mode_repeat_one
            MusicPlaybackMode.SHUFFLE -> R.drawable.ic_music_mode_shuffle to R.string.music_player_mode_shuffle
        }
        playbackModeButton.setImageResource(iconRes)
        playbackModeButton.contentDescription = getString(labelRes)
        playbackModeButton.alpha = 1f
    }

    private fun currentPlaybackModeLabel(): String {
        return when (playbackMode) {
            MusicPlaybackMode.ORDER -> getString(R.string.music_player_mode_order)
            MusicPlaybackMode.REPEAT_ONE -> getString(R.string.music_player_mode_repeat_one)
            MusicPlaybackMode.SHUFFLE -> getString(R.string.music_player_mode_shuffle)
        }
    }

    private fun showSleepTimerDialog() {
        val service = playbackService ?: MusicPlaybackService.activeService() ?: return
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_music_sleep_timer, null, false)
        val durationValueView = dialogView.findViewById<TextView>(R.id.musicSleepTimerDurationValue)
        val seekBar = dialogView.findViewById<SeekBar>(R.id.musicSleepTimerSeekBar)
        val startButton = dialogView.findViewById<TextView>(R.id.musicSleepTimerStartButton)
        val clearButton = dialogView.findViewById<TextView>(R.id.musicSleepTimerClearButton)
        val cancelButton = dialogView.findViewById<TextView>(R.id.musicSleepTimerCancelButton)

        val remainingMs = service.getSleepTimerRemainingMs()
        val initialMinutes = remainingMs?.let { remainingToSleepMinutes(it) } ?: DEFAULT_SLEEP_TIMER_MINUTES
        seekBar.progress = sleepMinutesToProgress(initialMinutes)
        durationValueView.text = formatSleepTimerMinutes(initialMinutes)
        startButton.text = getString(
            if (remainingMs != null) R.string.music_player_sleep_timer_update else R.string.music_player_sleep_timer_start
        )
        clearButton.visibility = if (remainingMs != null) View.VISIBLE else View.GONE

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                durationValueView.text = formatSleepTimerMinutes(progressToSleepMinutes(progress))
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })

        val dialog = createMusicGlassDialog(dialogView)
        startButton.setDebouncedClickListener {
            val durationMinutes = progressToSleepMinutes(seekBar.progress)
            service.startSleepTimer(durationMinutes * 60_000L)
            updateSleepTimerUi()
            dialog.dismiss()
        }
        clearButton.setDebouncedClickListener {
            service.clearSleepTimer()
            updateSleepTimerUi()
            dialog.dismiss()
        }
        cancelButton.setDebouncedClickListener {
            dialog.dismiss()
        }
        dialog.applyMusicGlassWindow(this)
    }

    private fun showSleepTimerDialogLegacy() {
        val service = playbackService ?: MusicPlaybackService.activeService() ?: return
        val options = mutableListOf<Pair<String, Long?>>(
            "10 分钟" to 10L * 60_000L,
            "20 分钟" to 20L * 60_000L,
            "30 分钟" to 30L * 60_000L,
            "45 分钟" to 45L * 60_000L,
            "60 分钟" to 60L * 60_000L,
            "90 分钟" to 90L * 60_000L
        )
        if (service.getSleepTimerRemainingMs() != null) {
            options.add(getString(R.string.music_player_sleep_timer_cancel_option) to null)
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.music_player_sleep_timer_dialog_title)
            .setItems(options.map { it.first }.toTypedArray()) { dialog, which ->
                val durationMs = options.getOrNull(which)?.second
                if (durationMs == null) {
                    service.clearSleepTimer()
                } else {
                    service.startSleepTimer(durationMs)
                }
                updateSleepTimerUi()
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun updateSleepTimerUi() {
        val remainingMs = (playbackService ?: MusicPlaybackService.activeService())?.getSleepTimerRemainingMs()
        if (remainingMs == null) {
            sleepTimerButton.alpha = 0.72f
            sleepTimerButton.contentDescription = getString(R.string.music_player_sleep_timer_off)
            mainHandler.removeCallbacks(sleepTimerUpdater)
            return
        }
        sleepTimerButton.alpha = 1f
        sleepTimerButton.contentDescription = getString(
            R.string.music_player_sleep_timer_remaining,
            formatSleepTimer(remainingMs)
        )
        mainHandler.removeCallbacks(sleepTimerUpdater)
        mainHandler.postDelayed(sleepTimerUpdater, 1000L)
    }

    private fun formatSleepTimer(remainingMs: Long): String {
        val totalSeconds = ((remainingMs + 999L) / 1000L).coerceAtLeast(0L)
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0L) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    private fun formatSleepTimerMinutes(minutes: Int): String {
        return getString(R.string.music_player_sleep_timer_minutes, minutes)
    }

    private fun progressToSleepMinutes(progress: Int): Int {
        return SLEEP_TIMER_MIN_MINUTES + (progress.coerceIn(0, SLEEP_TIMER_MAX_PROGRESS) * SLEEP_TIMER_STEP_MINUTES)
    }

    private fun sleepMinutesToProgress(minutes: Int): Int {
        val clampedMinutes = minutes.coerceIn(SLEEP_TIMER_MIN_MINUTES, SLEEP_TIMER_MAX_MINUTES)
        return ((clampedMinutes - SLEEP_TIMER_MIN_MINUTES) / SLEEP_TIMER_STEP_MINUTES)
            .coerceIn(0, SLEEP_TIMER_MAX_PROGRESS)
    }

    private fun remainingToSleepMinutes(remainingMs: Long): Int {
        val roundedMinutes = ((remainingMs + 59_999L) / 60_000L).toInt()
        val steps = ((roundedMinutes - SLEEP_TIMER_MIN_MINUTES + SLEEP_TIMER_STEP_MINUTES - 1) / SLEEP_TIMER_STEP_MINUTES)
            .coerceAtLeast(0)
        return (SLEEP_TIMER_MIN_MINUTES + steps * SLEEP_TIMER_STEP_MINUTES)
            .coerceIn(SLEEP_TIMER_MIN_MINUTES, SLEEP_TIMER_MAX_MINUTES)
    }

    private fun switchToIndex(index: Int, playWhenReady: Boolean, resetPosition: Boolean = true) {
        if (index !in queueIds.indices) {
            syncControls()
            return
        }

        val currentPlayer = player ?: return
        val previousPosition = currentPlayer.currentPosition.takeIf { it > 0L } ?: currentPlaybackPositionMs
        reportPlaybackProgress(previousPosition)

        isSwitchingTrack = true
        currentIndex = index
        shouldResumeAfterLoad = playWhenReady
        if (resetPosition) {
            currentPlaybackPositionMs = 0L
        }
        currentPlaybackIsLocal = false
        syncStaticMetadata()
        favoriteButton.isEnabled = false
        cacheButton.isEnabled = false
        deleteButton.isEnabled = false
        startLoadingAnimation()

        // Reset lyrics on track switch
        lyricsLines = emptyList()
        currentLyricIndex = -1
        if (isLyricsVisible) {
            lyricsContainer.removeAllViews()
            val loadingText = TextView(this).apply {
                text = getString(R.string.music_player_lyrics_loading)
                textSize = 13f
                setTextColor(ContextCompat.getColor(this@MusicPlayerActivity, R.color.music_library_meta))
                gravity = android.view.Gravity.CENTER
            }
            lyricsContainer.addView(loadingText)
        }

        AppExecutors.io.execute {
            val itemId = queueIds[index]
            val result = offlineCache.getCachedPlayback(connection, itemId)?.let { Result.success(it) }
                ?: musicRepository.fetchAudioPlayback(connection, itemId)
            runOnUiThread {
                result.onSuccess { playback ->
                    currentPlaybackItemId = playback.itemId
                    currentPlaybackIsLocal = playback.isOfflineCached
                    currentPlaybackPositionMs = if (resetPosition) {
                        playback.playbackPositionMs
                    } else {
                        currentPlaybackPositionMs
                    }
                    titleTextView.text = playback.title
                    subtitleTextView.text = playback.subtitle
                    bindCover(playback.coverImageUrl)
                    isCurrentFavorite = playback.isFavorite
                    favoriteButton.isEnabled = !currentPlaybackIsLocal
                    cacheButton.isEnabled = !currentPlaybackIsLocal
                    deleteButton.isEnabled = !currentPlaybackIsLocal
                    updateFavoriteIcon()
                    refreshCacheState(playback.itemId)

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
                    if (!playback.isOfflineCached) {
                        offlineCache.cachePlayback(connection, libraryId, playback)
                        cacheLyricsForOffline(playback.itemId)
                    }
                    isSwitchingTrack = false
                    syncControls()
                    // Load lyrics for new track
                    lyricsLines = emptyList()
                    currentLyricIndex = -1
                    if (isLyricsVisible) {
                        loadLyrics(playback.itemId)
                    }
                }.onFailure { error ->
                    stopLoadingAnimation()
                    isSwitchingTrack = false
                    favoriteButton.isEnabled = !currentPlaybackIsLocal
                    cacheButton.isEnabled = !currentPlaybackIsLocal
                    deleteButton.isEnabled = !currentPlaybackIsLocal
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
                    offlineCache.updateFavoriteState(connection, itemId, target)
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
        if (currentPlaybackIsLocal) {
            favoriteButton.isEnabled = false
            cacheButton.isEnabled = false
            return
        }
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

    private fun showDeleteMusicConfirmDialog() {
        val itemId = currentPlaybackItemId.ifBlank { queueIds.getOrNull(currentIndex).orEmpty() }
        if (itemId.isBlank() || deleteRequestInFlight) return

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_clear_played_state, null)
        dialogView.findViewById<TextView>(R.id.clearPlayedStateDialogTitle)
            .text = getString(R.string.delete_music_confirm_title)
        dialogView.findViewById<TextView>(R.id.clearPlayedStateDialogMessage)
            .text = getString(R.string.delete_music_confirm_message)
        dialogView.findViewById<TextView>(R.id.clearPlayedStateDialogConfirmButton)
            .text = getString(R.string.action_delete_music)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        dialog.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        dialog.window?.attributes = dialog.window?.attributes?.apply {
            dimAmount = 0.22f
        }

        dialogView.findViewById<TextView>(R.id.clearPlayedStateDialogCancelButton)
            .setDebouncedClickListener { dialog.dismiss() }
        dialogView.findViewById<TextView>(R.id.clearPlayedStateDialogConfirmButton)
            .setDebouncedClickListener {
                dialog.dismiss()
                deleteCurrentMusic(itemId)
            }
        dialog.show()
    }

    private fun deleteCurrentMusic(itemId: String) {
        if (deleteRequestInFlight) return
        deleteRequestInFlight = true
        favoriteButton.isEnabled = false
        cacheButton.isEnabled = false
        deleteButton.isEnabled = false

        AppExecutors.io.execute {
            val result = mediaRepository.deleteItem(connection, itemId)
            runOnUiThread {
                deleteRequestInFlight = false
                result.onSuccess {
                    offlineCache.remove(connection, itemId)
                    Toast.makeText(this, getString(R.string.delete_music_success), Toast.LENGTH_SHORT).show()
                    handleDeletedQueueItem(itemId)
                }.onFailure { error ->
                    favoriteButton.isEnabled = true
                    cacheButton.isEnabled = true
                    deleteButton.isEnabled = true
                    Toast.makeText(
                        this,
                        userFriendlyErrorMessage(error, R.string.delete_music_failed),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun handleDeletedQueueItem(itemId: String) {
        val removedIndex = queueIds.indexOf(itemId).takeIf { it >= 0 } ?: currentIndex
        if (removedIndex in queueIds.indices) {
            queueIds.removeAt(removedIndex)
        }
        if (removedIndex in queueTitles.indices) {
            queueTitles.removeAt(removedIndex)
        }
        if (removedIndex in queueSubtitles.indices) {
            queueSubtitles.removeAt(removedIndex)
        }
        if (removedIndex in queueImages.indices) {
            queueImages.removeAt(removedIndex)
        }
        val playlistRemovedIndex = playlistIds.indexOf(itemId)
        if (playlistRemovedIndex in playlistIds.indices) {
            playlistIds.removeAt(playlistRemovedIndex)
        }
        if (playlistRemovedIndex in playlistTitles.indices) {
            playlistTitles.removeAt(playlistRemovedIndex)
        }
        if (playlistRemovedIndex in playlistSubtitles.indices) {
            playlistSubtitles.removeAt(playlistRemovedIndex)
        }
        if (playlistRemovedIndex in playlistImages.indices) {
            playlistImages.removeAt(playlistRemovedIndex)
        }

        currentPlaybackItemId = ""
        currentPlaybackPositionMs = 0L

        if (queueIds.isEmpty()) {
            recordPlayerSession()
            playbackService?.stopPlaybackAndSelf()
            finish()
            return
        }

        currentIndex = removedIndex.coerceAtMost(queueIds.lastIndex)
        recordPlayerSession()
        switchToIndex(currentIndex, playWhenReady = true, resetPosition = true)
    }

    private fun handleQueuePlaybackEnded() {
        when (playbackMode) {
            MusicPlaybackMode.REPEAT_ONE -> replayCurrentTrackFromStart()
            MusicPlaybackMode.SHUFFLE -> {
                if (currentIndex < queueIds.lastIndex) {
                    switchToIndex(currentIndex + 1, true)
                } else {
                    reshuffleQueueForContinuousPlay()
                }
            }
            MusicPlaybackMode.ORDER -> {
                if (currentIndex < queueIds.lastIndex) {
                    switchToIndex(currentIndex + 1, true)
                } else if (queueIds.size > 1) {
                    switchToIndex(0, true)
                } else {
                    replayCurrentTrackFromStart()
                }
            }
        }
    }

    private fun reshuffleQueueForContinuousPlay() {
        if (queueIds.isEmpty()) {
            syncControls()
            return
        }
        val justFinishedItemId = queueIds.getOrNull(currentIndex).orEmpty()
        val shuffledEntries = queueIds.indices.map { index ->
            MusicQueueEntry(
                id = queueIds[index],
                title = queueTitles.getOrNull(index).orEmpty(),
                subtitle = queueSubtitles.getOrNull(index).orEmpty(),
                imageUrl = queueImages.getOrNull(index).orEmpty()
            )
        }.shuffled().toMutableList()

        if (shuffledEntries.size > 1 && shuffledEntries.firstOrNull()?.id == justFinishedItemId) {
            val first = shuffledEntries.removeAt(0)
            shuffledEntries.add(first)
        }

        queueIds = ArrayList(shuffledEntries.map { it.id })
        queueTitles = ArrayList(shuffledEntries.map { it.title })
        queueSubtitles = ArrayList(shuffledEntries.map { it.subtitle })
        queueImages = ArrayList(shuffledEntries.map { it.imageUrl })
        currentIndex = 0
        recordPlayerSession()
        switchToIndex(currentIndex, playWhenReady = true, resetPosition = true)
    }

    private fun updateFavoriteIcon() {
        favoriteButton.setImageResource(
            if (isCurrentFavorite) R.drawable.ic_favorite_heart_filled else R.drawable.ic_favorite_heart_outline
        )
        favoriteButton.clearColorFilter()
    }

    private fun refreshCacheState(itemId: String?) {
        if (itemId.isNullOrBlank()) return
        if (currentPlaybackIsLocal) {
            isCurrentCached = true
            cacheButton.isEnabled = false
            updateCacheIcon()
            return
        }
        AppExecutors.io.execute {
            val cached = offlineCache.isCached(connection, itemId)
            runOnUiThread {
                if (currentPlaybackItemId == itemId || player?.currentMediaItem?.mediaId == itemId) {
                    isCurrentCached = cached
                    updateCacheIcon()
                }
            }
        }
    }

    private fun updateCacheIcon() {
        if (isCurrentCached) {
            cacheButton.setImageResource(R.drawable.ic_music_player_cached)
            cacheButton.clearColorFilter()
            cacheButton.contentDescription = getString(R.string.music_player_cache_cached)
        } else {
            cacheButton.setImageResource(R.drawable.ic_music_library_download)
            cacheButton.clearColorFilter()
            cacheButton.contentDescription = getString(R.string.music_player_cache)
        }
    }

    // ─── Lyrics ──────────────────────────────────────────────

    private fun toggleLyrics() {
        switchContentPage(
            if (currentContentPage == MusicPlayerContentPage.LYRICS) {
                MusicPlayerContentPage.SONG
            } else {
                MusicPlayerContentPage.LYRICS
            }
        )
    }

    private fun switchContentPage(page: MusicPlayerContentPage) {
        currentContentPage = page
        isLyricsVisible = page == MusicPlayerContentPage.LYRICS
        songPage.visibility = if (page == MusicPlayerContentPage.SONG) View.VISIBLE else View.GONE
        lyricsPage.visibility = if (page == MusicPlayerContentPage.LYRICS) View.VISIBLE else View.GONE

        val selectedBackground = R.drawable.bg_glass_dialog_button_primary
        val unselectedBackground = R.drawable.bg_glass_dialog_button_secondary
        val selectedTextColor = ContextCompat.getColor(this, R.color.music_library_title)
        val unselectedTextColor = ContextCompat.getColor(this, R.color.music_library_meta)

        songTabButton.setBackgroundResource(
            if (page == MusicPlayerContentPage.SONG) selectedBackground else unselectedBackground
        )
        lyricsTabButton.setBackgroundResource(
            if (page == MusicPlayerContentPage.LYRICS) selectedBackground else unselectedBackground
        )
        songTabButton.setTextColor(
            if (page == MusicPlayerContentPage.SONG) selectedTextColor else unselectedTextColor
        )
        lyricsTabButton.setTextColor(
            if (page == MusicPlayerContentPage.LYRICS) selectedTextColor else unselectedTextColor
        )

        songQuickButton.alpha = if (page == MusicPlayerContentPage.SONG) 1f else 0.58f
        lyricsQuickButton.alpha = if (page == MusicPlayerContentPage.LYRICS) 1f else 0.58f

        if (page == MusicPlayerContentPage.LYRICS) {
            val itemId = currentPlaybackItemId.ifBlank { queueIds.getOrNull(currentIndex).orEmpty() }
            if (lyricsLines.isEmpty() && itemId.isNotBlank()) {
                loadLyrics(itemId)
            } else {
                renderLyrics()
            }
        } else {
            currentLyricIndex = -1
        }
    }

    private fun loadLyrics(itemId: String) {
        if (lyricsLoadInFlight) return
        val cachedLyrics = offlineCache.getCachedLyrics(connection, itemId)
        if (currentPlaybackIsLocal && cachedLyrics != null) {
            lyricsLines = cachedLyrics.lines
            renderLyrics()
            return
        }
        lyricsLoadInFlight = true
        lyricsContainer.removeAllViews()
        val loadingText = TextView(this).apply {
            text = getString(R.string.music_player_lyrics_loading)
            textSize = 13f
            setTextColor(ContextCompat.getColor(this@MusicPlayerActivity, R.color.music_library_meta))
            gravity = android.view.Gravity.CENTER
        }
        lyricsContainer.addView(loadingText)

        AppExecutors.io.execute {
            val result = musicRepository.fetchLyrics(connection, itemId)
            runOnUiThread {
                lyricsLoadInFlight = false
                result.onSuccess { lyrics ->
                    if (currentPlaybackItemId == itemId || queueIds.getOrNull(currentIndex) == itemId) {
                        lyricsLines = lyrics.lines
                        if (lyrics.lines.isNotEmpty()) {
                            offlineCache.cacheLyrics(connection, itemId, lyrics)
                        }
                        renderLyrics()
                    }
                }.onFailure { error ->
                    android.util.Log.e("MusicPlayerLyrics", "歌词加载失败", error)
                    if (currentPlaybackItemId == itemId || queueIds.getOrNull(currentIndex) == itemId) {
                        lyricsLines = cachedLyrics?.lines ?: emptyList()
                        renderLyrics()
                        Toast.makeText(
                            this@MusicPlayerActivity,
                            "歌词加载失败: ${error.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

    private fun cacheLyricsForOffline(itemId: String) {
        if (itemId.isBlank()) return
        AppExecutors.io.execute {
            val result = musicRepository.fetchLyrics(connection, itemId)
            result.onSuccess { lyrics ->
                if (lyrics.lines.isNotEmpty()) {
                    offlineCache.cacheLyrics(connection, itemId, lyrics)
                }
            }
        }
    }

    private fun renderLyrics() {
        lyricsContainer.removeAllViews()
        currentLyricIndex = -1

        if (lyricsLines.isEmpty()) {
            val noLyricsText = TextView(this).apply {
                text = getString(R.string.music_player_lyrics_no_lyrics)
                textSize = 14f
                setTextColor(ContextCompat.getColor(this@MusicPlayerActivity, R.color.music_library_meta))
                gravity = android.view.Gravity.CENTER
                setPadding(0, 64, 0, 64)
            }
            lyricsContainer.addView(noLyricsText)
            return
        }

        for ((index, line) in lyricsLines.withIndex()) {
            val lineView = TextView(this).apply {
                text = line.text
                textSize = 15f
                gravity = android.view.Gravity.CENTER
                setPadding(0, 12, 0, 12)
                setTextColor(ContextCompat.getColor(this@MusicPlayerActivity, R.color.music_library_meta))
                tag = index
            }
            lyricsContainer.addView(lineView)
        }

        // Update highlight based on current position
        val currentPlayer = player
        if (currentPlayer != null) {
            updateLyricsHighlight(currentPlayer.currentPosition.coerceAtLeast(0L))
        }
    }

    private fun updateLyricsHighlight(positionMs: Long) {
        if (!isLyricsVisible || lyricsLines.isEmpty()) return

        var activeIndex = -1
        for (i in lyricsLines.indices) {
            if (positionMs >= lyricsLines[i].startMs) {
                activeIndex = i
            } else {
                break
            }
        }

        if (activeIndex == currentLyricIndex) return
        currentLyricIndex = activeIndex

        val accentColor = ContextCompat.getColor(this, R.color.music_library_accent)
        val metaColor = ContextCompat.getColor(this, R.color.music_library_meta)

        for (i in 0 until lyricsContainer.childCount) {
            val child = lyricsContainer.getChildAt(i) as? TextView ?: continue
            if (i == activeIndex) {
                child.setTextColor(accentColor)
                child.textSize = 17f
                // Scroll to the active line
                val scrollY = child.top - (lyricsScrollView.height / 2) + (child.height / 2)
                lyricsScrollView.smoothScrollTo(0, scrollY.coerceAtLeast(0))
            } else {
                child.setTextColor(metaColor)
                child.textSize = 15f
            }
        }
    }

    // ─── End Lyrics ──────────────────────────────────────────

    /**
     * Triggers proactive caching for the current track.
     * Fetches playback info if needed, then downloads the audio file to local storage.
     */
    private fun triggerProactiveCache() {
        val itemId = currentPlaybackItemId.ifBlank { queueIds.getOrNull(currentIndex).orEmpty() }
        if (itemId.isBlank() || cacheRequestInFlight) return

        if (isCurrentCached) {
            Toast.makeText(this, getString(R.string.music_player_cache_already), Toast.LENGTH_SHORT).show()
            return
        }
        if (currentPlaybackIsLocal) return

        cacheRequestInFlight = true
        cacheButton.isEnabled = false
        cacheButton.setImageResource(R.drawable.ic_music_library_download)
        cacheButton.alpha = 0.5f
        cacheButton.contentDescription = getString(R.string.music_player_cache_caching)

        AppExecutors.io.execute {
            val playbackResult = musicRepository.fetchAudioPlayback(connection, itemId)
            playbackResult.onSuccess { playback ->
                offlineCache.cachePlaybackProactively(connection, libraryId, playback) { result ->
                    runOnUiThread {
                        cacheRequestInFlight = false
                        cacheButton.alpha = 1f
                        result.onSuccess {
                            isCurrentCached = true
                            cacheLyricsForOffline(itemId)
                            updateCacheIcon()
                            Toast.makeText(this, getString(R.string.music_player_cache_success), Toast.LENGTH_SHORT).show()
                        }.onFailure { error ->
                            cacheButton.isEnabled = true
                            updateCacheIcon()
                            Toast.makeText(
                                this,
                                userFriendlyErrorMessage(error, R.string.music_player_cache_failed),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }.onFailure { error ->
                runOnUiThread {
                    cacheRequestInFlight = false
                    cacheButton.alpha = 1f
                    cacheButton.isEnabled = true
                    updateCacheIcon()
                    Toast.makeText(
                        this,
                        userFriendlyErrorMessage(error, R.string.music_player_cache_failed),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
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
        applyBottomControlButtonTint()
        previousButton.isEnabled = queueIds.size > 1
        nextButton.isEnabled = queueIds.size > 1
        updatePlaybackModeUi()
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
        offlineCache.updatePlaybackProgress(connection, currentPlaybackItemId, positionMs)
        if (currentPlaybackIsLocal) return
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
            val currentPos = player?.currentPosition?.coerceAtLeast(0L) ?: 0L
            updateLyricsHighlight(currentPos)
            mainHandler.postDelayed(this, 120L)
        }
    }

    private fun applyBottomControlButtonTint() {
        val whiteTint = ColorStateList.valueOf(ContextCompat.getColor(this, android.R.color.white))
        previousButton.imageTintList = whiteTint
        playPauseButton.imageTintList = whiteTint
        nextButton.imageTintList = whiteTint
    }

    companion object {
        const val EXTRA_LIBRARY_ID = "extra_library_id"
        const val EXTRA_QUEUE_TITLE = "extra_queue_title"
        const val EXTRA_QUEUE_IDS = "extra_queue_ids"
        const val EXTRA_QUEUE_TITLES = "extra_queue_titles"
        const val EXTRA_QUEUE_SUBTITLES = "extra_queue_subtitles"
        const val EXTRA_QUEUE_IMAGES = "extra_queue_images"
        const val EXTRA_QUEUE_INDEX = "extra_queue_index"
        const val EXTRA_PLAYBACK_MODE = "extra_playback_mode"
        const val EXTRA_PLAYLIST_IDS = "extra_playlist_ids"
        const val EXTRA_PLAYLIST_TITLES = "extra_playlist_titles"
        const val EXTRA_PLAYLIST_SUBTITLES = "extra_playlist_subtitles"
        const val EXTRA_PLAYLIST_IMAGES = "extra_playlist_images"
        const val EXTRA_SHUFFLE_MODE = "extra_shuffle_mode"

        private const val NOTIFICATION_PERMISSION_REQUEST_CODE = 2001
        private const val SLEEP_TIMER_MIN_MINUTES = 5
        private const val SLEEP_TIMER_MAX_MINUTES = 180
        private const val SLEEP_TIMER_STEP_MINUTES = 5
        private const val SLEEP_TIMER_MAX_PROGRESS =
            (SLEEP_TIMER_MAX_MINUTES - SLEEP_TIMER_MIN_MINUTES) / SLEEP_TIMER_STEP_MINUTES
        private const val DEFAULT_SLEEP_TIMER_MINUTES = 15
    }
}

private data class MusicQueueEntry(
    val id: String,
    val title: String,
    val subtitle: String,
    val imageUrl: String
)

private enum class MusicPlayerContentPage {
    SONG,
    LYRICS
}
