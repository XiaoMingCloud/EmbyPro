package com.liujiaming.embypro

import android.app.Activity
import android.content.Intent
import android.widget.Toast

data class ServerConnection(
    val baseUrl: String,
    val userId: String,
    val accessToken: String
) {
    val isValid: Boolean
        get() = baseUrl.isNotBlank() && userId.isNotBlank() && accessToken.isNotBlank()
}

object SessionContract {
    const val EXTRA_BASE_URL = "extra_base_url"
    const val EXTRA_USER_ID = "extra_user_id"
    const val EXTRA_ACCESS_TOKEN = "extra_access_token"
}

fun Intent.putServerConnection(connection: ServerConnection): Intent {
    return putExtra(SessionContract.EXTRA_BASE_URL, connection.baseUrl)
        .putExtra(SessionContract.EXTRA_USER_ID, connection.userId)
        .putExtra(SessionContract.EXTRA_ACCESS_TOKEN, connection.accessToken)
}

fun Intent.readServerConnection(): ServerConnection {
    return ServerConnection(
        baseUrl = getStringExtra(SessionContract.EXTRA_BASE_URL).orEmpty(),
        userId = getStringExtra(SessionContract.EXTRA_USER_ID).orEmpty(),
        accessToken = getStringExtra(SessionContract.EXTRA_ACCESS_TOKEN).orEmpty()
    )
}

fun Activity.requireServerConnection(
    sessionStore: ServerSessionStore,
    serverRepository: ServerRepository,
    intent: Intent = this.intent
): ServerConnection? {
    val connection = sessionStore.resolveConnection(intent, serverRepository)
    if (connection != null && connection.isValid) {
        return connection
    }
    Toast.makeText(this, getString(R.string.server_data_missing), Toast.LENGTH_SHORT).show()
    finish()
    return null
}
