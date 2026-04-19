package com.liujiaming.embypro

import android.content.Context

class MusicRepository(context: Context) {
    private val embyApiService = EmbyApiService(context.applicationContext)

    fun fetchMusicLibraries(connection: ServerConnection): Result<List<MediaLibraryUiModel>> {
        return embyApiService.fetchMusicLibraries(
            baseUrl = connection.baseUrl,
            userId = connection.userId,
            accessToken = connection.accessToken
        )
    }

    fun fetchMusicLibraryStats(
        connection: ServerConnection,
        libraryId: String
    ): Result<MusicLibraryStatsUiModel> {
        return embyApiService.fetchMusicLibraryStats(
            baseUrl = connection.baseUrl,
            userId = connection.userId,
            accessToken = connection.accessToken,
            libraryId = libraryId
        )
    }

    fun fetchMusicBrowsePage(
        connection: ServerConnection,
        libraryId: String,
        browseType: MusicBrowseType,
        containerId: String? = null,
        containerTitle: String? = null
    ): Result<MusicListPageUiModel> {
        return embyApiService.fetchMusicBrowsePage(
            baseUrl = connection.baseUrl,
            userId = connection.userId,
            accessToken = connection.accessToken,
            libraryId = libraryId,
            browseType = browseType,
            containerId = containerId,
            containerTitle = containerTitle
        )
    }

    fun fetchAudioPlayback(connection: ServerConnection, itemId: String): Result<MusicPlaybackUiModel> {
        return embyApiService.fetchAudioPlayback(
            baseUrl = connection.baseUrl,
            userId = connection.userId,
            accessToken = connection.accessToken,
            itemId = itemId
        )
    }

    fun updatePlaybackProgress(
        connection: ServerConnection,
        itemId: String,
        playbackPositionMs: Long
    ): Result<Unit> {
        return embyApiService.updatePlaybackProgress(
            baseUrl = connection.baseUrl,
            userId = connection.userId,
            accessToken = connection.accessToken,
            itemId = itemId,
            playbackPositionMs = playbackPositionMs
        )
    }
}
