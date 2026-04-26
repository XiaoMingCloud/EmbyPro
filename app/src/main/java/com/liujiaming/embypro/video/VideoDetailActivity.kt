package com.liujiaming.embypro

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.MaterialColors
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import java.util.concurrent.ExecutorService

/**
 * Activity displaying detailed information about a video item.
 * Shows metadata, media info, chapters, and provides playback controls.
 */
class VideoDetailActivity : AppCompatActivity() {
    private val networkExecutor: ExecutorService = AppExecutors.io
    private val mediaRepository by lazy { MediaRepository(this) }
    private val sessionStore by lazy { ServerSessionStore(this) }
    private val serverRepository by lazy { ServerRepository(this) }
    private val playerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        val data = result.data ?: return@registerForActivityResult
        if (data.getBooleanExtra(PlayerActivity.RESULT_ITEM_DELETED, false)) {
            setResult(RESULT_OK)
            finish()
            return@registerForActivityResult
        }
        itemId = data.getStringExtra(PlayerActivity.RESULT_ITEM_ID).orEmpty().ifBlank { itemId }
        playlistIndex = data.getIntExtra(PlayerActivity.RESULT_PLAYLIST_INDEX, playlistIndex)
        loadVideoDetail()
    }

    private lateinit var connection: ServerConnection
    private lateinit var itemId: String
    private var playlistItemIds: ArrayList<String> = arrayListOf()
    private var playlistItemTitles: ArrayList<String> = arrayListOf()
    private var playlistIndex: Int = -1

    private lateinit var rootView: androidx.core.widget.NestedScrollView
    private lateinit var rootContent: LinearLayout
    private lateinit var heroContainer: View
    private lateinit var heroImage: ImageView
    private lateinit var heroBottomBlend: View
    private lateinit var contentContainer: LinearLayout
    private lateinit var titleText: TextView
    private lateinit var runtimeText: TextView
    private lateinit var versionText: TextView
    private lateinit var audioText: TextView
    private lateinit var studioText: TextView
    private lateinit var mediaTitleText: TextView
    private lateinit var videoInfoText: TextView
    private lateinit var audioInfoText: TextView
    private lateinit var chaptersRecyclerView: RecyclerView
    private lateinit var favoriteButton: ImageButton
    private lateinit var playButton: MaterialButton
    private lateinit var actionMoreButton: ImageButton

    private var isFavorite = false
    private var currentDetail: VideoDetailUiModel? = null
    private var initialPullY = 0f
    private var isStretchingHero = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EdgeToEdgeHelper.enable(this, lightSystemBars = false)
        setContentView(R.layout.activity_video_detail)

        supportActionBar?.hide()

        connection = requireServerConnection(sessionStore, serverRepository) ?: return
        itemId = intent.getStringExtra(EXTRA_ITEM_ID).orEmpty()
        playlistItemIds = intent.getStringArrayListExtra(EXTRA_PLAYLIST_ITEM_IDS) ?: arrayListOf()
        playlistItemTitles = intent.getStringArrayListExtra(EXTRA_PLAYLIST_ITEM_TITLES) ?: arrayListOf()
        playlistIndex = intent.getIntExtra(EXTRA_PLAYLIST_INDEX, -1)

        if (itemId.isBlank()) {
            Toast.makeText(this, getString(R.string.server_data_missing), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        rootView = findViewById(R.id.videoDetailScrollView)
        rootContent = findViewById(R.id.videoDetailRootContent)
        heroContainer = findViewById(R.id.videoHeroContainer)
        heroImage = findViewById(R.id.videoHeroImage)
        heroBottomBlend = findViewById(R.id.videoHeroBottomBlend)
        contentContainer = findViewById(R.id.videoDetailContentContainer)
        titleText = findViewById(R.id.videoTitleText)
        runtimeText = findViewById(R.id.videoRuntimeText)
        versionText = findViewById(R.id.videoVersionText)
        audioText = findViewById(R.id.videoAudioText)
        studioText = findViewById(R.id.videoStudioText)
        mediaTitleText = findViewById(R.id.mediaInfoTitleText)
        videoInfoText = findViewById(R.id.videoInfoBodyText)
        audioInfoText = findViewById(R.id.audioInfoBodyText)
        chaptersRecyclerView = findViewById(R.id.videoChaptersRecyclerView)
        favoriteButton = findViewById(R.id.videoFavoriteButton)
        playButton = findViewById(R.id.videoPlayButton)
        actionMoreButton = findViewById(R.id.videoActionMoreButton)
        val topBar = findViewById<ImageButton>(R.id.videoBackButton).parent as View

        findViewById<ImageButton>(R.id.videoBackButton).setDebouncedClickListener { finish() }
        findViewById<ImageButton>(R.id.videoMoreButton).setDebouncedClickListener {
            Toast.makeText(this, getString(R.string.more_actions_pending), Toast.LENGTH_SHORT).show()
        }
        playButton.setDebouncedClickListener {
            openPlayer()
        }
        actionMoreButton.setDebouncedClickListener {
            showVideoActionsSheet()
        }
        favoriteButton.setDebouncedClickListener { toggleFavorite() }

        EdgeToEdgeHelper.applyInsets(topBar, applyTop = true)
        EdgeToEdgeHelper.applyInsets(rootView, applyBottom = true)

        chaptersRecyclerView.layoutManager = LinearLayoutManager(this, RecyclerView.HORIZONTAL, false)
        setupHeroStretch()

        loadVideoDetail()
    }

    private fun loadVideoDetail() {
        networkExecutor.execute {
            val result = mediaRepository.fetchVideoDetail(connection, itemId)
            runOnUiThread {
                result.onSuccess { detail ->
                    bindDetail(detail)
                }.onFailure { error ->
                    Toast.makeText(
                        this,
                        userFriendlyErrorMessage(error, R.string.video_detail_load_failed),
                        Toast.LENGTH_SHORT
                    ).show()
                    finish()
                }
            }
        }
    }

    private fun bindDetail(detail: VideoDetailUiModel) {
        currentDetail = detail
        titleText.text = detail.title
        runtimeText.text = detail.runtimeLabel
        versionText.text = detail.versionLine
        audioText.text = detail.audioLine
        studioText.text = listOf(detail.subtitleLine, detail.studioLine)
            .filter { it.isNotBlank() }
            .joinToString("\n")
        mediaTitleText.text = detail.mediaTitleLine.ifBlank { detail.title }
        videoInfoText.text = detail.mediaInfoCards.getOrNull(0)?.lines?.joinToString("\n").orEmpty()
        audioInfoText.text = detail.mediaInfoCards.getOrNull(1)?.lines?.joinToString("\n").orEmpty()
        isFavorite = detail.isFavorite
        updateFavoriteIcon()
        updatePlayButtonText(detail.playbackPositionTicks)

        chaptersRecyclerView.adapter = MediaPosterAdapter(
            items = detail.chapters.mapIndexed { index, chapter ->
                MediaPosterUiModel(
                    id = "${detail.itemId}_chapter_$index",
                    title = chapter.title,
                    subtitle = chapter.startLabel,
                    style = ServerIconStyle.entries[index % ServerIconStyle.entries.size],
                    imageUrl = chapter.imageUrl
                )
            },
            cardLayout = R.layout.item_chapter_card,
            accessToken = connection.accessToken,
            onItemClick = { item ->
                val chapterIndex = item.id.substringAfterLast("_").toIntOrNull() ?: return@MediaPosterAdapter
                val chapter = detail.chapters.getOrNull(chapterIndex) ?: return@MediaPosterAdapter
                openPlayer(chapter.startPositionTicks / 10_000L)
            }
        )

        EmbyImageLoader.load(
            imageView = heroImage,
            url = detail.heroImageUrl,
            token = connection.accessToken,
            onFailure = {
                val fallbackColor = Color.parseColor(ServerIconStyle.INDIGO.fillColor)
                applyFallbackGradient(ServerIconStyle.INDIGO.fillColor)
                applyDynamicPlayButtonColor(fallbackColor, Color.WHITE)
            },
            onSuccess = { bitmap ->
                val fallbackColor = Color.parseColor(ServerIconStyle.INDIGO.fillColor)
                val buttonColors = MaterialYouColorHelper.extractButtonColors(bitmap, fallbackColor)
                applyDynamicGradient(buttonColors.seed)
                applyDynamicPlayButtonColor(buttonColors.container, buttonColors.content)
            }
        )
    }

    private fun openPlayer(startPositionMs: Long? = null) {
        val detail = currentDetail ?: return
        if (detail.playbackUrl.isNullOrBlank()) {
            Toast.makeText(this, getString(R.string.playback_url_missing), Toast.LENGTH_SHORT).show()
            return
        }
        if (!VideoPlayerLaunchGuard.tryAcquire()) return

        runCatching {
            PlayerActivity.closeActivePlayerForReplacement()
            playerLauncher.launch(
                AppNavigator.videoPlayerIntent(
                    context = this,
                    connection = connection,
                    detail = detail,
                    queue = VideoQueue(
                        itemIds = playlistItemIds,
                        itemTitles = playlistItemTitles,
                        currentIndex = playlistIndex
                    ),
                    itemId = itemId,
                    preferredStartPositionMs = startPositionMs ?: (detail.playbackPositionTicks / 10_000L)
                )
            )
        }.onFailure {
            VideoPlayerLaunchGuard.release()
        }
    }

    private fun toggleFavorite() {
        val target = !isFavorite
        networkExecutor.execute {
            val result = mediaRepository.setFavoriteState(connection, itemId, target)
            runOnUiThread {
                result.onSuccess {
                    isFavorite = target
                    updateFavoriteIcon()
                    Toast.makeText(
                        this,
                        if (isFavorite) getString(R.string.favorite_added) else getString(R.string.favorite_removed),
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

    private fun updateFavoriteIcon() {
        favoriteButton.setImageResource(
            if (isFavorite) R.drawable.ic_favorite_heart_filled else R.drawable.ic_favorite_heart_outline
        )
        favoriteButton.clearColorFilter()
    }

    private fun showVideoActionsSheet() {
        val detail = currentDetail ?: return
        val themedContext = DynamicColors.wrapContextIfAvailable(this)
        val sheetView = LayoutInflater.from(themedContext).inflate(R.layout.dialog_video_actions, null)
        val sheetRoot = sheetView.findViewById<LinearLayout>(R.id.videoActionsSheet)
        val posterImage = sheetView.findViewById<ImageView>(R.id.videoActionPosterImage)
        val actionTitle = sheetView.findViewById<TextView>(R.id.videoActionTitleText)
        val surfaceColor = MaterialColors.getColor(
            sheetView,
            com.google.android.material.R.attr.colorSurface,
            Color.WHITE
        )
        val onSurfaceVariant = MaterialColors.getColor(
            sheetView,
            com.google.android.material.R.attr.colorOnSurfaceVariant,
            MaterialColors.getColor(sheetView, com.google.android.material.R.attr.colorOnSurface, Color.BLACK)
        )
        val dialog = BottomSheetDialog(themedContext)

        sheetRoot.background = GradientDrawable().apply {
            val radius = 34f * resources.displayMetrics.density
            cornerRadii = floatArrayOf(radius, radius, radius, radius, 0f, 0f, 0f, 0f)
            setColor(surfaceColor)
        }
        actionTitle.text = detail.title
        sheetView.findViewById<View>(R.id.videoActionReplayRow).setDebouncedClickListener {
            Toast.makeText(this, getString(R.string.action_replay_from_start), Toast.LENGTH_SHORT).show()
        }
        sheetView.findViewById<View>(R.id.videoActionCastRow).setDebouncedClickListener {
            Toast.makeText(this, getString(R.string.action_cast), Toast.LENGTH_SHORT).show()
        }
        sheetView.findViewById<View>(R.id.videoActionExternalRow).setDebouncedClickListener {
            Toast.makeText(this, getString(R.string.action_external_player), Toast.LENGTH_SHORT).show()
        }
        sheetView.findViewById<View>(R.id.videoActionSourceRow).setDebouncedClickListener {
            Toast.makeText(this, getString(R.string.action_search_other_source), Toast.LENGTH_SHORT).show()
        }
        sheetView.findViewById<View>(R.id.videoActionDeleteRow).setDebouncedClickListener {
            dialog.dismiss()
            showDeleteVideoConfirmDialog()
        }

        EmbyImageLoader.load(
            imageView = posterImage,
            url = detail.heroImageUrl,
            token = connection.accessToken,
            onFailure = {
                AppIconPlaceholder.apply(
                    imageView = posterImage,
                    cornerRadiusDp = 18f,
                    backgroundColor = adjustAlpha(onSurfaceVariant, 0.12f)
                )
            }
        )

        dialog.setContentView(sheetView)
        dialog.show()
        dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.apply {
            background = MaterialShapeDrawable(
                ShapeAppearanceModel.builder()
                    .setTopLeftCornerSize(34f * resources.displayMetrics.density)
                    .setTopRightCornerSize(34f * resources.displayMetrics.density)
                    .build()
            ).apply {
                fillColor = android.content.res.ColorStateList.valueOf(Color.TRANSPARENT)
            }
            clipToOutline = true
        }
    }

    private fun showDeleteVideoConfirmDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_clear_played_state, null)
        dialogView.findViewById<TextView>(R.id.clearPlayedStateDialogTitle)
            .text = getString(R.string.delete_video_confirm_title)
        dialogView.findViewById<TextView>(R.id.clearPlayedStateDialogMessage)
            .text = getString(R.string.delete_video_confirm_message)
        dialogView.findViewById<TextView>(R.id.clearPlayedStateDialogConfirmButton)
            .text = getString(R.string.action_delete_video)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        dialog.window?.attributes = dialog.window?.attributes?.apply {
            dimAmount = 0.22f
        }

        dialogView.findViewById<TextView>(R.id.clearPlayedStateDialogCancelButton)
            .setDebouncedClickListener { dialog.dismiss() }
        dialogView.findViewById<TextView>(R.id.clearPlayedStateDialogConfirmButton)
            .setDebouncedClickListener {
                dialog.dismiss()
                deleteCurrentVideo()
            }
        dialog.show()
    }

    private fun deleteCurrentVideo() {
        networkExecutor.execute {
            val result = mediaRepository.deleteItem(connection, itemId)
            runOnUiThread {
                result.onSuccess {
                    Toast.makeText(this, getString(R.string.delete_video_success), Toast.LENGTH_SHORT).show()
                    setResult(RESULT_OK)
                    finish()
                }.onFailure { error ->
                    Toast.makeText(
                        this,
                        userFriendlyErrorMessage(error, R.string.delete_video_failed),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun applyFallbackGradient(baseHex: String) {
        applyDynamicGradient(Color.parseColor(baseHex))
    }

    private fun applyDynamicPlayButtonColor(containerColor: Int, contentColor: Int) {
        playButton.backgroundTintList = ColorStateList.valueOf(containerColor)
        playButton.setTextColor(contentColor)
        playButton.iconTint = ColorStateList.valueOf(contentColor)
        actionMoreButton.backgroundTintList = ColorStateList.valueOf(adjustAlpha(containerColor, 0.24f))
        actionMoreButton.imageTintList = ColorStateList.valueOf(contentColor)
    }

    private fun updatePlayButtonText(playbackPositionTicks: Long) {
        val playbackPositionMs = playbackPositionTicks / 10_000L
        if (playbackPositionMs > 0L) {
            playButton.text = getString(R.string.continue_play_from, formatMillis(playbackPositionMs))
            playButton.icon = getDrawable(R.drawable.ic_video_play_small)
        } else {
            playButton.text = getString(R.string.play)
            playButton.icon = getDrawable(R.drawable.ic_video_play_small)
        }
    }

    private fun formatMillis(positionMs: Long): String {
        val totalSeconds = positionMs / 1000L
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    private fun applyDynamicGradient(baseColor: Int) {
        val backdrop = MaterialYouColorHelper.extractBackdropColors(baseColor)
        rootView.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(backdrop.pageTop, backdrop.pageMid, backdrop.pageBottom)
        )
        rootContent.background = null
        heroBottomBlend.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(
                backdrop.heroBlendTop,
                adjustAlpha(backdrop.heroBlendBottom, 0.88f),
                adjustAlpha(backdrop.pageTop, 0.96f),
                backdrop.pageMid
            )
        )
        contentContainer.background = null
    }

    private fun setupHeroStretch() {
        heroImage.pivotY = 0f
        heroImage.pivotX = resources.displayMetrics.widthPixels / 2f
        rootView.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialPullY = event.rawY
                    isStretchingHero = false
                    false
                }

                MotionEvent.ACTION_MOVE -> {
                    val pullDistance = (event.rawY - initialPullY).coerceAtLeast(0f)
                    if (rootView.scrollY == 0 && (pullDistance > 0f || isStretchingHero)) {
                        isStretchingHero = true
                        applyHeroStretch(pullDistance)
                        true
                    } else {
                        false
                    }
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isStretchingHero) {
                        releaseHeroStretch()
                        isStretchingHero = false
                        true
                    } else {
                        false
                    }
                }

                else -> false
            }
        }
    }

    private fun applyHeroStretch(pullDistance: Float) {
        val damped = pullDistance * 0.42f
        val scaleBoost = (damped / heroContainer.height.coerceAtLeast(1)).coerceAtMost(0.26f)
        val scale = 1f + scaleBoost
        heroImage.scaleX = scale
        heroImage.scaleY = scale
        heroContainer.translationY = damped * 0.08f
        heroBottomBlend.translationY = damped * 0.12f
        contentContainer.translationY = damped * 0.04f
    }

    private fun releaseHeroStretch() {
        heroImage.animate().scaleX(1f).scaleY(1f).setDuration(220L).start()
        heroContainer.animate().translationY(0f).setDuration(220L).start()
        heroBottomBlend.animate().translationY(0f).setDuration(220L).start()
        contentContainer.animate().translationY(0f).setDuration(220L).start()
    }

    private fun adjustAlpha(color: Int, factor: Float): Int {
        val alpha = (Color.alpha(color) * factor).toInt()
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
    }

    companion object {
        const val EXTRA_ITEM_ID = "extra_item_id"
        const val EXTRA_PLAYLIST_ITEM_IDS = "extra_playlist_item_ids"
        const val EXTRA_PLAYLIST_ITEM_TITLES = "extra_playlist_item_titles"
        const val EXTRA_PLAYLIST_INDEX = "extra_playlist_index"
    }
}
