package com.liujiaming.embypro

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * Settings activity for application-level configurations.
 * Allows users to customize font color, background image library, and access music/home settings.
 */
class SettingsActivity : AppCompatActivity() {
    private lateinit var topBar: View
    private val sessionStore by lazy { ServerSessionStore(this) }
    private val serverRepository by lazy { ServerRepository(this) }
    private val themeStore by lazy { GlobalThemeStore(this) }
    private lateinit var connection: ServerConnection
    private lateinit var backgroundValueText: TextView

    private val backgroundLibrary by lazy { GlobalBackgroundLibrary(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EdgeToEdgeHelper.enable(this, lightSystemBars = GlobalThemeStore(this).loadTheme().lightSystemBars)
        setContentView(R.layout.activity_settings)
        supportActionBar?.hide()
        GlobalThemeManager.apply(this)
        connection = requireServerConnection(sessionStore, serverRepository) ?: return

        topBar = findViewById(R.id.settingsTopBar)
        val currentThemeValueText = findViewById<TextView>(R.id.settingsThemeValueText)
        backgroundValueText = findViewById(R.id.settingsBackgroundImageValueText)
        findViewById<ImageButton>(R.id.settingsBackButton).setDebouncedClickListener { finish() }
        findViewById<View>(R.id.settingsThemeEntry).setDebouncedClickListener {
            startActivity(Intent(this, ThemeSettingsActivity::class.java))
        }
        findViewById<View>(R.id.settingsBackgroundImageEntry).setDebouncedClickListener {
            startActivity(Intent(this, GlobalBackgroundSettingsActivity::class.java))
        }
        findViewById<View>(R.id.settingsBackgroundImageEntry).setOnLongClickListener {
            clearBackgroundImage()
            true
        }
        findViewById<View>(R.id.settingsMusicEntry).setDebouncedClickListener {
            AppNavigator.openMusicSettings(this, connection)
        }
        findViewById<View>(R.id.settingsHomeDisplayEntry).setDebouncedClickListener {
            AppNavigator.openHomeSettings(this, connection)
        }

        currentThemeValueText.text = GlobalThemeManager.currentThemeLabel(this)
        updateBackgroundSummary()
        EdgeToEdgeHelper.applyInsets(topBar, applyTop = true)
    }

    override fun onResume() {
        super.onResume()
        GlobalThemeManager.apply(this)
        findViewById<TextView>(R.id.settingsThemeValueText).text = GlobalThemeManager.currentThemeLabel(this)
        updateBackgroundSummary()
    }

    private fun updateBackgroundSummary() {
        val selected = !themeStore.loadBackgroundImageUri().isNullOrBlank()
        val count = backgroundLibrary.imageCount()
        backgroundValueText.text = if (!selected) {
            getString(R.string.settings_background_image_builtin_default)
        } else {
            getString(R.string.settings_background_image_selected_with_count, count)
        }
    }

    private fun clearBackgroundImage() {
        themeStore.clearBackgroundImageUri()
        GlobalThemeManager.apply(this)
        updateBackgroundSummary()
        Toast.makeText(this, getString(R.string.settings_background_image_cleared), Toast.LENGTH_SHORT).show()
    }
}
