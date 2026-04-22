package com.liujiaming.embypro

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * BroadcastReceiver for handling Picture-in-Picture (PiP) mode control actions.
 * Receives playback control intents (play, pause, toggle) from PiP window and delegates to PlayerActivity.
 */
class PlayerPipActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            PlayerActivity.ACTION_PIP_PLAY,
            PlayerActivity.ACTION_PIP_PAUSE,
            PlayerActivity.ACTION_PIP_TOGGLE -> PlayerActivity.handlePipControlAction(intent.action.orEmpty())
        }
    }
}
