package com.liujiaming.embypro

import android.content.Context

/**
 * Repository for media-related operations (movies, TV shows, etc.).
 * Acts as a facade over EmbyApiService, providing connection-aware API calls.
 */
class MediaRepository(context: Context) {
    private val embyApiService = EmbyApiService(context.applicationContext)

    /**
     * Fetches all media libraries for the current server connection.
     */
    fun fetchMediaLibraries(connection: ServerConnection): Result<List<MediaLibraryUiModel>> {
        return embyApiService.fetchMediaLibraries(
            baseUrl = connection.baseUrl,
            userId = connection.userId,
            accessToken = connection.accessToken
        )
    }

    /**
     * Fetches a paginated list of items from a media library.
     * Supports various browse modes, filters, and sorting options.
     */
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

    /**
     * Fetches available filter options (genres, tags) for a media library.
     */
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

    /**
     * Searches for media items matching the query.
     */
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

    /**
     * Fetches playback history with optional category filtering.
     */
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

    /**
     * Fetches favorite items for the current user.
     */
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

    /**
     * Fetches detailed information for a specific video item.
     */
    fun fetchVideoDetail(connection: ServerConnection, itemId: String): Result<VideoDetailUiModel> {
        return embyApiService.fetchVideoDetail(
            baseUrl = connection.baseUrl,
            userId = connection.userId,
            accessToken = connection.accessToken,
            itemId = itemId
        )
    }

    /**
     * Updates the favorite state of a media item.
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
     * Clears the played state for a media item.
     */
    fun clearPlayedState(connection: ServerConnection, itemId: String): Result<Unit> {
        return embyApiService.clearPlayedState(
            baseUrl = connection.baseUrl,
            userId = connection.userId,
            accessToken = connection.accessToken,
            itemId = itemId
        )
    }

    /**
     * Deletes a media item from the server.
     */
    fun deleteItem(connection: ServerConnection, itemId: String): Result<Unit> {
        return embyApiService.deleteItem(
            baseUrl = connection.baseUrl,
            accessToken = connection.accessToken,
            itemId = itemId
        )
    }

    /**
     * Updates playback progress for a media item.
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
