package com.liujiaming.embypro

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.graphics.Color
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.media3.common.Player
import java.lang.ref.WeakReference

object MusicMiniPlayerOverlay : Application.ActivityLifecycleCallbacks,
    MusicPlaybackService.PlaybackStateListener {

    private const val OVERLAY_TAG = "music_mini_player_overlay"
    private val mainHandler = Handler(Looper.getMainLooper())
    private var application: Application? = null
    private var currentActivityRef: WeakReference<Activity>? = null
    private var capsuleView: View? = null
    private var coverImageView: ImageView? = null
    private var titleTextView: TextView? = null
    private var playPauseButton: ImageButton? = null
    private var closeButton: ImageButton? = null
    private var seekBar: SeekBar? = null
    private var isUserSeeking = false

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

    private fun attach(activity: Activity) {
        val decor = activity.window.decorView as? ViewGroup ?: return
        val existing = decor.findViewWithTag<View>(OVERLAY_TAG)
        if (existing != null && existing === capsuleView) return
        existing?.let { (it.parent as? ViewGroup)?.removeView(it) }

        val capsule = buildCapsule(activity)
        val params = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(activity, 112)
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            marginStart = dp(activity, 14)
            marginEnd = dp(activity, 14)
            topMargin = statusBarHeight(activity) + dp(activity, 2)
        }
        decor.addView(capsule, params)
        capsuleView = capsule
    }

    private fun detach() {
        stopTicker()
        capsuleView?.let { view ->
            (view.parent as? ViewGroup)?.removeView(view)
        }
        capsuleView = null
        coverImageView = null
        titleTextView = null
        playPauseButton = null
        closeButton = null
        seekBar = null
        isUserSeeking = false
    }

    private fun buildCapsule(activity: Activity): View {
        val root = FrameLayout(activity).apply {
            tag = OVERLAY_TAG
            setPadding(dp(activity, 14), dp(activity, 10), dp(activity, 12), dp(activity, 10))
            elevation = dp(activity, 8).toFloat()
            setOnClickListener { openMusicPlayer(activity) }
        }

        val backgroundView = View(activity).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(activity, 34).toFloat()
                setColor(Color.WHITE)
                setStroke(dp(activity, 1), Color.parseColor("#1FFFFFFF"))
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRenderEffect(
                    RenderEffect.createBlurEffect(
                        dp(activity, 18).toFloat(),
                        dp(activity, 18).toFloat(),
                        Shader.TileMode.CLAMP
                    )
                )
            }
        }
        root.addView(
            backgroundView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

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
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(activity, 18).toFloat()
                setColor(Color.parseColor("#33FFFFFF"))
            }
        }
        contentRow.addView(
            coverImageView,
            LinearLayout.LayoutParams(dp(activity, 72), dp(activity, 72)).apply {
                marginEnd = dp(activity, 8)
            }
        )

        playPauseButton = ImageButton(activity).apply {
            background = null
            setColorFilter(Color.parseColor("#FF272336"))
            setOnClickListener {
                MusicPlaybackService.activeService()?.togglePlayback()
            }
        }
        contentRow.addView(playPauseButton, LinearLayout.LayoutParams(dp(activity, 48), dp(activity, 48)))

        val centerColumn = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(activity, 4), 0, dp(activity, 4), 0)
        }
        titleTextView = TextView(activity).apply {
            setTextColor(Color.parseColor("#FF272336"))
            textSize = 15f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        seekBar = SeekBar(activity).apply {
            splitTrack = false
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
                dp(activity, 34)
            )
        )
        contentRow.addView(
            centerColumn,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
        )

        closeButton = ImageButton(activity).apply {
            background = null
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setColorFilter(Color.parseColor("#FF272336"))
            setOnClickListener { closePlayback() }
        }
        contentRow.addView(closeButton, LinearLayout.LayoutParams(dp(activity, 44), dp(activity, 44)))

        return root
    }

    private fun updateContent() {
        val service = MusicPlaybackService.activeService() ?: return
        val player = service.getPlayer()
        val mediaItem = player.currentMediaItem
        val title = mediaItem?.mediaMetadata?.title?.toString()
            ?.ifBlank { null }
            ?: mediaItem?.mediaId.orEmpty()
        titleTextView?.text = title.ifBlank { "正在播放" }
        bindCover(mediaItem?.mediaMetadata?.artworkUri?.toString())
        playPauseButton?.setImageResource(
            if (player.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
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

    private fun bindCover(url: String?) {
        val imageView = coverImageView ?: return
        if (url.isNullOrBlank()) {
            AppIconPlaceholder.apply(imageView, cornerRadiusDp = 18f)
            return
        }
        EmbyImageLoader.load(
            imageView = imageView,
            url = url,
            token = MusicPlayerSessionStore.currentConnection()?.accessToken,
            onFailure = {
                AppIconPlaceholder.apply(imageView, cornerRadiusDp = 18f)
            }
        )
    }

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
