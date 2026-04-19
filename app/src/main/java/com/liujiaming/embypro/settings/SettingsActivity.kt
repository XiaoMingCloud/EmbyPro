package com.liujiaming.embypro

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {
    private lateinit var topBar: View
    private val sessionStore by lazy { ServerSessionStore(this) }
    private val serverRepository by lazy { ServerRepository(this) }
    private val themeStore by lazy { GlobalThemeStore(this) }
    private lateinit var connection: ServerConnection
    private lateinit var backgroundValueText: TextView

    private val backgroundPickerLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        runCatching {
            contentResolver.takePersistableUriPermission(uri, takeFlags)
        }
        themeStore.saveBackgroundImageUri(uri.toString())
        GlobalThemeManager.apply(this)
        updateBackgroundSummary()
    }

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
            backgroundPickerLauncher.launch(arrayOf("image/*"))
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
        backgroundValueText.text = if (themeStore.loadBackgroundImageUri().isNullOrBlank()) {
            getString(R.string.settings_background_image_none)
        } else {
            getString(R.string.settings_background_image_selected)
        }
    }

    private fun clearBackgroundImage() {
        themeStore.clearBackgroundImageUri()
        GlobalThemeManager.apply(this)
        updateBackgroundSummary()
        Toast.makeText(this, getString(R.string.settings_background_image_cleared), Toast.LENGTH_SHORT).show()
    }
}
