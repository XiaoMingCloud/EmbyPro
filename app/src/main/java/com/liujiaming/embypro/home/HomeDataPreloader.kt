package com.liujiaming.embypro

import android.content.Context
import java.util.concurrent.Future

/**
 * Data class holding preloaded home screen data.
 * Contains all necessary information to display the home feed immediately.
 */
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

/**
 * Manages background preloading of home screen data during splash screen.
 * Prevents duplicate requests and allows taking the preloaded task when ready.
 */
object HomeDataPreloader {
    private val executor = AppExecutors.io
    private val lock = Any()

    private var activeRequest: HomePreloadRequest? = null
    private var activeTask: Future<Result<PreloadedHomeData>>? = null

    /**
     * Starts preloading home data in the background.
     * Cancels previous request if parameters changed.
     *
     * @param context Application context
     * @param baseUrl Server base URL
     * @param userId User ID
     * @param accessToken Access token
     * @param excludedLibraryIds Set of library IDs to exclude from home
     * @param primaryCategoryKey Primary content category (video/audio)
     */
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

    /**
     * Takes the active preload task if it matches the expected request.
     * Clears the active request after taking.
     *
     * @return Future containing preloaded data or null if no matching task
     */
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

    /**
     * Builds preloaded home data by fetching from the repository.
     */
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

    /**
     * Builds a signature string from excluded library IDs for request comparison.
     */
    private fun buildExcludedSignature(excludedLibraryIds: Set<String>): String {
        return excludedLibraryIds.toList()
            .sorted()
            .joinToString("|")
    }

    /**
     * Data class representing a unique home preload request.
     * Used to detect duplicate requests and prevent redundant loading.
     */
    private data class HomePreloadRequest(
        val baseUrl: String,
        val userId: String,
        val accessToken: String,
        val primaryCategoryKey: String,
        val excludedLibrarySignature: String
    )
}
