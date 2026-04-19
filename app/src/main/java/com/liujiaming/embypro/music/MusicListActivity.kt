package com.liujiaming.embypro

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MusicListActivity : AppCompatActivity() {
    private val sessionStore by lazy { ServerSessionStore(this) }
    private val embyApiService by lazy { EmbyApiService(this) }

    private lateinit var baseUrl: String
    private lateinit var userId: String
    private lateinit var accessToken: String
    private lateinit var browseType: MusicBrowseType

    private var containerId: String? = null
    private var containerTitle: String? = null
    private var lastLibraryId: String? = null
    private var isLoadingPage = false

    private lateinit var recyclerView: RecyclerView
    private lateinit var loadingContainer: View
    private lateinit var emptyContainer: View
    private lateinit var errorContainer: View
    private lateinit var errorTextView: TextView
    private lateinit var pageTitleView: TextView
    private lateinit var pageSubtitleView: TextView
    private lateinit var partitionRow: View
    private lateinit var partitionTextView: TextView
    private lateinit var adapter: MusicListAdapter

    private val items = mutableListOf<MusicListEntryUiModel>()

    private val stateListener = MusicLibraryStateListener { state ->
        renderLibraryState(state)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EdgeToEdgeHelper.enable(this, lightSystemBars = false)
        setContentView(R.layout.activity_music_list)
        supportActionBar?.hide()

        resolveSessionParams()
        browseType = MusicBrowseType.valueOf(
            intent.getStringExtra(EXTRA_BROWSE_TYPE).orEmpty().ifBlank { MusicBrowseType.SONGS.name }
        )
        containerId = intent.getStringExtra(EXTRA_CONTAINER_ID)
        containerTitle = intent.getStringExtra(EXTRA_CONTAINER_TITLE)

        val topBar = findViewById<View>(R.id.musicListTopBar)
        recyclerView = findViewById(R.id.musicListRecyclerView)
        loadingContainer = findViewById(R.id.musicListLoadingContainer)
        emptyContainer = findViewById(R.id.musicListEmptyContainer)
        errorContainer = findViewById(R.id.musicListErrorContainer)
        errorTextView = findViewById(R.id.musicListErrorText)
        pageTitleView = findViewById(R.id.musicListTitleText)
        pageSubtitleView = findViewById(R.id.musicListSubtitleText)
        partitionRow = findViewById(R.id.musicListPartitionRow)
        partitionTextView = findViewById(R.id.musicListPartitionText)

        findViewById<ImageButton>(R.id.musicListBackButton).setDebouncedClickListener { finish() }
        partitionRow.setDebouncedClickListener { showLibraryPicker() }
        findViewById<View>(R.id.musicListRetryButton).setDebouncedClickListener {
            MusicLibraryRepository.connect(this, baseUrl, userId, accessToken, forceRefresh = true)
        }

        adapter = MusicListAdapter(items, accessToken) { entry ->
            if (entry.kind == MusicEntryKind.SONG) {
                openPlayer(entry)
            } else {
                openNestedList(entry)
            }
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        EdgeToEdgeHelper.applyInsets(topBar, applyTop = true)
        EdgeToEdgeHelper.applyInsets(recyclerView, applyBottom = true)
    }

    override fun onStart() {
        super.onStart()
        MusicLibraryRepository.subscribe(stateListener)
        MusicLibraryRepository.connect(this, baseUrl, userId, accessToken)
    }

    override fun onStop() {
        MusicLibraryRepository.unsubscribe(stateListener)
        super.onStop()
    }

    private fun resolveSessionParams() {
        baseUrl = intent.getStringExtra(EXTRA_BASE_URL).orEmpty()
        userId = intent.getStringExtra(EXTRA_USER_ID).orEmpty()
        accessToken = intent.getStringExtra(EXTRA_ACCESS_TOKEN).orEmpty()

        if (baseUrl.isNotBlank() && userId.isNotBlank() && accessToken.isNotBlank()) return

        val activeServer = sessionStore.loadServers().firstOrNull() ?: return
        baseUrl = baseUrl.ifBlank { embyApiService.buildBaseUrl(activeServer.address, activeServer.port) }
        userId = userId.ifBlank { activeServer.userId }
        accessToken = accessToken.ifBlank { activeServer.accessToken }
    }

    private fun renderLibraryState(state: MusicLibraryState) {
        if (state.errorMessage != null && state.musicLibraries.isEmpty()) {
            showError(state.errorMessage)
            return
        }

        if (state.isLoadingLibraries && state.musicLibraries.isEmpty()) {
            showLoading()
            return
        }

        val currentLibrary = state.currentMusicLibrary ?: run {
            showEmpty()
            return
        }

        partitionTextView.text = MusicLibraryRepository.displayName(currentLibrary)

        if (lastLibraryId != null && lastLibraryId != currentLibrary.id && !containerId.isNullOrBlank()) {
            containerId = null
            containerTitle = null
        }

        if (lastLibraryId != currentLibrary.id || items.isEmpty()) {
            lastLibraryId = currentLibrary.id
            loadPage(currentLibrary.id)
        }
    }

    private fun loadPage(libraryId: String) {
        if (isLoadingPage) return
        isLoadingPage = true
        showLoading()

        AppExecutors.io.execute {
            val result = embyApiService.fetchMusicBrowsePage(
                baseUrl = baseUrl,
                userId = userId,
                accessToken = accessToken,
                libraryId = libraryId,
                browseType = browseType,
                containerId = containerId,
                containerTitle = containerTitle
            )
            runOnUiThread {
                isLoadingPage = false
                result.onSuccess { page ->
                    pageTitleView.text = page.title
                    pageSubtitleView.text = page.subtitle
                    adapter.submitItems(page.items)
                    if (page.items.isEmpty()) {
                        showEmpty()
                    } else {
                        showContent()
                    }
                }.onFailure { error ->
                    showError(error.message ?: getString(R.string.music_list_load_failed))
                }
            }
        }
    }

    private fun openNestedList(entry: MusicListEntryUiModel) {
        startActivity(
            Intent(this, MusicListActivity::class.java)
                .putExtra(EXTRA_BROWSE_TYPE, entry.browseType.name)
                .putExtra(EXTRA_CONTAINER_ID, entry.id)
                .putExtra(EXTRA_CONTAINER_TITLE, entry.title)
                .putExtra(EXTRA_BASE_URL, baseUrl)
                .putExtra(EXTRA_USER_ID, userId)
                .putExtra(EXTRA_ACCESS_TOKEN, accessToken)
        )
    }

    private fun openPlayer(entry: MusicListEntryUiModel) {
        val queue = items.filter { it.kind == MusicEntryKind.SONG }
        val currentIndex = queue.indexOfFirst { it.id == entry.id }
        if (currentIndex < 0) return

        startActivity(
            Intent(this, MusicPlayerActivity::class.java)
                .putExtra(MusicPlayerActivity.EXTRA_BASE_URL, baseUrl)
                .putExtra(MusicPlayerActivity.EXTRA_USER_ID, userId)
                .putExtra(MusicPlayerActivity.EXTRA_ACCESS_TOKEN, accessToken)
                .putExtra(MusicPlayerActivity.EXTRA_LIBRARY_ID, lastLibraryId)
                .putExtra(MusicPlayerActivity.EXTRA_QUEUE_TITLE, pageTitleView.text.toString())
                .putStringArrayListExtra(
                    MusicPlayerActivity.EXTRA_QUEUE_IDS,
                    ArrayList(queue.map { it.id })
                )
                .putStringArrayListExtra(
                    MusicPlayerActivity.EXTRA_QUEUE_TITLES,
                    ArrayList(queue.map { it.title })
                )
                .putStringArrayListExtra(
                    MusicPlayerActivity.EXTRA_QUEUE_SUBTITLES,
                    ArrayList(queue.map { it.subtitle })
                )
                .putStringArrayListExtra(
                    MusicPlayerActivity.EXTRA_QUEUE_IMAGES,
                    ArrayList(queue.map { it.imageUrl.orEmpty() })
                )
                .putExtra(MusicPlayerActivity.EXTRA_QUEUE_INDEX, currentIndex)
        )
    }

    private fun showLibraryPicker() {
        val state = MusicLibraryRepository.currentState()
        if (state.musicLibraries.isEmpty()) return

        val labels = state.musicLibraries.map { MusicLibraryRepository.displayName(it) }.toTypedArray()
        val selectedIndex = state.musicLibraries.indexOfFirst { it.id == state.currentLibraryId }.coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle(R.string.music_settings_partition_dialog_title)
            .setSingleChoiceItems(labels, selectedIndex) { dialog, which ->
                MusicLibraryRepository.selectLibrary(state.musicLibraries[which].id)
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showLoading() {
        recyclerView.visibility = View.GONE
        loadingContainer.visibility = View.VISIBLE
        emptyContainer.visibility = View.GONE
        errorContainer.visibility = View.GONE
    }

    private fun showEmpty() {
        recyclerView.visibility = View.GONE
        loadingContainer.visibility = View.GONE
        emptyContainer.visibility = View.VISIBLE
        errorContainer.visibility = View.GONE
    }

    private fun showError(message: String) {
        recyclerView.visibility = View.GONE
        loadingContainer.visibility = View.GONE
        emptyContainer.visibility = View.GONE
        errorContainer.visibility = View.VISIBLE
        errorTextView.text = message
    }

    private fun showContent() {
        recyclerView.visibility = View.VISIBLE
        loadingContainer.visibility = View.GONE
        emptyContainer.visibility = View.GONE
        errorContainer.visibility = View.GONE
    }

    companion object {
        const val EXTRA_BROWSE_TYPE = "extra_browse_type"
        const val EXTRA_CONTAINER_ID = "extra_container_id"
        const val EXTRA_CONTAINER_TITLE = "extra_container_title"
        const val EXTRA_BASE_URL = "extra_base_url"
        const val EXTRA_USER_ID = "extra_user_id"
        const val EXTRA_ACCESS_TOKEN = "extra_access_token"
    }
}
