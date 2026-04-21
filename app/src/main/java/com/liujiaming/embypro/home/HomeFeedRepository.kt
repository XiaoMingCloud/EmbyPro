package com.liujiaming.embypro

import android.content.Context

class HomeFeedRepository(context: Context) {
    private val serverRepository = ServerRepository(context.applicationContext)
    private val mediaRepository = MediaRepository(context.applicationContext)
    private val musicRepository = MusicRepository(context.applicationContext)
    private val preferenceStore = AppPreferenceStore(context.applicationContext)

    fun buildHomeData(
        connection: ServerConnection,
        excludedLibraryIds: Set<String>,
        primaryCategoryKey: String
    ): Result<PreloadedHomeData> {
        return runCatching {
            serverRepository.fetchPublicServerInfo(connection.baseUrl).getOrThrow()

            val categoryLibraries = loadLibrariesForCategory(connection, primaryCategoryKey)
            val contentCategory = contentCategoryFor(primaryCategoryKey)
            val homeLibraries = if (contentCategory == LibraryContentCategory.AUDIO) {
                categoryLibraries
            } else {
                categoryLibraries.filterNot { excludedLibraryIds.contains(it.id) }
            }.shuffled()

            val seenItemIds = linkedSetOf<String>()
            val offsets = linkedMapOf<String, Int>()
            val totals = linkedMapOf<String, Int>()
            homeLibraries.forEach { library ->
                offsets[library.id] = 0
                totals[library.id] = Int.MAX_VALUE
            }

            val homeFeedItems = if (contentCategory == LibraryContentCategory.AUDIO) {
                loadCurrentMusicHomeItems(
                    connection = connection,
                    homeLibraryOrder = homeLibraries,
                    seenItemIds = seenItemIds,
                    offsets = offsets,
                    totals = totals
                )
            } else {
                loadNextHomeFeedBatch(
                    connection = connection,
                    homeLibraryOrder = homeLibraries,
                    homeSeenItemIds = seenItemIds,
                    homeLibraryOffsets = offsets,
                    homeLibraryTotals = totals,
                    contentCategory = contentCategory
                )
            }

            PreloadedHomeData(
                baseUrl = connection.baseUrl,
                userId = connection.userId,
                accessToken = connection.accessToken,
                primaryCategoryKey = primaryCategoryKey,
                excludedLibrarySignature = buildExcludedSignature(excludedLibraryIds),
                homeFeedItems = homeFeedItems,
                mediaLibraries = categoryLibraries,
                homeLibraryOrder = homeLibraries,
                homeLibraryOffsets = offsets.toMap(),
                homeLibraryTotals = totals.toMap(),
                homeSeenItemIds = seenItemIds.toSet()
            )
        }
    }

    fun loadNextHomeFeedBatch(
        connection: ServerConnection,
        homeLibraryOrder: List<MediaLibraryUiModel>,
        homeSeenItemIds: MutableSet<String>,
        homeLibraryOffsets: MutableMap<String, Int>,
        homeLibraryTotals: MutableMap<String, Int>,
        contentCategory: LibraryContentCategory = LibraryContentCategory.VIDEO
    ): List<MediaPosterUiModel> {
        if (homeLibraryOrder.isEmpty()) return emptyList()

        val freshItems = mutableListOf<MediaPosterUiModel>()
        val libraries = homeLibraryOrder.shuffled()
        val maxAttempts = (libraries.size * 3).coerceAtLeast(3)
        var attempts = 0

        while (freshItems.size < HOME_BATCH_SIZE && attempts < maxAttempts) {
            val library = libraries[attempts % libraries.size]
            val offset = homeLibraryOffsets[library.id] ?: 0
            val total = homeLibraryTotals[library.id] ?: Int.MAX_VALUE
            if (offset >= total) {
                attempts++
                continue
            }

            val pageItems: List<MediaPosterUiModel>
            val totalCount: Int
            if (contentCategory == LibraryContentCategory.AUDIO) {
                val musicPage = musicRepository.fetchMusicBrowsePage(
                    connection = connection,
                    libraryId = library.id,
                    browseType = MusicBrowseType.SONGS
                ).getOrThrow()
                totalCount = musicPage.totalCount
                pageItems = musicPage.items
                    .asSequence()
                    .drop(offset)
                    .take(HOME_PAGE_SIZE)
                    .mapIndexed { index, item -> item.toHomeAudioPoster(index) }
                    .toList()
            } else {
                val page = mediaRepository.fetchLibraryItemsPage(
                    connection = connection,
                    parentId = library.id,
                    startIndex = offset,
                    limit = HOME_PAGE_SIZE,
                    sortField = LibrarySortField.RANDOM,
                    sortDescending = true,
                    contentCategory = contentCategory
                ).getOrThrow()
                totalCount = page.totalCount
                pageItems = page.items
            }

            homeLibraryOffsets[library.id] = offset + HOME_PAGE_SIZE
            homeLibraryTotals[library.id] = totalCount

            pageItems
                .asSequence()
                .filter { !it.isFolder && it.itemType != "BoxSet" && it.itemType != "Folder" }
                .filter { it.id.isNotBlank() }
                .filter { homeSeenItemIds.add(it.id) }
                .take(HOME_BATCH_SIZE - freshItems.size)
                .forEach { freshItems.add(it) }

            attempts++
            if (homeLibraryTotals.values.all { it != Int.MAX_VALUE } &&
                homeLibraryOffsets.all { (libraryId, currentOffset) ->
                    currentOffset >= (homeLibraryTotals[libraryId] ?: Int.MAX_VALUE)
                }
            ) {
                break
            }
        }

        return freshItems.shuffled()
    }

