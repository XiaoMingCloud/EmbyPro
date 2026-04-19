package com.liujiaming.embypro

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var hasNavigated = false
    private val sessionStore by lazy { ServerSessionStore(this) }
    private val serverRepository by lazy { ServerRepository(this) }
    private val preferenceStore by lazy { AppPreferenceStore(this) }

    private val navigateRunnable = Runnable {
        if (isFinishing || isDestroyed || hasNavigated) return@Runnable
        hasNavigated = true
        startActivity(Intent(this, HomeTabsActivity::class.java))
        finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EdgeToEdgeHelper.enable(this, lightSystemBars = GlobalThemeStore(this).loadTheme().lightSystemBars)
        setContentView(R.layout.activity_splash)
        supportActionBar?.hide()
        GlobalThemeManager.apply(this)

        preloadHomeData()

        val iconView = findViewById<ImageView>(R.id.splashIconView)
        playIconAnimation(iconView)
        mainHandler.postDelayed(navigateRunnable, SPLASH_DURATION_MS)
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacks(navigateRunnable)
    }

    private fun playIconAnimation(iconView: View) {
        iconView.alpha = 0f
        iconView.scaleX = 0.86f
        iconView.scaleY = 0.86f

        val alpha = ObjectAnimator.ofFloat(iconView, View.ALPHA, 0f, 1f)
        val scaleX = ObjectAnimator.ofFloat(iconView, View.SCALE_X, 0.86f, 1f)
        val scaleY = ObjectAnimator.ofFloat(iconView, View.SCALE_Y, 0.86f, 1f)

        AnimatorSet().apply {
            duration = 520L
            interpolator = DecelerateInterpolator()
            playTogether(alpha, scaleX, scaleY)
            start()
        }
    }

    private fun preloadHomeData() {
        val activeServer = sessionStore.loadCurrentServer() ?: return
        if (activeServer.userId.isBlank() || activeServer.accessToken.isBlank()) return

        val baseUrl = serverRepository.buildBaseUrl(activeServer.address, activeServer.port)
        val excludedLibraryIds = preferenceStore.loadExcludedHomeLibraryIds(baseUrl, activeServer.userId)
        HomeDataPreloader.preload(
            context = this,
            baseUrl = baseUrl,
            userId = activeServer.userId,
            accessToken = activeServer.accessToken,
            excludedLibraryIds = excludedLibraryIds
        )
    }

    companion object {
        private const val SPLASH_DURATION_MS = 820L
    }
}
