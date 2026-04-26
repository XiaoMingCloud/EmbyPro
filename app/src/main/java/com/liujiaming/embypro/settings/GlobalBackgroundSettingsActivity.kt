package com.liujiaming.embypro

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

class GlobalBackgroundSettingsActivity : AppCompatActivity() {
    private lateinit var topBar: View
    private lateinit var summaryText: TextView
    private lateinit var emptyText: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: GlobalBackgroundAdapter
    private val backgroundLibrary by lazy { GlobalBackgroundLibrary(this) }

    private val uploadLauncher = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNullOrEmpty()) return@registerForActivityResult
        val importedCount = backgroundLibrary.importImages(uris)
        refreshBackgrounds()
        if (importedCount > 0) {
            Toast.makeText(this, getString(R.string.settings_background_library_imported, importedCount), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EdgeToEdgeHelper.enable(this, lightSystemBars = GlobalThemeStore(this).loadTheme().lightSystemBars)
        setContentView(R.layout.activity_global_background_settings)
        supportActionBar?.hide()
        GlobalThemeManager.apply(this)

        topBar = findViewById(R.id.backgroundSettingsTopBar)
        summaryText = findViewById(R.id.backgroundSettingsSummaryText)
        emptyText = findViewById(R.id.backgroundSettingsEmptyText)
        recyclerView = findViewById(R.id.backgroundSettingsRecyclerView)

        findViewById<ImageButton>(R.id.backgroundSettingsBackButton).setDebouncedClickListener { finish() }
        findViewById<View>(R.id.backgroundSettingsUploadButton).setDebouncedClickListener {
            uploadLauncher.launch(arrayOf("image/*"))
        }

        recyclerView.layoutManager = GridLayoutManager(this, 2)
        adapter = GlobalBackgroundAdapter(
            items = mutableListOf(),
            onItemClick = { item ->
                backgroundLibrary.selectImage(item.absolutePath)
                GlobalThemeManager.apply(this)
                refreshBackgrounds()
                Toast.makeText(this, getString(R.string.settings_background_image_selected), Toast.LENGTH_SHORT).show()
            },
            onDeleteClick = { item ->
                backgroundLibrary.deleteImage(item.absolutePath)
                GlobalThemeManager.apply(this)
                refreshBackgrounds()
                Toast.makeText(this, getString(R.string.settings_background_image_deleted), Toast.LENGTH_SHORT).show()
            }
        )
        recyclerView.adapter = adapter

        EdgeToEdgeHelper.applyInsets(topBar, applyTop = true)
        EdgeToEdgeHelper.applyInsets(recyclerView, applyBottom = true)
        refreshBackgrounds()
    }

    override fun onResume() {
        super.onResume()
        GlobalThemeManager.apply(this)
        refreshBackgrounds()
    }

    private fun refreshBackgrounds() {
        val items = backgroundLibrary.listImages()
        adapter.submitItems(items)
        val uploadedCount = backgroundLibrary.imageCount()
        summaryText.text = if (uploadedCount == 0) {
            getString(R.string.settings_background_library_empty_summary)
        } else {
            getString(R.string.settings_background_library_summary, uploadedCount)
        }
        emptyText.visibility = View.GONE
        recyclerView.visibility = View.VISIBLE
    }
}
