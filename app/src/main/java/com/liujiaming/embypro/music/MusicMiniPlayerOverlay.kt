package com.liujiaming.embypro

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.Outline
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.media3.common.Player
import java.lang.ref.WeakReference

/**
 * Singleton overlay that displays a draggable mini music player capsule on top of activities.
 * Automatically attaches to the current activity when music is playing.
 * Supports drag-to-reposition, progress seeking, and tap-to-open full player.
 * Persists capsule position across activity transitions.
 */
object MusicMiniPlayerOverlay : Application.ActivityLifecycleCallbacks,
    MusicPlaybackService.PlaybackStateListener {

    private const val OVERLAY_TAG = "music_mini_player_overlay"
    private const val PREF_NAME = "music_mini_player_overlay"
    private const val KEY_LEFT = "capsule_left"
    private const val KEY_TOP = "capsule_top"
    private val mainHandler = Handler(Looper.getMainLooper())
    private var application: Application? = null
    private var currentActivityRef: WeakReference<Activity>? = null
    private var capsuleView: View? = null
    private var coverImageView: ImageView? = null
    private var titleTextView: TextView? = null
    private var subtitleTextView: TextView? = null
    private var playPauseButton: ImageButton? = null
    private var closeButton: ImageButton? = null
    private var seekBar: SeekBar? = null
    private var isUserSeeking = false
    private var dragDownRawX = 0f
    private var dragDownRawY = 0f
    private var dragStartLeft = 0
    private var dragStartTop = 0
    private var hasDragged = false

    /**
     * Installs the overlay system to monitor activity lifecycle and playback state.
     * Should be called once during application initialization.
     */
    fun install(application: Application) {
        if (this.application === application) return
        this.application = application
        application.registerActivityLifecycleCallbacks(this)
        MusicPlaybackService.registerStateListener(this)
    }

    override fun onActivityResumed(activity: Activity) {
        currentActivityRef = WeakReference(activity)
        refresh()
    }

    override fun onActivityPaused(activity: Activity) {
        if (currentActivityRef?.get() === activity) {
            detach()
        }
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (currentActivityRef?.get() === activity) {
            detach()
            currentActivityRef = null
        }
    }

    override fun onMusicPlaybackStateChanged() {
        refresh()
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    /**
     * Refreshes the overlay visibility and content based on current playback state.
     * Detaches if on MusicPlayerActivity or no active playback.
     */
    private fun refresh() {
        mainHandler.post {
            val activity = currentActivityRef?.get()
            val service = MusicPlaybackService.activeService()
            if (activity == null || activity is MusicPlayerActivity || service?.hasActivePlayback() != true) {
                detach()
                return@post
            }

            attach(activity)
            updateContent()
        }
    }

    /**
     * Attaches the mini player capsule view to the activity's decor view.
     * Removes any existing overlay before adding a new one.
     */
    private fun attach(activity: Activity) {
        val decor = activity.window.decorView as? ViewGroup ?: return
        val existing = decor.findViewWithTag<View>(OVERLAY_TAG)
        if (existing != null && existing === capsuleView) return
        existing?.let { (it.parent as? ViewGroup)?.removeView(it) }

        val capsule = buildCapsule(activity)
        val params = FrameLayout.LayoutParams(
            activity.resources.displayMetrics.widthPixels - dp(activity, 28),
            dp(activity, 101)
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            leftMargin = dp(activity, 14)
            topMargin = 0
        }
        decor.addView(capsule, params)
        capsuleView = capsule
        capsule.post {
            restorePosition(activity, capsule)
        }
    }

    /**
     * Detaches and cleans up the overlay view and all references.
     */
    private fun detach() {
        stopTicker()
        capsuleView?.let { view ->
            (view.parent as? ViewGroup)?.removeView(view)
        }
        capsuleView = null
        coverImageView = null
        titleTextView = null
        subtitleTextView = null
        playPauseButton = null
        closeButton = null
        seekBar = null
        isUserSeeking = false
    }

    /**
     * Builds the complete mini player capsule UI with cover art, controls, and seek bar.
     * Configures rounded corners, shadow, and touch handling.
     */
    private fun buildCapsule(activity: Activity): View {
        val root = FrameLayout(activity).apply {
            tag = OVERLAY_TAG
            setPadding(dp(activity, 14), dp(activity, 8), dp(activity, 12), dp(activity, 8))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(activity, 44).toFloat()
                setColor(Color.WHITE)
                setStroke(dp(activity, 1), Color.parseColor("#33272336"))
            }
            elevation = dp(activity, 18).toFloat()
            translationZ = dp(activity, 8).toFloat()
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, dp(activity, 44).toFloat())
                }
            }
            clipToOutline = true
            setOnTouchListener { view, event -> handleDragTouch(activity, view, event) }
        }

        val contentRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        root.addView(
            contentRow,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        coverImageView = ImageView(activity).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#33FFFFFF"))
            }
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    val size = minOf(view.width, view.height)
                    outline.setOval(0, 0, size, size)
                }
            }
            clipToOutline = true
        }
        contentRow.addView(
            coverImageView,
            LinearLayout.LayoutParams(dp(activity, 72), dp(activity, 72)).apply {
                marginEnd = dp(activity, 8)
            }
        )

        playPauseButton = ImageButton(activity).apply {
            setBackgroundResource(R.drawable.bg_music_capsule_control_button)
            setColorFilter(Color.parseColor("#FF272336"))
            setOnClickListener {
                MusicPlaybackService.activeService()?.togglePlayback()
            }
        }
        contentRow.addView(playPauseButton, LinearLayout.LayoutParams(dp(activity, 42), dp(activity, 42)))

        val centerColumn = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(activity, 4), 0, dp(activity, 4), 0)
        }
        titleTextView = TextView(activity).apply {
            setTextColor(Color.parseColor("#FF272336"))
            textSize = 14f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        subtitleTextView = TextView(activity).apply {
            setTextColor(Color.parseColor("#FF7E748E"))
            textSize = 11f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        seekBar = SeekBar(activity).apply {
            splitTrack = false
            progressDrawable = activity.getDrawable(R.drawable.bg_music_capsule_seekbar_progress)
            thumb = activity.getDrawable(R.drawable.bg_music_capsule_seekbar_thumb)
            thumbOffset = dp(activity, 7)
            setPadding(0, 0, 0, 0)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) = Unit

                override fun onStartTrackingTouch(seekBar: SeekBar?) {
                    isUserSeeking = true
                }

                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    MusicPlaybackService.activeService()?.seekTo(seekBar?.progress?.toLong() ?: 0L)
                    isUserSeeking = false
                    updateContent()
                }
            })
        }
        centerColumn.addView(
            titleTextView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        centerColumn.addView(
            seekBar,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(activity, 26)
            )
        )
        centerColumn.addView(
            subtitleTextView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        contentRow.addView(
            centerColumn,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
        )

        closeButton = ImageButton(activity).apply {
            setBackgroundResource(R.drawable.bg_music_capsule_control_button)
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setColorFilter(Color.parseColor("#FF272336"))
            setOnClickListener { closePlayback() }
        }
        contentRow.addView(closeButton, LinearLayout.LayoutParams(dp(activity, 44), dp(activity, 44)))

        return root
    }

    /**
     * Updates the capsule content with current playback information.
     * Refreshes title, artist, cover art, play/pause icon, and seek bar progress.
     */
    private fun updateContent() {
        val service = MusicPlaybackService.activeService() ?: return
        val player = service.getPlayer()
        val mediaItem = player.currentMediaItem
        val title = mediaItem?.mediaMetadata?.title?.toString()
            ?.ifBlank { null }
            ?: mediaItem?.mediaId.orEmpty()
        val subtitle = mediaItem?.mediaMetadata?.artist?.toString().orEmpty()
        titleTextView?.text = title.ifBlank { "正在播放" }
        subtitleTextView?.text = subtitle
        bindCover(mediaItem?.mediaMetadata?.artworkUri?.toString())
        playPauseButton?.setImageResource(
            if (player.isPlaying) R.drawable.ic_music_capsule_pause else R.drawable.ic_music_capsule_play
        )

        if (!isUserSeeking) {
            val duration = player.duration.takeIf { it > 0 } ?: 0L
            val position = player.currentPosition.coerceAtLeast(0L)
            seekBar?.max = duration.coerceAtMost(Int.MAX_VALUE.toLong()).toInt().coerceAtLeast(1)
            seekBar?.progress = position.coerceAtMost(seekBar?.max?.toLong() ?: 1L).toInt()
            seekBar?.isEnabled = duration > 0L
        }

        if (player.isPlaying || player.playbackState == Player.STATE_BUFFERING) {
            startTicker()
        } else {
            stopTicker()
        }
    }

    /**
     * Loads and displays the cover art image from URL.
     * Falls back to placeholder if URL is blank or loading fails.
     */
    private fun bindCover(url: String?) {
        val imageView = coverImageView ?: return
        if (url.isNullOrBlank()) {
            imageView.setImageDrawable(null)
            imageView.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#FFE5DDFF"))
            }
            return
        }
        EmbyImageLoader.load(
            imageView = imageView,
            url = url,
            token = MusicPlayerSessionStore.currentConnection()?.accessToken,
            onFailure = {
                imageView.setImageDrawable(null)
                imageView.background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#FFE5DDFF"))
                }
            }
        )
    }

    private fun handleDragTouch(activity: Activity, view: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragDownRawX = event.rawX
                dragDownRawY = event.rawY
                val params = view.layoutParams as? FrameLayout.LayoutParams
                dragStartLeft = params?.leftMargin ?: view.left
                dragStartTop = params?.topMargin ?: view.top
                hasDragged = false
                view.parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - dragDownRawX
                val dy = event.rawY - dragDownRawY
                if (!hasDragged && (kotlin.math.abs(dx) > dp(activity, 4) || kotlin.math.abs(dy) > dp(activity, 4))) {
                    hasDragged = true
                }
                val bounds = dragBounds(activity, view)
                updateCapsulePosition(
                    view = view,
                    left = (dragStartLeft + dx.toInt()).coerceIn(bounds.minLeft, bounds.maxLeft),
                    top = (dragStartTop + dy.toInt()).coerceIn(bounds.minTop, bounds.maxTop)
                )
                return true
            }

            MotionEvent.ACTION_UP -> {
                view.parent?.requestDisallowInterceptTouchEvent(false)
                if (hasDragged) {
                    savePosition(activity, view)
                } else {
                    openMusicPlayer(activity)
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                view.parent?.requestDisallowInterceptTouchEvent(false)
                if (hasDragged) {
                    savePosition(activity, view)
                }
                return true
            }
        }
        return false
    }

    private fun restorePosition(activity: Activity, view: View) {
        val preferences = activity.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val bounds = dragBounds(activity, view)
        val left = preferences.getInt(KEY_LEFT, dp(activity, 14))
        val top = preferences.getInt(KEY_TOP, 0)
        updateCapsulePosition(
            view = view,
            left = left.coerceIn(bounds.minLeft, bounds.maxLeft),
            top = top.coerceIn(bounds.minTop, bounds.maxTop)
        )
    }

    private fun savePosition(activity: Activity, view: View) {
        val params = view.layoutParams as? FrameLayout.LayoutParams ?: return
        activity.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_LEFT, params.leftMargin)
            .putInt(KEY_TOP, params.topMargin)
            .apply()
    }

    private fun dragBounds(activity: Activity, view: View): DragBounds {
        val decor = activity.window.decorView as? View ?: return DragBounds(0, 0, 0, 0)
        val availableWidth = decor.width.takeIf { it > 0 } ?: activity.resources.displayMetrics.widthPixels
        val availableHeight = decor.height.takeIf { it > 0 } ?: activity.resources.displayMetrics.heightPixels
        val minLeft = dp(activity, 8)
        val maxLeft = (availableWidth - view.width - dp(activity, 8)).coerceAtLeast(minLeft)
        val minTop = 0
        val maxTop = (availableHeight - view.height - dp(activity, 12)).coerceAtLeast(minTop)
        return DragBounds(minLeft, maxLeft, minTop, maxTop)
    }

    private fun updateCapsulePosition(view: View, left: Int, top: Int) {
        val params = view.layoutParams as? FrameLayout.LayoutParams ?: return
        params.leftMargin = left
        params.topMargin = top
        view.layoutParams = params
    }

    private data class DragBounds(
        val minLeft: Int,
        val maxLeft: Int,
        val minTop: Int,
        val maxTop: Int
    )

    private fun openMusicPlayer(activity: Activity) {
        val intent = MusicPlayerSessionStore.createPlayerIntent(activity) ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        activity.startActivity(intent)
    }

    private fun closePlayback() {
        val service = MusicPlaybackService.activeService() ?: return
        val player = service.getPlayer()
        val mediaId = player.currentMediaItem?.mediaId.orEmpty()
        val position = player.currentPosition.coerceAtLeast(0L)
        val connection = MusicPlayerSessionStore.currentConnection()
        if (connection != null && mediaId.isNotBlank()) {
            AppExecutors.io.execute {
                MusicRepository(application ?: return@execute).updatePlaybackProgress(connection, mediaId, position)
            }
        }
        service.stopPlaybackAndSelf()
        detach()
    }

    private fun startTicker() {
        mainHandler.removeCallbacks(progressTicker)
        mainHandler.postDelayed(progressTicker, 500L)
    }

    private fun stopTicker() {
        mainHandler.removeCallbacks(progressTicker)
    }

    private val progressTicker = object : Runnable {
        override fun run() {
            updateContent()
            mainHandler.postDelayed(this, 500L)
        }
    }

    private fun dp(activity: Activity, value: Int): Int {
        return (value * activity.resources.displayMetrics.density).toInt()
    }

    private fun statusBarHeight(activity: Activity): Int {
        val resourceId = activity.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) activity.resources.getDimensionPixelSize(resourceId) else 0
    }
}
