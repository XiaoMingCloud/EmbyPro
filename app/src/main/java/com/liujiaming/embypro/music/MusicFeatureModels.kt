package com.liujiaming.embypro

enum class MusicBrowseType {
    SONGS,
    ALBUMS,
    ARTISTS,
    PLAYLISTS,
    FAVORITES,
    FOLDERS
}

enum class MusicEntryKind {
    SONG,
    CONTAINER
}

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

data class MusicListPageUiModel(
    val title: String,
    val subtitle: String,
    val items: List<MusicListEntryUiModel>,
    val totalCount: Int,
    val isSongList: Boolean,
    val libraryId: String
)

data class MusicPlaybackUiModel(
    val itemId: String,
    val title: String,
    val subtitle: String,
    val coverImageUrl: String?,
    val playbackUrl: String,
    val playbackPositionMs: Long,
    val isFavorite: Boolean
)
