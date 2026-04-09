package com.liujiaming.embypro

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class ThemeSettingsActivity : AppCompatActivity() {
    private lateinit var adapter: ThemeOptionAdapter
    private lateinit var topBar: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EdgeToEdgeHelper.enable(this, lightSystemBars = GlobalThemeStore(this).loadTheme().lightSystemBars)
        setContentView(R.layout.activity_theme_settings)
        supportActionBar?.hide()
        GlobalThemeManager.apply(this)

        topBar = findViewById(R.id.themeSettingsTopBar)
        val currentThemeValueText = findViewById<TextView>(R.id.themeSettingsValueText)
        val recyclerView = findViewById<RecyclerView>(R.id.themeSettingsRecyclerView)
        findViewById<ImageButton>(R.id.themeSettingsBackButton).setDebouncedClickListener { finish() }

        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = ThemeOptionAdapter(
            GlobalThemeOption.values().toList(),
            GlobalThemeStore(this).loadTheme()
        ) { option ->
            GlobalThemeStore(this).saveTheme(option)
            EdgeToEdgeHelper.enable(this, lightSystemBars = option.lightSystemBars)
            GlobalThemeManager.apply(this)
            currentThemeValueText.text = getString(option.labelRes)
            adapter.updateSelectedTheme(option)
        }
        recyclerView.adapter = adapter

        currentThemeValueText.text = GlobalThemeManager.currentThemeLabel(this)
        EdgeToEdgeHelper.applyInsets(topBar, applyTop = true)
        EdgeToEdgeHelper.applyInsets(recyclerView, applyBottom = true)
    }
}
