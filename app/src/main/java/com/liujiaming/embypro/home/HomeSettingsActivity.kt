package com.liujiaming.embypro

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class HomeSettingsActivity : AppCompatActivity() {
    private val networkExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val embyApiService by lazy { EmbyApiService(this) }
    private val filterStore by lazy { HomeLibraryFilterStore(this) }

    private lateinit var baseUrl: String
    private lateinit var userId: String
    private lateinit var accessToken: String

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyText: TextView
    private lateinit var adapter: HomeLibraryFilterAdapter
    private val libraries = mutableListOf<MediaLibraryUiModel>()
    private val excludedIds = linkedSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EdgeToEdgeHelper.enable(this, lightSystemBars = true)
        setContentView(R.layout.activity_home_settings)
        supportActionBar?.hide()

        baseUrl = intent.getStringExtra(EXTRA_BASE_URL).orEmpty()
        userId = intent.getStringExtra(EXTRA_USER_ID).orEmpty()
        accessToken = intent.getStringExtra(EXTRA_ACCESS_TOKEN).orEmpty()

        if (baseUrl.isBlank() || userId.isBlank() || accessToken.isBlank()) {
            Toast.makeText(this, getString(R.string.server_data_missing), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        excludedIds.addAll(filterStore.loadExcludedLibraryIds(baseUrl, userId))
        val topBar = findViewById<View>(R.id.homeSettingsTopBar)
        recyclerView = findViewById(R.id.homeSettingsRecyclerView)
        emptyText = findViewById(R.id.homeSettingsEmptyText)

        findViewById<ImageButton>(R.id.homeSettingsBackButton).setDebouncedClickListener { finish() }
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = HomeLibraryFilterAdapter(libraries, excludedIds) { library, excluded ->
            filterStore.setExcluded(baseUrl, userId, library.id, excluded)
            adapter.updateExcluded(library.id, excluded)
        }
        recyclerView.adapter = adapter

        EdgeToEdgeHelper.applyInsets(topBar, applyTop = true)
        EdgeToEdgeHelper.applyInsets(recyclerView, applyBottom = true)
        loadLibraries()
    }

    override fun onDestroy() {
        super.onDestroy()
        networkExecutor.shutdownNow()
    }

    private fun loadLibraries() {
        networkExecutor.execute {
            val result = embyApiService.fetchMediaLibraries(baseUrl, userId, accessToken)
            runOnUiThread {
                result.onSuccess { items ->
                    libraries.clear()
                    libraries.addAll(items)
                    adapter.notifyDataSetChanged()
                    emptyText.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
                    recyclerView.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
                }.onFailure { error ->
                    recyclerView.visibility = View.GONE
                    emptyText.visibility = View.VISIBLE
                    emptyText.text = error.message ?: getString(R.string.home_settings_load_failed)
                }
            }
        }
    }

    companion object {
        const val EXTRA_BASE_URL = "extra_base_url"
        const val EXTRA_USER_ID = "extra_user_id"
        const val EXTRA_ACCESS_TOKEN = "extra_access_token"
    }
}
