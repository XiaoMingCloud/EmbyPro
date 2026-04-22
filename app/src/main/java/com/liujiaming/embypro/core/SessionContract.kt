package com.liujiaming.embypro

import android.app.Activity
import android.content.Intent
import android.widget.Toast

/**
 * Data class representing server connection credentials.
 * Contains base URL, user ID, and access token for API authentication.
 */
data class ServerConnection(
    val baseUrl: String,
    val userId: String,
    val accessToken: String
) {
    /**
     * Validates that all connection fields are non-empty.
     */
    val isValid: Boolean
        get() = baseUrl.isNotBlank() && userId.isNotBlank() && accessToken.isNotBlank()
}

/**
 * Contract class defining intent extras for passing server connection data between activities.
 */
object SessionContract {
    const val EXTRA_BASE_URL = "extra_base_url"
    const val EXTRA_USER_ID = "extra_user_id"
    const val EXTRA_ACCESS_TOKEN = "extra_access_token"
}

/**
 * Extension function to write a ServerConnection to an Intent.
 */
fun Intent.putServerConnection(connection: ServerConnection): Intent {
    return putExtra(SessionContract.EXTRA_BASE_URL, connection.baseUrl)
        .putExtra(SessionContract.EXTRA_USER_ID, connection.userId)
        .putExtra(SessionContract.EXTRA_ACCESS_TOKEN, connection.accessToken)
}

/**
 * Extension function to read a ServerConnection from an Intent.
 */
fun Intent.readServerConnection(): ServerConnection {
    return ServerConnection(
        baseUrl = getStringExtra(SessionContract.EXTRA_BASE_URL).orEmpty(),
        userId = getStringExtra(SessionContract.EXTRA_USER_ID).orEmpty(),
        accessToken = getStringExtra(SessionContract.EXTRA_ACCESS_TOKEN).orEmpty()
    )
}

/**
 * Extension function for activities to validate server connection before proceeding.
 * Shows a toast and finishes the activity if connection data is missing or invalid.
 *
 * @param sessionStore The server session store to resolve connection from
 * @param serverRepository The server repository for building base URLs
 * @param intent The intent containing connection data (defaults to activity's intent)
 * @return Valid ServerConnection or null if invalid
 */
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
