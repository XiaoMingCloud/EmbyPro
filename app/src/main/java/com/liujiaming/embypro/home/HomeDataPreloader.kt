package com.liujiaming.embypro

import android.content.Context
import java.util.concurrent.Executors
import java.util.concurrent.Future

data class PreloadedHomeData(
    val baseUrl: String,
    val userId: String,
    val accessToken: String,
    val excludedLibrarySignature: String,
    val homeFeedItems: List<MediaPosterUiModel>,
    val mediaLibraries: List<MediaLibraryUiModel>,
    val homeLibraryOrder: List<MediaLibraryUiModel>,
    val homeLibraryOffsets: Map<String, Int>,
    val homeLibraryTotals: Map<String, Int>,
    val homeSeenItemIds: Set<String>
)

object HomeDataPreloader {
    private val executor = Executors.newSingleThreadExecutor()
    private val lock = Any()

    private var activeRequest: HomePreloadRequest? = null
    private var activeTask: Future<Result<PreloadedHomeData>>? = null

    fun preload(
        context: Context,
        baseUrl: String,
        userId: String,
        accessToken: String,
        excludedLibraryIds: Set<String>
    ) {
        if (baseUrl.isBlank() || userId.isBlank() || accessToken.isBlank()) return

        val request = HomePreloadRequest(
            baseUrl = baseUrl,
            userId = userId,
            accessToken = accessToken,
            excludedLibrarySignature = buildExcludedSignature(excludedLibraryIds)
        )

        synchronized(lock) {
            if (activeRequest == request && activeTask?.isCancelled == false) {
                return
            }

            activeRequest = request
            activeTask = executor.submit<Result<PreloadedHomeData>> {
                runCatching {
                    buildPreloadedHomeData(
                        context = context.applicationContext,
                        request = request,
                        excludedLibraryIds = excludedLibraryIds
                    )
                }
            }
        }
    }

    fun takeTask(
        baseUrl: String,
        userId: String,
        accessToken: String,
        excludedLibraryIds: Set<String>
    ): Future<Result<PreloadedHomeData>>? {
        val expectedRequest = HomePreloadRequest(
            baseUrl = baseUrl,
            userId = userId,
            accessToken = accessToken,
            excludedLibrarySignature = buildExcludedSignature(excludedLibraryIds)
        )

        synchronized(lock) {
            if (activeRequest != expectedRequest) return null
            val task = activeTask
            activeRequest = null
            activeTask = null
            return task
        }
    }

    private fun buildPreloadedHomeData(
        context: Context,
        request: HomePreloadRequest,
        excludedLibraryIds: Set<String>
    ): PreloadedHomeData {
        val embyApiService = EmbyApiService(context)
        embyApiService.fetchPublicServerInfo(request.baseUrl).getOrThrow()

        val allLibraries = embyApiService.fetchMediaLibraries(
            request.baseUrl,
            request.userId,
            request.accessToken
        ).getOrThrow()
        val homeLibraries = allLibraries
            .filterNot { excludedLibraryIds.contains(it.id) }
            .shuffled()

        val seenItemIds = linkedSetOf<String>()
        val offsets = linkedMapOf<String, Int>()
        val totals = linkedMapOf<String, Int>()
        homeLibraries.forEach { library ->
            offsets[library.id] = 0
            totals[library.id] = Int.MAX_VALUE
        }

        val homeFeedItems = fetchNextHomeFeedBatch(
            embyApiService = embyApiService,
            baseUrl = request.baseUrl,
            userId = request.userId,
            accessToken = request.accessToken,
            homeLibraryOrder = homeLibraries,
            homeSeenItemIds = seenItemIds,
            homeLibraryOffsets = offsets,
            homeLibraryTotals = totals
        )

        return PreloadedHomeData(
            baseUrl = request.baseUrl,
            userId = request.userId,
            accessToken = request.accessToken,
            excludedLibrarySignature = request.excludedLibrarySignature,
            homeFeedItems = homeFeedItems,
            mediaLibraries = allLibraries,
            homeLibraryOrder = homeLibraries,
            homeLibraryOffsets = offsets.toMap(),
            homeLibraryTotals = totals.toMap(),
            homeSeenItemIds = seenItemIds.toSet()
        )
    }

    private fun fetchNextHomeFeedBatch(
        embyApiService: EmbyApiService,
        baseUrl: String,
        userId: String,
        accessToken: String,
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

            val page = embyApiService.fetchLibraryItemsPage(
                baseUrl = baseUrl,
                userId = userId,
                accessToken = accessToken,
                parentId = library.id,
                startIndex = offset,
                limit = HOME_PAGE_SIZE,
                sortField = LibrarySortField.RANDOM,
                sortDescending = true
            )
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
            if (homeLibraryTotals.values.all { knownTotal ->
                    knownTotal != Int.MAX_VALUE
                } && homeLibraryOffsets.all { (libraryId, currentOffset) ->
                    currentOffset >= (homeLibraryTotals[libraryId] ?: Int.MAX_VALUE)
                }
            ) {
                break
            }
        }

        return freshItems.shuffled()
    }

    private fun buildExcludedSignature(excludedLibraryIds: Set<String>): String {
        return excludedLibraryIds.toList()
            .sorted()
            .joinToString("|")
    }

    private data class HomePreloadRequest(
        val baseUrl: String,
        val userId: String,
        val accessToken: String,
        val excludedLibrarySignature: String
    )

    private const val HOME_PAGE_SIZE = 12
    private const val HOME_BATCH_SIZE = 18
}
