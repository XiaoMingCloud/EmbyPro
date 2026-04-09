package com.liujiaming.embypro

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {
    private lateinit var topBar: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EdgeToEdgeHelper.enable(this, lightSystemBars = GlobalThemeStore(this).loadTheme().lightSystemBars)
        setContentView(R.layout.activity_settings)
        supportActionBar?.hide()
        GlobalThemeManager.apply(this)

        topBar = findViewById(R.id.settingsTopBar)
        val currentThemeValueText = findViewById<TextView>(R.id.settingsThemeValueText)
        findViewById<ImageButton>(R.id.settingsBackButton).setDebouncedClickListener { finish() }
        findViewById<View>(R.id.settingsThemeEntry).setDebouncedClickListener {
            startActivity(Intent(this, ThemeSettingsActivity::class.java))
        }

        currentThemeValueText.text = GlobalThemeManager.currentThemeLabel(this)
        EdgeToEdgeHelper.applyInsets(topBar, applyTop = true)
    }

    override fun onResume() {
        super.onResume()
        GlobalThemeManager.apply(this)
        findViewById<TextView>(R.id.settingsThemeValueText).text = GlobalThemeManager.currentThemeLabel(this)
    }
}
