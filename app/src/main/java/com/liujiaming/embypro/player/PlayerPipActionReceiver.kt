package com.liujiaming.embypro

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class PlayerPipActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            PlayerActivity.ACTION_PIP_PLAY,
            PlayerActivity.ACTION_PIP_PAUSE,
            PlayerActivity.ACTION_PIP_TOGGLE -> PlayerActivity.handlePipControlAction(intent.action.orEmpty())
        }
    }
}
