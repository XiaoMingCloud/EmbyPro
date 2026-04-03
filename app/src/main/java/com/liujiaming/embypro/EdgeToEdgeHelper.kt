package com.liujiaming.embypro

import android.app.Activity
import android.os.Build
import android.view.View
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

object EdgeToEdgeHelper {
    fun enable(activity: Activity, lightSystemBars: Boolean) {
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        activity.window.statusBarColor = android.graphics.Color.TRANSPARENT
        activity.window.navigationBarColor = android.graphics.Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            activity.window.isNavigationBarContrastEnforced = false
            activity.window.isStatusBarContrastEnforced = false
        }

        WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
            isAppearanceLightStatusBars = lightSystemBars
            isAppearanceLightNavigationBars = lightSystemBars
        }
    }

    fun applyInsets(
        target: View,
        applyTop: Boolean = false,
        applyBottom: Boolean = false,
        applyLeft: Boolean = false,
        applyRight: Boolean = false,
        extraTop: Int = 0,
        extraBottom: Int = 0,
        extraLeft: Int = 0,
        extraRight: Int = 0
    ) {
        val startPaddingLeft = target.paddingLeft
        val startPaddingTop = target.paddingTop
        val startPaddingRight = target.paddingRight
        val startPaddingBottom = target.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(target) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(
                left = if (applyLeft) startPaddingLeft + systemBars.left + extraLeft else startPaddingLeft + extraLeft,
                top = if (applyTop) startPaddingTop + systemBars.top + extraTop else startPaddingTop + extraTop,
                right = if (applyRight) startPaddingRight + systemBars.right + extraRight else startPaddingRight + extraRight,
                bottom = if (applyBottom) startPaddingBottom + systemBars.bottom + extraBottom else startPaddingBottom + extraBottom
            )
            insets
        }
        ViewCompat.requestApplyInsets(target)
    }

    fun applyMargins(
        target: View,
        applyTop: Boolean = false,
        applyBottom: Boolean = false
    ) {
        val layoutParams = target.layoutParams as? android.view.ViewGroup.MarginLayoutParams ?: return
        val startTop = layoutParams.topMargin
        val startBottom = layoutParams.bottomMargin

        ViewCompat.setOnApplyWindowInsetsListener(target) { view, insets ->
            val systemBars: Insets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val params = view.layoutParams as? android.view.ViewGroup.MarginLayoutParams
            params?.topMargin = if (applyTop) startTop + systemBars.top else startTop
            params?.bottomMargin = if (applyBottom) startBottom + systemBars.bottom else startBottom
            view.layoutParams = params
            insets
        }
        ViewCompat.requestApplyInsets(target)
    }
}
