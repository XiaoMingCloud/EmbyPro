package com.liujiaming.embypro

data class MediaPosterUiModel(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val style: ServerIconStyle = ServerIconStyle.INDIGO,
    val imageUrl: String? = null,
    val isFolder: Boolean = false,
    val itemType: String = ""
)

data class MediaLibraryUiModel(
    val id: String,
    val title: String,
    val style: ServerIconStyle,
    val imageUrl: String? = null,
    val totalCount: Int = 0,
    val collectionType: String = ""
)
