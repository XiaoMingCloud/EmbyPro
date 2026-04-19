package com.liujiaming.embypro

import android.content.Context

class HomeFeedRepository(context: Context) {
    private val serverRepository = ServerRepository(context.applicationContext)
    private val mediaRepository = MediaRepository(context.applicationContext)

    fun buildHomeData(
        connection: ServerConnection,
        excludedLibraryIds: Set<String>,
        primaryCategoryKey: String
    ): Result<PreloadedHomeData> {
        return runCatching {
            serverRepository.fetchPublicServerInfo(connection.baseUrl).getOrThrow()

            val allLibraries = mediaRepository.fetchMediaLibraries(connection).getOrThrow()
            val categoryLibraries = filterLibrariesByCategory(allLibraries, primaryCategoryKey)
            val homeLibraries = allLibraries
                .filter { library -> categoryLibraries.any { it.id == library.id } }
                .filterNot { excludedLibraryIds.contains(it.id) }
                .shuffled()

            val seenItemIds = linkedSetOf<String>()
            val offsets = linkedMapOf<String, Int>()
            val totals = linkedMapOf<String, Int>()
            homeLibraries.forEach { library ->
                offsets[library.id] = 0
                totals[library.id] = Int.MAX_VALUE
            }

            val homeFeedItems = loadNextHomeFeedBatch(
                connection = connection,
                homeLibraryOrder = homeLibraries,
                homeSeenItemIds = seenItemIds,
                homeLibraryOffsets = offsets,
                homeLibraryTotals = totals
            )

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
        homeLibraryTotals: MutableMap<String, Int>
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

            val page = mediaRepository.fetchLibraryItemsPage(
                connection = connection,
                parentId = library.id,
                startIndex = offset,
                limit = HOME_PAGE_SIZE,
                sortField = LibrarySortField.RANDOM,
                sortDescending = true
            ).getOrThrow()

            homeLibraryOffsets[library.id] = offset + HOME_PAGE_SIZE
            homeLibraryTotals[library.id] = page.totalCount

            page.items
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

    fun buildExcludedSignature(excludedLibraryIds: Set<String>): String {
        return excludedLibraryIds.toList()
            .sorted()
            .joinToString("|")
    }

    private fun filterLibrariesByCategory(
        libraries: List<MediaLibraryUiModel>,
        primaryCategoryKey: String
    ): List<MediaLibraryUiModel> {
        val isAudio = primaryCategoryKey.equals("audio", ignoreCase = true)
        return if (isAudio) {
            libraries.filter { it.collectionType.equals("music", ignoreCase = true) }
        } else {
            libraries.filterNot { it.collectionType.equals("music", ignoreCase = true) }
        }
    }

    companion object {
        private const val HOME_PAGE_SIZE = 12
        private const val HOME_BATCH_SIZE = 18
    }
}
