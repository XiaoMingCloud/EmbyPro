package com.liujiaming.embypro

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
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
        findViewById<View>(R.id.settingsMusicEntry).setDebouncedClickListener {
            startActivity(
                Intent(this, MusicSettingsActivity::class.java)
                    .putExtra(MusicSettingsActivity.EXTRA_BASE_URL, intent.getStringExtra(EXTRA_BASE_URL).orEmpty())
                    .putExtra(MusicSettingsActivity.EXTRA_USER_ID, intent.getStringExtra(EXTRA_USER_ID).orEmpty())
                    .putExtra(MusicSettingsActivity.EXTRA_ACCESS_TOKEN, intent.getStringExtra(EXTRA_ACCESS_TOKEN).orEmpty())
            )
        }
        findViewById<View>(R.id.settingsHomeDisplayEntry).setDebouncedClickListener {
            startActivity(
                Intent(this, HomeSettingsActivity::class.java)
                    .putExtra(HomeSettingsActivity.EXTRA_BASE_URL, intent.getStringExtra(EXTRA_BASE_URL).orEmpty())
                    .putExtra(HomeSettingsActivity.EXTRA_USER_ID, intent.getStringExtra(EXTRA_USER_ID).orEmpty())
                    .putExtra(HomeSettingsActivity.EXTRA_ACCESS_TOKEN, intent.getStringExtra(EXTRA_ACCESS_TOKEN).orEmpty())
            )
        }

        currentThemeValueText.text = GlobalThemeManager.currentThemeLabel(this)
        EdgeToEdgeHelper.applyInsets(topBar, applyTop = true)
    }

    override fun onResume() {
        super.onResume()
        GlobalThemeManager.apply(this)
        findViewById<TextView>(R.id.settingsThemeValueText).text = GlobalThemeManager.currentThemeLabel(this)
    }

    companion object {
        const val EXTRA_BASE_URL = "extra_base_url"
        const val EXTRA_USER_ID = "extra_user_id"
        const val EXTRA_ACCESS_TOKEN = "extra_access_token"
    }
}
