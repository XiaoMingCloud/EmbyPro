package com.liujiaming.embypro

/**
 * Enum representing different music browsing categories.
 * Used to navigate between songs, albums, artists, playlists, favorites, and folders.
 */
enum class MusicBrowseType {
    SONGS,
    ALBUMS,
    ARTISTS,
    PLAYLISTS,
    FAVORITES,
    FOLDERS,
    LOCAL
}

/**
 * Enum representing the type of music list entry.
 * SONG represents individual tracks, CONTAINER represents collections (albums, artists, etc.).
 */
enum class MusicEntryKind {
    SONG,
    CONTAINER
}

/**
 * UI model representing a single item in a music list.
 * Can be either a song or a container (album, artist, playlist, etc.).
 */
data class MusicListEntryUiModel(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val detail: String = "",
    val imageUrl: String? = null,
    val kind: MusicEntryKind,
    val browseType: MusicBrowseType,
    val itemType: String = "",
    val runtimeTicks: Long = 0L,
    val albumTitle: String = "",
    val artistLine: String = ""
)

/**
 * UI model representing a complete music list page.
 * Contains page metadata and list of music entries.
 */
data class MusicListPageUiModel(
    val title: String,
    val subtitle: String,
    val items: List<MusicListEntryUiModel>,
    val totalCount: Int,
    val isSongList: Boolean,
    val libraryId: String
)

/**
 * UI model representing music playback information.
 * Contains item details, playback URL, and current position.
 */
data class MusicPlaybackUiModel(
    val itemId: String,
    val title: String,
    val subtitle: String,
    val coverImageUrl: String?,
    val playbackUrl: String,
    val playbackPositionMs: Long,
    val isFavorite: Boolean,
    val runtimeMs: Long = 0L,
    val isOfflineCached: Boolean = false
)
