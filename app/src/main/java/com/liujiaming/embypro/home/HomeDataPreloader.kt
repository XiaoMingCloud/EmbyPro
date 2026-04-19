package com.liujiaming.embypro

import android.content.Context
import java.util.concurrent.Future

data class PreloadedHomeData(
    val baseUrl: String,
    val userId: String,
    val accessToken: String,
    val primaryCategoryKey: String,
    val excludedLibrarySignature: String,
    val homeFeedItems: List<MediaPosterUiModel>,
    val mediaLibraries: List<MediaLibraryUiModel>,
    val homeLibraryOrder: List<MediaLibraryUiModel>,
    val homeLibraryOffsets: Map<String, Int>,
    val homeLibraryTotals: Map<String, Int>,
    val homeSeenItemIds: Set<String>
)

object HomeDataPreloader {
    private val executor = AppExecutors.io
    private val lock = Any()

    private var activeRequest: HomePreloadRequest? = null
    private var activeTask: Future<Result<PreloadedHomeData>>? = null

    fun preload(
        context: Context,
        baseUrl: String,
        userId: String,
        accessToken: String,
        excludedLibraryIds: Set<String>,
        primaryCategoryKey: String
    ) {
        if (baseUrl.isBlank() || userId.isBlank() || accessToken.isBlank()) return

        val request = HomePreloadRequest(
            baseUrl = baseUrl,
            userId = userId,
            accessToken = accessToken,
            primaryCategoryKey = primaryCategoryKey,
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
        excludedLibraryIds: Set<String>,
        primaryCategoryKey: String
    ): Future<Result<PreloadedHomeData>>? {
        val expectedRequest = HomePreloadRequest(
            baseUrl = baseUrl,
            userId = userId,
            accessToken = accessToken,
            primaryCategoryKey = primaryCategoryKey,
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
        return HomeFeedRepository(context).buildHomeData(
            connection = ServerConnection(
                baseUrl = request.baseUrl,
                userId = request.userId,
                accessToken = request.accessToken
            ),
            excludedLibraryIds = excludedLibraryIds,
            primaryCategoryKey = request.primaryCategoryKey
        ).getOrThrow().copy(
            primaryCategoryKey = request.primaryCategoryKey,
            excludedLibrarySignature = request.excludedLibrarySignature
        )
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
        val primaryCategoryKey: String,
        val excludedLibrarySignature: String
    )
}
