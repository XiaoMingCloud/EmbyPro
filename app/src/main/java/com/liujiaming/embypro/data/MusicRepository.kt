package com.liujiaming.embypro

import android.content.Context

/**
 * Repository for music-related operations (songs, albums, artists, playlists).
 * Acts as a facade over EmbyApiService, providing connection-aware music API calls.
 */
class MusicRepository(context: Context) {
    private val embyApiService = EmbyApiService(context.applicationContext)

    /**
     * Fetches music libraries for the current server connection.
     */
    fun fetchMusicLibraries(connection: ServerConnection): Result<List<MediaLibraryUiModel>> {
        return embyApiService.fetchMusicLibraries(
            baseUrl = connection.baseUrl,
            userId = connection.userId,
            accessToken = connection.accessToken
        )
    }

    /**
     * Fetches statistics for a music library (songs, albums, artists, playlists count).
     */
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

    /**
     * Fetches a browsable page of music content based on type (songs, albums, artists, etc.).
     */
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

    /**
     * Fetches playback information for an audio item.
     */
    fun fetchAudioPlayback(connection: ServerConnection, itemId: String): Result<MusicPlaybackUiModel> {
        return embyApiService.fetchAudioPlayback(
            baseUrl = connection.baseUrl,
            userId = connection.userId,
            accessToken = connection.accessToken,
            itemId = itemId
        )
    }

    /**
     * Searches for music items matching the query within a library.
     */
    fun searchMusicItems(
        connection: ServerConnection,
        libraryId: String,
        query: String
    ): Result<MusicListPageUiModel> {
        return embyApiService.searchMusicItems(
            baseUrl = connection.baseUrl,
            userId = connection.userId,
            accessToken = connection.accessToken,
            libraryId = libraryId,
            query = query
        )
    }

    /**
     * Updates the favorite state of a music item.
     */
    fun setFavoriteState(
        connection: ServerConnection,
        itemId: String,
        favorite: Boolean
    ): Result<Unit> {
        return embyApiService.setFavoriteState(
            baseUrl = connection.baseUrl,
            userId = connection.userId,
            accessToken = connection.accessToken,
            itemId = itemId,
            favorite = favorite
        )
    }

    /**
     * Updates playback progress for a music item.
     */
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
