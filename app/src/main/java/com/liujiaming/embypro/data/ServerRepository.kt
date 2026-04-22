package com.liujiaming.embypro

import android.content.Context

/**
 * Repository for server-related operations (connection, authentication, user profile).
 * Acts as a facade over EmbyApiService for server management tasks.
 */
class ServerRepository(context: Context) {
    private val embyApiService = EmbyApiService(context.applicationContext)

    /**
     * Builds a normalized base URL from address and port.
     */
    fun buildBaseUrl(address: String, port: String): String {
        return embyApiService.buildBaseUrl(address, port)
    }

    /**
     * Parses a base URL into address and port components.
     */
    fun parseBaseUrl(baseUrl: String): Pair<String, String> {
        return embyApiService.parseBaseUrl(baseUrl)
    }

    /**
     * Fetches public server information (name, version, ID).
     */
    fun fetchPublicServerInfo(baseUrl: String): Result<ServerInfo> {
        return embyApiService.fetchPublicServerInfo(baseUrl)
    }

    /**
     * Authenticates a user with username and password.
     */
    fun authenticate(
        baseUrl: String,
        username: String,
        password: String
    ): Result<LoginResult> {
        return embyApiService.authenticate(baseUrl, username, password)
    }

    /**
     * Builds the URL for fetching user avatar image.
     */
    fun buildUserAvatarUrl(baseUrl: String, userId: String): String? {
        return embyApiService.buildUserAvatarUrl(baseUrl, userId)
    }

    /**
     * Updates the user's avatar image on the server.
     */
    fun updateUserAvatar(connection: ServerConnection, imageBytes: ByteArray): Result<String> {
        return embyApiService.updateUserAvatar(
            baseUrl = connection.baseUrl,
            userId = connection.userId,
            accessToken = connection.accessToken,
            imageBytes = imageBytes
        )
    }
}
