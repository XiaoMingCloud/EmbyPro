package com.liujiaming.embypro

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import kotlin.math.min

internal fun Context.createMusicGlassDialog(contentView: android.view.View): Dialog {
    return Dialog(this).apply {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(contentView)
        setCanceledOnTouchOutside(true)
        setCancelable(true)
    }
}

internal fun Dialog.applyMusicGlassWindow(context: Context) {
    show()
    window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    window?.decorView?.setBackgroundColor(Color.TRANSPARENT)
    window?.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        window?.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
    }
    window?.attributes = window?.attributes?.apply {
        dimAmount = 0.02f
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            blurBehindRadius = 96
        }
    }
    window?.setLayout(
        min(context.resources.displayMetrics.widthPixels - dialogDp(context, 40), dialogDp(context, 520)),
        ViewGroup.LayoutParams.WRAP_CONTENT
    )
}

private fun dialogDp(context: Context, value: Int): Int {
    return (context.resources.displayMetrics.density * value).toInt()
}
