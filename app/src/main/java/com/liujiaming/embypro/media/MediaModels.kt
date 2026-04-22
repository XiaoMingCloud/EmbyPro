package com.liujiaming.embypro

/**
 * UI model representing a media item poster (movie, episode, song, etc.).
 * Contains display information like title, subtitle, image, and item type.
 */
data class MediaPosterUiModel(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val style: ServerIconStyle = ServerIconStyle.INDIGO,
    val imageUrl: String? = null,
    val isFolder: Boolean = false,
    val itemType: String = ""
)

/**
 * UI model representing a media library (collection of media items).
 * Contains library metadata like title, image, item count, and collection type.
 */
data class MediaLibraryUiModel(
    val id: String,
    val title: String,
    val style: ServerIconStyle,
    val imageUrl: String? = null,
    val totalCount: Int = 0,
    val collectionType: String = ""
)

/**
 * Enum representing the category of library content.
 * Used to differentiate between video and audio content types.
 */
enum class LibraryContentCategory {
    VIDEO,
    AUDIO
}