    private fun loadCurrentMusicHomeItems(
        connection: ServerConnection,
        homeLibraryOrder: List<MediaLibraryUiModel>,
        seenItemIds: MutableSet<String>,
        offsets: MutableMap<String, Int>,
        totals: MutableMap<String, Int>
    ): List<MediaPosterUiModel> {
        val currentLibrary = homeLibraryOrder.firstOrNull() ?: return emptyList()
        val musicPage = musicRepository.fetchMusicBrowsePage(
            connection = connection,
            libraryId = currentLibrary.id,
            browseType = MusicBrowseType.SONGS
        ).getOrThrow()

        val musicItems = musicPage.items
            .asSequence()
            .filter { it.kind == MusicEntryKind.SONG && it.id.isNotBlank() }
            .filter { seenItemIds.add(it.id) }
            .take(HOME_BATCH_SIZE)
            .mapIndexed { index, item -> item.toHomeAudioPoster(index) }
            .toList()

        if (musicItems.isNotEmpty()) {
            totals[currentLibrary.id] = musicPage.totalCount
            offsets[currentLibrary.id] = HOME_BATCH_SIZE
            return musicItems.shuffled()
        }

        val fallbackPage = mediaRepository.fetchLibraryItemsPage(
            connection = connection,
            parentId = currentLibrary.id,
            startIndex = 0,
            limit = HOME_BATCH_SIZE,
            sortField = LibrarySortField.TITLE,
            sortDescending = false,
            contentCategory = LibraryContentCategory.AUDIO
        ).getOrThrow()

        totals[currentLibrary.id] = fallbackPage.totalCount
        offsets[currentLibrary.id] = HOME_BATCH_SIZE
        return fallbackPage.items
            .asSequence()
            .filter { it.itemType.equals("Audio", ignoreCase = true) && it.id.isNotBlank() }
            .filter { seenItemIds.add(it.id) }
            .take(HOME_BATCH_SIZE)
            .toList()
            .shuffled()
    }

    fun buildExcludedSignature(excludedLibraryIds: Set<String>): String {
        return excludedLibraryIds.toList()
            .sorted()
            .joinToString("|")
    }

    private fun loadLibrariesForCategory(
        connection: ServerConnection,
        primaryCategoryKey: String
    ): List<MediaLibraryUiModel> {
        return if (primaryCategoryKey.equals("audio", ignoreCase = true)) {
            loadCurrentMusicLibrary(connection)
        } else {
            mediaRepository.fetchMediaLibraries(connection)
                .getOrThrow()
                .filterNot { it.collectionType.equals("music", ignoreCase = true) }
        }
    }

    private fun MusicListEntryUiModel.toHomeAudioPoster(index: Int): MediaPosterUiModel {
        return MediaPosterUiModel(
            id = id,
            title = title,
            subtitle = subtitle,
            style = ServerIconStyle.entries[index % ServerIconStyle.entries.size],
            imageUrl = imageUrl,
            isFolder = false,
            itemType = itemType.ifBlank { "Audio" }
        )
    }

    private fun loadCurrentMusicLibrary(connection: ServerConnection): List<MediaLibraryUiModel> {
        val libraries = musicRepository.fetchMusicLibraries(connection).getOrThrow()
        if (libraries.isEmpty()) return emptyList()

        val repositoryState = MusicLibraryRepository.currentState()
        val selectedLibraryId = repositoryState.currentLibraryId
            ?.takeIf {
                repositoryState.baseUrl == connection.baseUrl &&
                    repositoryState.userId == connection.userId
            }
            ?: preferenceStore.loadSelectedMusicLibraryId(connection.baseUrl, connection.userId)

        val currentLibrary = libraries.firstOrNull { it.id == selectedLibraryId }
            ?: libraries.first()
        if (selectedLibraryId.isNullOrBlank()) {
            preferenceStore.saveSelectedMusicLibraryId(connection.baseUrl, connection.userId, currentLibrary.id)
        }
        return listOf(currentLibrary)
    }

    fun contentCategoryFor(primaryCategoryKey: String): LibraryContentCategory {
        return if (primaryCategoryKey.equals("audio", ignoreCase = true)) {
            LibraryContentCategory.AUDIO
        } else {
            LibraryContentCategory.VIDEO
        }
    }

    companion object {
        private const val HOME_PAGE_SIZE = 12
        private const val HOME_BATCH_SIZE = 18
    }
}
