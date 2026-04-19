package com.liujiaming.embypro

import android.content.Context

class ServerRepository(context: Context) {
    private val embyApiService = EmbyApiService(context.applicationContext)

    fun buildBaseUrl(address: String, port: String): String {
        return embyApiService.buildBaseUrl(address, port)
    }

    fun parseBaseUrl(baseUrl: String): Pair<String, String> {
        return embyApiService.parseBaseUrl(baseUrl)
    }

    fun fetchPublicServerInfo(baseUrl: String): Result<ServerInfo> {
        return embyApiService.fetchPublicServerInfo(baseUrl)
    }

    fun authenticate(
        baseUrl: String,
        username: String,
        password: String
    ): Result<LoginResult> {
        return embyApiService.authenticate(baseUrl, username, password)
    }

    fun buildUserAvatarUrl(baseUrl: String, userId: String): String? {
        return embyApiService.buildUserAvatarUrl(baseUrl, userId)
    }

    fun updateUserAvatar(connection: ServerConnection, imageBytes: ByteArray): Result<String> {
        return embyApiService.updateUserAvatar(
            baseUrl = connection.baseUrl,
            userId = connection.userId,
            accessToken = connection.accessToken,
            imageBytes = imageBytes
        )
    }
}
