package com.liujiaming.embypro

import android.content.ActivityNotFoundException
import android.content.Intent
import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.ColorUtils
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
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
    private lateinit var pageRoot: FrameLayout
    private lateinit var rootContent: LinearLayout
    private lateinit var heroContainer: View
    private lateinit var heroImage: ImageView
    private lateinit var heroBlurImage: ImageView
    private lateinit var heroColorDiffuse: View
    private lateinit var heroDimOverlay: View
    private lateinit var heroBottomBlend: View
    private lateinit var heroTopBar: LinearLayout
    private lateinit var collapsedAppBar: LinearLayout
    private lateinit var contentContainer: LinearLayout
    private lateinit var titleText: TextView
    private lateinit var runtimeText: TextView
    private lateinit var versionLabelText: TextView
    private lateinit var versionText: TextView
    private lateinit var audioLabelText: TextView
    private lateinit var audioText: TextView
    private lateinit var studioText: TextView
    private lateinit var packedByText: TextView
    private lateinit var chaptersHeaderText: TextView
    private lateinit var mediaInfoHeaderText: TextView
    private lateinit var mediaTitleText: TextView
    private lateinit var videoInfoCard: LinearLayout
    private lateinit var audioInfoCard: LinearLayout
    private lateinit var videoInfoCardTitleText: TextView
    private lateinit var audioInfoCardTitleText: TextView
    private lateinit var videoInfoText: TextView
    private lateinit var audioInfoText: TextView
    private lateinit var chaptersRecyclerView: RecyclerView
    private lateinit var favoriteButton: ImageButton
    private lateinit var watchedButton: ImageButton
    private lateinit var collapsedFavoriteButton: ImageButton
    private lateinit var collapsedWatchedButton: ImageButton
    private lateinit var collapsedTitleText: TextView
    private lateinit var playButton: MaterialButton
    private lateinit var actionMoreButton: ImageButton
    private lateinit var actionMoreCard: MaterialCardView

    private var isFavorite = false
    private var isPlayed = false
    private var currentDetail: VideoDetailUiModel? = null
    private var initialPullY = 0f
    private var isStretchingHero = false
    private var heroBaseHeight = 0
    private var currentPageBackgroundColor = Color.parseColor("#EAF3F6")
    private var currentCoverScheme = CoverColorExtractor.defaultScheme()
    private var currentImageKey: String = ""
    private var colorAnimator: ValueAnimator? = null

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

        pageRoot = findViewById(R.id.videoDetailPageRoot)
        rootView = findViewById(R.id.videoDetailScrollView)
        rootContent = findViewById(R.id.videoDetailRootContent)
        heroContainer = findViewById(R.id.videoHeroContainer)
        heroImage = findViewById(R.id.videoHeroImage)
        heroBlurImage = findViewById(R.id.videoHeroBlurImage)
        heroColorDiffuse = findViewById(R.id.videoHeroColorDiffuse)
        heroDimOverlay = findViewById(R.id.videoHeroDimOverlay)
        heroBottomBlend = findViewById(R.id.videoHeroBottomBlend)
        heroTopBar = findViewById(R.id.videoTopBar)
        collapsedAppBar = findViewById(R.id.videoCollapsedAppBar)
        contentContainer = findViewById(R.id.videoDetailContentContainer)
        titleText = findViewById(R.id.videoTitleText)
        runtimeText = findViewById(R.id.videoRuntimeText)
        versionLabelText = findViewById(R.id.videoVersionLabelText)
        versionText = findViewById(R.id.videoVersionText)
        audioLabelText = findViewById(R.id.videoAudioLabelText)
        audioText = findViewById(R.id.videoAudioText)
        studioText = findViewById(R.id.videoStudioText)
        packedByText = findViewById(R.id.videoPackedByText)
        chaptersHeaderText = findViewById(R.id.videoChaptersHeaderText)
        mediaInfoHeaderText = findViewById(R.id.videoMediaInfoHeaderText)
        mediaTitleText = findViewById(R.id.mediaInfoTitleText)
        videoInfoCard = findViewById(R.id.videoInfoCard)
        audioInfoCard = findViewById(R.id.audioInfoCard)
        videoInfoCardTitleText = findViewById(R.id.videoInfoCardTitleText)
        audioInfoCardTitleText = findViewById(R.id.audioInfoCardTitleText)
        videoInfoText = findViewById(R.id.videoInfoBodyText)
        audioInfoText = findViewById(R.id.audioInfoBodyText)
        chaptersRecyclerView = findViewById(R.id.videoChaptersRecyclerView)
        watchedButton = findViewById(R.id.videoWatchedButton)
        favoriteButton = findViewById(R.id.videoFavoriteButton)
        collapsedFavoriteButton = findViewById(R.id.videoCollapsedFavoriteButton)
        collapsedWatchedButton = findViewById(R.id.videoCollapsedWatchedButton)
        collapsedTitleText = findViewById(R.id.videoCollapsedTitleText)
        playButton = findViewById(R.id.videoPlayButton)
        actionMoreButton = findViewById(R.id.videoActionMoreButton)
        actionMoreCard = findViewById(R.id.videoActionMoreCard)

        configureHeroHeight()
        renderCoverScheme(currentCoverScheme)

        prepareDynamicActionButtons()

        findViewById<ImageButton>(R.id.videoBackButton).setDebouncedClickListener { finish() }
        findViewById<ImageButton>(R.id.videoCollapsedBackButton).setDebouncedClickListener { finish() }
        playButton.setDebouncedClickListener {
            openPlayer()
        }
        actionMoreButton.setDebouncedClickListener {
            showVideoActionsSheet()
        }
        favoriteButton.setDebouncedClickListener { toggleFavorite() }
        collapsedFavoriteButton.setDebouncedClickListener { toggleFavorite() }
        watchedButton.setDebouncedClickListener { clearPlayedStateFromDetail() }
        collapsedWatchedButton.setDebouncedClickListener { clearPlayedStateFromDetail() }

        EdgeToEdgeHelper.applyInsets(heroTopBar, applyTop = true)
        EdgeToEdgeHelper.applyInsets(collapsedAppBar, applyTop = true)
        EdgeToEdgeHelper.applyInsets(rootView, applyBottom = true)

        chaptersRecyclerView.layoutManager = LinearLayoutManager(this, RecyclerView.HORIZONTAL, false)
        setupHeroStretch()
        rootView.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            updateTopBarState(scrollY)
        }
        updateTopBarState(0)

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
        prepareDynamicActionButtons()
        titleText.text = detail.title
        collapsedTitleText.text = detail.title
        runtimeText.text = detail.runtimeLabel
        versionText.text = detail.versionLine
        audioText.text = cleanAudioLine(detail.audioLine)
        runtimeText.visibility = if (detail.runtimeLabel.isBlank()) View.GONE else View.VISIBLE
        versionText.visibility = if (detail.versionLine.isBlank()) View.GONE else View.VISIBLE
        audioText.visibility = if (audioText.text.isNullOrBlank()) View.GONE else View.VISIBLE
        studioText.text = listOf(detail.subtitleLine, detail.studioLine)
            .filter { it.isNotBlank() }
            .joinToString("\n")
        studioText.visibility = if (studioText.text.isNullOrBlank()) View.GONE else View.VISIBLE
        mediaTitleText.text = detail.mediaTitleLine.ifBlank { detail.title }
        videoInfoText.text = detail.mediaInfoCards.getOrNull(0)?.lines?.joinToString("\n").orEmpty()
        audioInfoText.text = detail.mediaInfoCards.getOrNull(1)?.lines?.joinToString("\n").orEmpty()
        isFavorite = detail.isFavorite
        isPlayed = detail.isPlayed || detail.playbackPositionTicks > 0L
        updateFavoriteButtons()
        updateWatchedButtons()
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
                currentImageKey = detail.heroImageUrl ?: detail.itemId
                heroBlurImage.setImageDrawable(null)
                heroBlurImage.alpha = 0f
                animateToCoverScheme(CoverColorExtractor.defaultScheme(currentImageKey))
                showDynamicActionButtons()
            },
            onSuccess = { bitmap ->
                val imageKey = detail.heroImageUrl ?: detail.itemId
                currentImageKey = imageKey
                applyHeroDiffusion(bitmap)
                CoverColorExtractor.extractColors(imageKey, bitmap) { scheme ->
                    if (currentImageKey != imageKey) return@extractColors
                    animateToCoverScheme(scheme)
                    showDynamicActionButtons()
                }
            }
        )
        updateTopBarState(rootView.scrollY)
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
                    updateFavoriteButtons()
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

    private fun updateFavoriteButtons() {
        val iconRes = if (isFavorite) R.drawable.ic_favorite_heart_filled else R.drawable.ic_favorite_heart_outline
        favoriteButton.setImageResource(iconRes)
        collapsedFavoriteButton.setImageResource(iconRes)
        favoriteButton.imageTintList = ColorStateList.valueOf(Color.WHITE)
        collapsedFavoriteButton.imageTintList = ColorStateList.valueOf(resolveCollapsedBarContentColor())
    }

    private fun updateWatchedButtons() {
        val enabledAlpha = if (isPlayed) 1f else 0.52f
        watchedButton.alpha = enabledAlpha
        collapsedWatchedButton.alpha = enabledAlpha
        watchedButton.imageTintList = ColorStateList.valueOf(Color.WHITE)
        collapsedWatchedButton.imageTintList = ColorStateList.valueOf(resolveCollapsedBarContentColor())
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
            dialog.dismiss()
            openPlayer(startPositionMs = 0L)
        }
        sheetView.findViewById<View>(R.id.videoActionCastRow).setDebouncedClickListener {
            Toast.makeText(this, getString(R.string.action_cast), Toast.LENGTH_SHORT).show()
        }
        sheetView.findViewById<View>(R.id.videoActionExternalRow).setDebouncedClickListener {
            dialog.dismiss()
            openExternalPlayer()
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

    private fun openExternalPlayer() {
        val detail = currentDetail ?: return
        val rawUrl = detail.playbackUrl
        if (rawUrl.isNullOrBlank()) {
            Toast.makeText(this, getString(R.string.playback_url_missing), Toast.LENGTH_SHORT).show()
            return
        }
        val playbackUri = Uri.parse(appendQueryParameter(rawUrl, "api_key", connection.accessToken))
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(playbackUri, "video/*")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching {
            startActivity(intent)
        }.onFailure { error ->
            if (error is ActivityNotFoundException) {
                Toast.makeText(this, getString(R.string.player_error), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, userFriendlyErrorMessage(error, R.string.player_error), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun configureHeroHeight() {
        val targetHeight = (resources.displayMetrics.heightPixels * 0.48f).toInt()
        heroBaseHeight = targetHeight.coerceAtLeast((320f * resources.displayMetrics.density).toInt())
        heroContainer.layoutParams = heroContainer.layoutParams.apply {
            height = heroBaseHeight
        }
        heroBottomBlend.layoutParams = heroBottomBlend.layoutParams.apply {
            height = (heroBaseHeight * 0.54f).toInt()
        }
    }

    private fun updateTopBarState(scrollY: Int) {
        val collapseStart = (heroBaseHeight * 0.44f).toInt()
        val collapseRange = (heroBaseHeight - collapseStart).coerceAtLeast(1)
        val progress = ((scrollY - collapseStart).toFloat() / collapseRange.toFloat()).coerceIn(0f, 1f)

        collapsedAppBar.alpha = progress
        collapsedAppBar.translationY = (-18f * resources.displayMetrics.density) * (1f - progress)
        heroTopBar.alpha = (1f - progress * 1.18f).coerceIn(0f, 1f)

        val collapsedBackground = ColorUtils.setAlphaComponent(
            currentPageBackgroundColor,
            (235 * progress).toInt().coerceIn(0, 235)
        )
        collapsedAppBar.setBackgroundColor(collapsedBackground)
        val collapsedColor = resolveCollapsedBarContentColor()
        collapsedTitleText.setTextColor(collapsedColor)
        findViewById<ImageButton>(R.id.videoCollapsedBackButton).imageTintList = ColorStateList.valueOf(collapsedColor)
        collapsedFavoriteButton.imageTintList = ColorStateList.valueOf(collapsedColor)
        collapsedWatchedButton.imageTintList = ColorStateList.valueOf(collapsedColor)

        heroImage.translationY = -scrollY * 0.08f
        heroDimOverlay.alpha = 0.16f + progress * 0.14f
    }

    private fun resolveCollapsedBarContentColor(): Int {
        return if (ColorUtils.calculateLuminance(currentPageBackgroundColor) > 0.62) {
            getColor(R.color.text_primary)
        } else {
            Color.WHITE
        }
    }

    private fun clearPlayedStateFromDetail() {
        if (!isPlayed && (currentDetail?.playbackPositionTicks ?: 0L) <= 0L) {
            Toast.makeText(this, getString(R.string.video_detail_no_watch_state), Toast.LENGTH_SHORT).show()
            return
        }
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_clear_played_state, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialogView.findViewById<TextView>(R.id.clearPlayedStateDialogCancelButton)
            .setDebouncedClickListener { dialog.dismiss() }
        dialogView.findViewById<TextView>(R.id.clearPlayedStateDialogConfirmButton)
            .setDebouncedClickListener {
                dialog.dismiss()
                networkExecutor.execute {
                    val result = mediaRepository.clearPlayedState(connection, itemId)
                    runOnUiThread {
                        result.onSuccess {
                            isPlayed = false
                            currentDetail = currentDetail?.copy(playbackPositionTicks = 0L, isPlayed = false)
                            updateWatchedButtons()
                            loadVideoDetail()
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
        dialog.show()
    }

    private fun applyFallbackGradient(baseHex: String) {
        applyDynamicGradient(Color.parseColor(baseHex))
    }

    private fun prepareDynamicActionButtons() {
        playButton.clearAnimation()
        playButton.alpha = 0f
        playButton.isEnabled = false
        playButton.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
        playButton.setTextColor(Color.TRANSPARENT)
        playButton.iconTint = ColorStateList.valueOf(Color.TRANSPARENT)
    }

    private fun showDynamicActionButtons() {
        playButton.isEnabled = true
        if (playButton.alpha >= 1f) return
        playButton.animate()
            .alpha(1f)
            .setDuration(160L)
            .start()
    }

    private fun applyDynamicPlayButtonColor(containerColor: Int, contentColor: Int) {
        playButton.backgroundTintList = ColorStateList.valueOf(containerColor)
        playButton.setTextColor(contentColor)
        playButton.iconTint = ColorStateList.valueOf(contentColor)
        actionMoreCard.setCardBackgroundColor(currentCoverScheme.cardColor)
        actionMoreCard.strokeColor = currentCoverScheme.cardStrokeColor
        actionMoreButton.imageTintList = ColorStateList.valueOf(currentCoverScheme.textPrimaryColor)
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

    private fun cleanAudioLine(rawLine: String): String {
        if (rawLine.isBlank()) return ""
        return rawLine
            .replace(Regex("\\bund\\b", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\baac\\b", RegexOption.IGNORE_CASE)) { match -> if (match.range.first == 0) "AAC" else "" }
            .replace(Regex("\\s+AAC\\s*$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+"), " ")
            .trim()
        return rawLine
            .replace("und ", "")
            .replace(" und", "")
            .replace(Regex("\\baac\\b", RegexOption.IGNORE_CASE)) { match ->
                if (match.range.first == 0) "AAC" else ""
            }
            .replace(Regex("\\s+AAC\\s*$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+"), " ")
            .trim()
            .replace("stereo (", "stereo（")
            .replace(")", "）")
    }

    private fun applyDynamicGradient(baseColor: Int) {
        val backdrop = MaterialYouColorHelper.extractBackdropColors(baseColor)
        currentPageBackgroundColor = backdrop.pageUpper
        rootView.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(
                backdrop.pageTop,
                backdrop.pageUpper,
                backdrop.pageMid,
                backdrop.pageBottom
            )
        )
        pageRoot.background = rootView.background
        rootContent.background = null
        heroBottomBlend.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(
                backdrop.heroBlendTop,
                backdrop.heroBlendUpper,
                backdrop.heroBlendMid,
                backdrop.heroBlendBottom,
                backdrop.pageUpper,
                backdrop.pageMid,
                backdrop.pageBottom
            )
        )
        heroColorDiffuse.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(
                backdrop.glowTop,
                backdrop.glowMid,
                backdrop.glowBottom,
                adjustAlpha(backdrop.pageUpper, 0.14f)
            )
        )
        contentContainer.background = null
        updateTopBarState(rootView.scrollY)
    }

    private fun applyHeroDiffusion(bitmap: android.graphics.Bitmap) {
        heroBlurImage.setImageBitmap(bitmap)
        heroBlurImage.scaleX = 1.16f
        heroBlurImage.scaleY = 1.16f
        heroBlurImage.translationY = heroBaseHeight * 0.03f
        heroBlurImage.alpha = 0.52f
        heroBlurImage.setRenderEffect(RenderEffect.createBlurEffect(42f, 42f, Shader.TileMode.CLAMP))
    }

    private fun animateToCoverScheme(targetScheme: CoverColorScheme) {
        val startScheme = currentCoverScheme
        colorAnimator?.cancel()
        if (startScheme == targetScheme) {
            renderCoverScheme(targetScheme)
            return
        }
        colorAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 320L
            interpolator = FastOutSlowInInterpolator()
            addUpdateListener { animator ->
                renderCoverScheme(
                    CoverColorExtractor.blendSchemes(
                        startScheme,
                        targetScheme,
                        animator.animatedFraction
                    )
                )
            }
            start()
        }
    }

    private fun renderCoverScheme(scheme: CoverColorScheme) {
        currentCoverScheme = scheme
        currentPageBackgroundColor = scheme.surfaceColor
        pageRoot.setBackgroundColor(scheme.surfaceColor)
        rootView.setBackgroundColor(scheme.surfaceColor)
        rootContent.setBackgroundColor(scheme.surfaceColor)
        contentContainer.background = null

        val overlay25 = adjustAlpha(scheme.overlayColor, 0.25f)
        val overlay55 = adjustAlpha(scheme.overlayColor, 0.55f)
        heroBottomBlend.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.TRANSPARENT, overlay25, overlay55, scheme.surfaceColor, scheme.surfaceColor)
        ).apply {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                setColors(
                    intArrayOf(Color.TRANSPARENT, overlay25, overlay55, scheme.surfaceColor, scheme.surfaceColor),
                    floatArrayOf(0f, 0.35f, 0.65f, 0.90f, 1f)
                )
            }
        }

        heroColorDiffuse.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(
                Color.TRANSPARENT,
                adjustAlpha(scheme.surfaceColor, 0.10f),
                adjustAlpha(scheme.surfaceColor, 0.18f),
                adjustAlpha(scheme.surfaceColor, 0.24f)
            )
        ).apply {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                setColors(
                    intArrayOf(
                        Color.TRANSPARENT,
                        adjustAlpha(scheme.surfaceColor, 0.10f),
                        adjustAlpha(scheme.surfaceColor, 0.18f),
                        adjustAlpha(scheme.surfaceColor, 0.24f)
                    ),
                    floatArrayOf(0f, 0.42f, 0.74f, 1f)
                )
            }
        }

        heroDimOverlay.setBackgroundColor(scheme.heroFogColor)
        applyDynamicPlayButtonColor(scheme.buttonColor, scheme.buttonContentColor)
        applyContentColors(scheme)
        updateTopBarState(rootView.scrollY)
    }

    private fun applyContentColors(scheme: CoverColorScheme) {
        titleText.setTextColor(scheme.textPrimaryColor)
        runtimeText.setTextColor(scheme.textSecondaryColor)
        versionLabelText.setTextColor(scheme.textPrimaryColor)
        versionText.setTextColor(scheme.textPrimaryColor)
        audioLabelText.setTextColor(scheme.textPrimaryColor)
        audioText.setTextColor(scheme.textPrimaryColor)
        studioText.setTextColor(scheme.textSecondaryColor)
        packedByText.setTextColor(scheme.textPrimaryColor)
        chaptersHeaderText.setTextColor(scheme.textPrimaryColor)
        mediaInfoHeaderText.setTextColor(scheme.textPrimaryColor)
        mediaTitleText.setTextColor(scheme.textPrimaryColor)
        videoInfoCardTitleText.setTextColor(scheme.textPrimaryColor)
        audioInfoCardTitleText.setTextColor(scheme.textPrimaryColor)
        videoInfoText.setTextColor(scheme.textSecondaryColor)
        audioInfoText.setTextColor(scheme.textSecondaryColor)
        videoInfoCard.background = createInfoCardBackground(scheme)
        audioInfoCard.background = createInfoCardBackground(scheme)
    }

    private fun createInfoCardBackground(scheme: CoverColorScheme): GradientDrawable {
        return GradientDrawable().apply {
            cornerRadius = 16f * resources.displayMetrics.density
            setColor(scheme.cardColor)
            setStroke(
                (1f * resources.displayMetrics.density).toInt().coerceAtLeast(1),
                scheme.cardStrokeColor
            )
        }
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

    private fun appendQueryParameter(url: String, key: String, value: String): String {
        if (url.isBlank() || value.isBlank()) return url
        if (url.contains("$key=")) return url
        val separator = if (url.contains("?")) "&" else "?"
        return url + separator + key + "=" + Uri.encode(value)
    }

    companion object {
        const val EXTRA_ITEM_ID = "extra_item_id"
        const val EXTRA_PLAYLIST_ITEM_IDS = "extra_playlist_item_ids"
        const val EXTRA_PLAYLIST_ITEM_TITLES = "extra_playlist_item_titles"
        const val EXTRA_PLAYLIST_INDEX = "extra_playlist_index"
    }
}
