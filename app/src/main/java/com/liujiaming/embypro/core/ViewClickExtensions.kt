package com.liujiaming.embypro

import android.os.SystemClock
import android.view.View

/**
 * Extension function to set a debounced click listener on a View.
 * Prevents rapid successive clicks within the specified interval.
 *
 * @param intervalMs Minimum time interval between clicks in milliseconds (default 600ms)
 * @param onClick Callback invoked when a valid click occurs
 */
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
