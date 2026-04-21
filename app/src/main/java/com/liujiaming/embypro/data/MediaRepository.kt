package com.liujiaming.embypro

import android.content.Context

class MediaRepository(context: Context) {
    private val embyApiService = EmbyApiService(context.applicationContext)

    fun fetchMediaLibraries(connection: ServerConnection): Result<List<MediaLibraryUiModel>> {
        return embyApiService.fetchMediaLibraries(
            baseUrl = connection.baseUrl,
            userId = connection.userId,
            accessToken = connection.accessToken
        )
    }

    fun fetchLibraryItemsPage(
        connection: ServerConnection,
        parentId: String,
        startIndex: Int,
        limit: Int,
        mode: LibraryBrowseMode = LibraryBrowseMode.ALL,
        filterValue: String? = null,
        sortField: LibrarySortField = LibrarySortField.TITLE,
        sortDescending: Boolean = true,
        contentCategory: LibraryContentCategory = LibraryContentCategory.VIDEO
    ): Result<LibraryItemsPageUiModel> {
        return runCatching {
            embyApiService.fetchLibraryItemsPage(
                baseUrl = connection.baseUrl,
                userId = connection.userId,
                accessToken = connection.accessToken,
                parentId = parentId,
                startIndex = startIndex,
                limit = limit,
                mode = mode,
                filterValue = filterValue,
                sortField = sortField,
                sortDescending = sortDescending,
                contentCategory = contentCategory
            )
        }
    }

    fun fetchLibraryFilterOptions(
        connection: ServerConnection,
        parentId: String
    ): Result<LibraryFilterOptionsUiModel> {
        return embyApiService.fetchLibraryFilterOptions(
            baseUrl = connection.baseUrl,
            userId = connection.userId,
            accessToken = connection.accessToken,
            parentId = parentId
        )
    }

    fun searchMediaItemsPage(
        connection: ServerConnection,
        query: String,
        startIndex: Int,
        limit: Int
    ): Result<LibraryItemsPageUiModel> {
        return embyApiService.searchMediaItemsPage(
            baseUrl = connection.baseUrl,
            userId = connection.userId,
            accessToken = connection.accessToken,
            query = query,
            startIndex = startIndex,
            limit = limit
        )
    }

    fun fetchPlaybackHistoryPage(
        connection: ServerConnection,
        startIndex: Int,
        limit: Int,
        category: PlaybackHistoryCategory = PlaybackHistoryCategory.ALL
    ): Result<PlaybackHistoryPageUiModel> {
        return embyApiService.fetchPlaybackHistoryPage(
            baseUrl = connection.baseUrl,
            userId = connection.userId,
            accessToken = connection.accessToken,
            startIndex = startIndex,
            limit = limit,
            category = category
        )
    }

    fun fetchFavoriteItemsPage(
        connection: ServerConnection,
        startIndex: Int,
        limit: Int
    ): Result<FavoriteItemsPageUiModel> {
        return embyApiService.fetchFavoriteItemsPage(
            baseUrl = connection.baseUrl,
            userId = connection.userId,
            accessToken = connection.accessToken,
            startIndex = startIndex,
            limit = limit
        )
    }

    fun fetchVideoDetail(connection: ServerConnection, itemId: String): Result<VideoDetailUiModel> {
        return embyApiService.fetchVideoDetail(
            baseUrl = connection.baseUrl,
            userId = connection.userId,
            accessToken = connection.accessToken,
            itemId = itemId
        )
    }

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

    fun clearPlayedState(connection: ServerConnection, itemId: String): Result<Unit> {
        return embyApiService.clearPlayedState(
            baseUrl = connection.baseUrl,
            userId = connection.userId,
            accessToken = connection.accessToken,
            itemId = itemId
        )
    }

    fun deleteItem(connection: ServerConnection, itemId: String): Result<Unit> {
        return embyApiService.deleteItem(
            baseUrl = connection.baseUrl,
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
