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

class HomeSettingsActivity : AppCompatActivity() {
    private val networkExecutor: ExecutorService = AppExecutors.io
    private val mediaRepository by lazy { MediaRepository(this) }
    private val preferenceStore by lazy { AppPreferenceStore(this) }
    private val sessionStore by lazy { ServerSessionStore(this) }
    private val serverRepository by lazy { ServerRepository(this) }

    private lateinit var connection: ServerConnection

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyText: TextView
    private lateinit var adapter: HomeLibraryFilterAdapter
    private val libraries = mutableListOf<MediaLibraryUiModel>()
    private val excludedIds = linkedSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EdgeToEdgeHelper.enable(this, lightSystemBars = GlobalThemeStore(this).loadTheme().lightSystemBars)
        setContentView(R.layout.activity_home_settings)
        supportActionBar?.hide()
        GlobalThemeManager.apply(this)

        connection = requireServerConnection(sessionStore, serverRepository) ?: return

        excludedIds.addAll(preferenceStore.loadExcludedHomeLibraryIds(connection.baseUrl, connection.userId))
        val topBar = findViewById<View>(R.id.homeSettingsTopBar)
        recyclerView = findViewById(R.id.homeSettingsRecyclerView)
        emptyText = findViewById(R.id.homeSettingsEmptyText)

        findViewById<ImageButton>(R.id.homeSettingsBackButton).setDebouncedClickListener { finish() }
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = HomeLibraryFilterAdapter(libraries, excludedIds) { library, excluded ->
            preferenceStore.setHomeLibraryExcluded(connection.baseUrl, connection.userId, library.id, excluded)
            adapter.updateExcluded(library.id, excluded)
        }
        recyclerView.adapter = adapter

        EdgeToEdgeHelper.applyInsets(topBar, applyTop = true)
        EdgeToEdgeHelper.applyInsets(recyclerView, applyBottom = true)
        loadLibraries()
    }

    override fun onResume() {
        super.onResume()
        GlobalThemeManager.apply(this)
        adapter.notifyDataSetChanged()
    }

    private fun loadLibraries() {
        networkExecutor.execute {
            val result = mediaRepository.fetchMediaLibraries(connection)
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
}
