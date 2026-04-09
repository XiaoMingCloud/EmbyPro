package com.liujiaming.embypro

import android.os.SystemClock
import android.view.View

fun View.setDebouncedClickListener(
    intervalMs: Long = 600L,
    onClick: (View) -> Unit
) {
    setOnClickListener { view ->
        val now = SystemClock.elapsedRealtime()
        val lastClick = (view.getTag(R.id.tag_debounced_click_timestamp) as? Long) ?: 0L
        if (now - lastClick < intervalMs) return@setOnClickListener
        view.setTag(R.id.tag_debounced_click_timestamp, now)
        onClick(view)
    }
}
