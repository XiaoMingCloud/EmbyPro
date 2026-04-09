package com.liujiaming.embypro

import android.content.Context
import android.provider.Settings
import android.util.Base64
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class ServerInfo(
    val serverId: String,
    val serverName: String,
    val version: String
)

data class LoginResult(
    val accessToken: String,
    val userId: String,
    val userName: String
)

data class LibrarySectionUiModel(
    val library: MediaLibraryUiModel,
    val items: List<MediaPosterUiModel>
)

data class ServerHomeUiModel(
    val continueWatching: List<MediaPosterUiModel>,
    val mediaLibraries: List<MediaLibraryUiModel>,
    val librarySections: Map<String, List<MediaPosterUiModel>>
)

data class LibraryItemsPageUiModel(
    val items: List<MediaPosterUiModel>,
    val totalCount: Int
)

data class LibraryFilterOptionsUiModel(
    val genres: List<String>,
    val tags: List<String>
)

data class PlaybackHistoryItemUiModel(
    val itemId: String,
    val title: String,
    val libraryName: String,
    val playedTimeLabel: String,
    val imageUrl: String?,
    val itemType: String,
    val playbackPositionTicks: Long = 0L,
    val runtimeTicks: Long = 0L,
    val played: Boolean = false
)

data class PlaybackHistoryPageUiModel(
    val items: List<PlaybackHistoryItemUiModel>,
    val totalCount: Int
)

data class FavoriteItemsPageUiModel(
    val items: List<PlaybackHistoryItemUiModel>,
    val totalCount: Int
)

enum class LibraryBrowseMode {
    ALL,
    CONTINUE,
    FAVORITES,
    GENRES,
    TAGS,
    COLLECTIONS,
    FOLDERS
}

enum class PlaybackHistoryCategory(val includeItemTypes: String, val labelRes: Int) {
    ALL("Movie,Episode,Video,MusicVideo,Series,Season,BoxSet,Program,TvChannel", R.string.tab_all),
    VIDEO("Movie,Episode,Video,MusicVideo", R.string.playback_history_tab_video),
    LIVE("Program,TvChannel", R.string.playback_history_tab_live),
    COLUMN("Series,Season,BoxSet", R.string.playback_history_tab_column)
}

enum class LibrarySortField(val apiValue: String, val labelRes: Int) {
    DATE_MODIFIED("DateModified", R.string.sort_date_modified),
    DATE_CREATED("DateCreated", R.string.sort_date_created),
    TITLE("SortName", R.string.sort_title),
    IMDB_RATING("CommunityRating", R.string.sort_imdb_rating),
    CRITIC_RATING("CriticRating", R.string.sort_critic_rating),
    PRODUCTION_YEAR("ProductionYear", R.string.sort_production_year),
    PREMIERE_DATE("PremiereDate", R.string.sort_premiere_date),
    OFFICIAL_RATING("OfficialRating", R.string.sort_official_rating),
    DATE_PLAYED("DatePlayed", R.string.sort_date_played),
    PLAYBACK_DURATION("Runtime", R.string.sort_playback_duration),
    RANDOM("Random", R.string.sort_random)
}

data class ChapterUiModel(
    val title: String,
    val startLabel: String,
    val imageUrl: String?
)

data class MediaInfoCardUiModel(
    val title: String,
    val lines: List<String>
)

data class VideoDetailUiModel(
    val itemId: String,
    val title: String,
    val overview: String,
    val runtimeLabel: String,
    val versionLine: String,
    val audioLine: String,
    val subtitleLine: String,
    val studioLine: String,
    val mediaTitleLine: String,
    val chapters: List<ChapterUiModel>,
    val mediaInfoCards: List<MediaInfoCardUiModel>,
    val heroImageUrl: String?,
    val isFavorite: Boolean,
    val playbackPositionTicks: Long,
    val playbackUrl: String?,
    val playSessionId: String
)

class EmbyApiService(
    private val context: Context
) {
    private val client = OkHttpClient()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val localMediaCache = LocalMediaCache(context.applicationContext)

    fun buildBaseUrl(address: String, port: String): String {
        val trimmedAddress = address.trim().removeSuffix("/")
        val normalizedAddress = if (
            trimmedAddress.startsWith("http://") || trimmedAddress.startsWith("https://")
        ) {
            trimmedAddress
        } else {
            "https://$trimmedAddress"
        }

        if (port.isBlank()) {
            return normalizedAddress
        }

        val scheme = normalizedAddress.substringBefore("://")
        val hostAndPath = normalizedAddress.substringAfter("://")
        val host = hostAndPath.substringBefore("/").substringBefore(":")
        val path = hostAndPath.substringAfter("/", "")

        return buildString {
            append(scheme)
            append("://")
            append(host)
            append(":")
            append(port)
            if (path.isNotBlank()) {
                append("/")
                append(path)
            }
        }.removeSuffix("/")
    }

    fun parseBaseUrl(baseUrl: String): Pair<String, String> {
        val httpUrl = baseUrl.toHttpUrlOrNull() ?: return baseUrl to ""
        val defaultPort = if (httpUrl.isHttps) 443 else 80
        val address = httpUrl.newBuilder().port(defaultPort).build().toString().removeSuffix("/")
        val port = if (httpUrl.port == defaultPort) "" else httpUrl.port.toString()
        return address to port
    }

    fun fetchPublicServerInfo(baseUrl: String): Result<ServerInfo> {
        return runCatching {
            val request = Request.Builder()
                .url("$baseUrl/emby/System/Info/Public")
                .header("X-Emby-Authorization", buildAuthorizationHeader())
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("连接服务器失败：${response.code}")
                }

                val json = JSONObject(response.body?.string().orEmpty())
                ServerInfo(
                    serverId = json.optString("Id"),
                    serverName = json.optString("ServerName"),
                    version = json.optString("Version")
                )
            }
        }
    }

    fun authenticate(
        baseUrl: String,
        username: String,
        password: String
    ): Result<LoginResult> {
        return runCatching {
            val requestBody = JSONObject()
                .put("Username", username)
                .put("Pw", password)
                .toString()
                .toRequestBody(jsonMediaType)

            val request = Request.Builder()
                .url("$baseUrl/emby/Users/AuthenticateByName")
                .header("X-Emby-Authorization", buildAuthorizationHeader())
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("登录失败：${response.code}")
                }

                val json = JSONObject(response.body?.string().orEmpty())
                val userJson = json.optJSONObject("User") ?: JSONObject()
                LoginResult(
                    accessToken = json.optString("AccessToken"),
                    userId = userJson.optString("Id"),
                    userName = userJson.optString("Name", username)
                )
            }
        }
    }

    fun buildUserAvatarUrl(
        baseUrl: String,
        userId: String,
        maxWidth: Int = 256,
        maxHeight: Int = 256
    ): String? {
        if (userId.isBlank()) return null
        return buildString {
            append(baseUrl)
            append("/emby/Users/")
            append(userId)
            append("/Images/Primary")
            append("?maxWidth=")
            append(maxWidth)
            append("&maxHeight=")
            append(maxHeight)
        }
    }

    fun updateUserAvatar(
        baseUrl: String,
        userId: String,
        accessToken: String,
        imageBytes: ByteArray
    ): Result<String> {
        return runCatching {
            val request = Request.Builder()
                .url("$baseUrl/emby/Users/$userId/Images/Primary")
                .header("X-Emby-Token", accessToken)
                .post(
                    Base64.encodeToString(imageBytes, Base64.NO_WRAP)
                        .toRequestBody("text/plain; charset=utf-8".toMediaType())
                )
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("更新用户头像失败：${response.code}")
                }
            }

            buildUserAvatarUrl(baseUrl, userId).orEmpty() + "&v=" + System.currentTimeMillis()
        }
    }

    fun fetchServerHomeData(baseUrl: String, userId: String, accessToken: String): Result<ServerHomeUiModel> {
        return runCatching {
            val cacheKey = buildHomeCacheKey(baseUrl, userId)
            val cachedJson = localMediaCache.readJson(cacheKey, HOME_CACHE_MAX_AGE_MS)
            if (!cachedJson.isNullOrBlank()) {
                return@runCatching parseServerHomeCache(JSONObject(cachedJson))
            }

            try {
                val continueWatching = fetchResumeItems(baseUrl, userId, accessToken)
                val views = fetchViews(baseUrl, userId, accessToken)
                val sections = linkedMapOf<String, List<MediaPosterUiModel>>()

                views.forEach { view ->
                    sections[view.id] = fetchLibraryItemsPage(
                        baseUrl = baseUrl,
                        userId = userId,
                        accessToken = accessToken,
                        parentId = view.id,
                        startIndex = 0,
                        limit = 12
                    ).items
                }

                val home = ServerHomeUiModel(
                    continueWatching = continueWatching,
                    mediaLibraries = views,
                    librarySections = sections
                )
                localMediaCache.writeJson(cacheKey, buildServerHomeCache(home).toString())
                home
            } catch (error: Throwable) {
                val staleJson = localMediaCache.readJsonAnyAge(cacheKey)
                if (!staleJson.isNullOrBlank()) {
                    parseServerHomeCache(JSONObject(staleJson))
                } else {
                    throw error
                }
            }
        }
    }

    fun fetchMediaLibraries(baseUrl: String, userId: String, accessToken: String): Result<List<MediaLibraryUiModel>> {
        return runCatching {
            val cacheKey = "views::$baseUrl::$userId"
            val cachedJson = localMediaCache.readJson(cacheKey, LIBRARY_CACHE_MAX_AGE_MS)
            if (!cachedJson.isNullOrBlank()) {
                return@runCatching parseLibraryItems(JSONArray(cachedJson))
            }

            try {
                val views = fetchViews(baseUrl, userId, accessToken)
                localMediaCache.writeJson(cacheKey, views.toLibraryJsonArray().toString())
                views
            } catch (error: Throwable) {
                val staleJson = localMediaCache.readJsonAnyAge(cacheKey)
                if (!staleJson.isNullOrBlank()) {
                    parseLibraryItems(JSONArray(staleJson))
                } else {
                    throw error
                }
            }
        }
    }

    fun fetchVideoDetail(
        baseUrl: String,
        userId: String,
        accessToken: String,
        itemId: String
    ): Result<VideoDetailUiModel> {
        return runCatching {
            val itemRequest = Request.Builder()
                .url(
                    "$baseUrl/emby/Users/$userId/Items/$itemId" +
                        "?Fields=Chapters,MediaSources,MediaStreams,Overview,People,Studios,Genres,Taglines,ImageTags,UserData"
                )
                .header("X-Emby-Token", accessToken)
                .get()
                .build()

            val itemJson = client.newCall(itemRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("读取视频详情失败：${response.code}")
                }
                JSONObject(response.body?.string().orEmpty())
            }

            val playbackRequest = Request.Builder()
                .url("$baseUrl/emby/Items/$itemId/PlaybackInfo?UserId=$userId")
                .header("X-Emby-Token", accessToken)
                .post("{}".toRequestBody(jsonMediaType))
                .build()

            val playbackJson = client.newCall(playbackRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    JSONObject()
                } else {
                    JSONObject(response.body?.string().orEmpty())
                }
            }

            buildVideoDetailUiModel(baseUrl, itemJson, playbackJson)
        }
    }

    fun setFavoriteState(
        baseUrl: String,
        userId: String,
        accessToken: String,
        itemId: String,
        favorite: Boolean
    ): Result<Unit> {
        return runCatching {
            val requestBuilder = Request.Builder()
                .url("$baseUrl/emby/Users/$userId/FavoriteItems/$itemId")
                .header("X-Emby-Token", accessToken)

            val request = if (favorite) {
                requestBuilder.post("".toRequestBody(null)).build()
            } else {
                requestBuilder.delete().build()
            }

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("更新收藏状态失败：${response.code}")
                }
            }
        }
    }

    fun deleteItem(
        baseUrl: String,
        accessToken: String,
        itemId: String
    ): Result<Unit> {
        return runCatching {
            val request = Request.Builder()
                .url("$baseUrl/emby/Items/$itemId")
                .header("X-Emby-Token", accessToken)
                .delete()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("删除视频失败：${response.code}")
                }
            }
        }
    }

    fun updatePlaybackProgress(
        baseUrl: String,
        userId: String,
        accessToken: String,
        itemId: String,
        playbackPositionMs: Long
    ): Result<Unit> {
        return runCatching {
            val ticks = playbackPositionMs.coerceAtLeast(0L) * 10_000L
            val requestBody = JSONObject()
                .put("PlaybackPositionTicks", ticks)
                .toString()
                .toRequestBody(jsonMediaType)

            val request = Request.Builder()
                .url("$baseUrl/emby/Users/$userId/Items/$itemId/UserData")
                .header("X-Emby-Token", accessToken)
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("更新播放进度失败：${response.code}")
                }
            }
        }
    }

    fun fetchLibraryItemsPage(
        baseUrl: String,
        userId: String,
        accessToken: String,
        parentId: String,
        startIndex: Int,
        limit: Int,
        mode: LibraryBrowseMode = LibraryBrowseMode.ALL,
        filterValue: String? = null,
        sortField: LibrarySortField = LibrarySortField.TITLE,
        sortDescending: Boolean = true
    ): LibraryItemsPageUiModel {
        val cacheKey = buildLibraryPageCacheKey(
            baseUrl,
            userId,
            parentId,
            startIndex,
            limit,
            mode,
            filterValue,
            sortField,
            sortDescending
        )
        val cachedJson = localMediaCache.readJson(cacheKey, LIBRARY_CACHE_MAX_AGE_MS)
        if (!cachedJson.isNullOrBlank()) {
            return parseLibraryItemsPageCache(JSONObject(cachedJson))
        }

        try {
            val request = Request.Builder()
                .url(
                    buildLibraryItemsUrl(
                        baseUrl,
                        userId,
                        parentId,
                        startIndex,
                        limit,
                        mode,
                        filterValue,
                        sortField,
                        sortDescending
                    )
                )
                .header("X-Emby-Token", accessToken)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("读取媒体内容失败：${response.code}")
                }

                val json = JSONObject(response.body?.string().orEmpty())
                val items = parseMediaItems(baseUrl, json.optJSONArray("Items"))
                return LibraryItemsPageUiModel(
                    items = items,
                    totalCount = json.optInt("TotalRecordCount", items.size + startIndex)
                ).also { page ->
                    localMediaCache.writeJson(cacheKey, buildLibraryItemsPageCache(page).toString())
                }
            }
        } catch (error: Throwable) {
            val staleJson = localMediaCache.readJsonAnyAge(cacheKey)
            if (!staleJson.isNullOrBlank()) {
                return parseLibraryItemsPageCache(JSONObject(staleJson))
            }
            throw error
        }
    }

    fun fetchLibraryFilterOptions(
        baseUrl: String,
        userId: String,
        accessToken: String,
        parentId: String
    ): Result<LibraryFilterOptionsUiModel> {
        return runCatching {
            val cacheKey = "filters::$baseUrl::$userId::$parentId"
            val cachedJson = localMediaCache.readJson(cacheKey, FILTER_CACHE_MAX_AGE_MS)
            if (!cachedJson.isNullOrBlank()) {
                return@runCatching parseLibraryFilterOptions(JSONObject(cachedJson))
            }

            try {
                val request = Request.Builder()
                    .url(
                        "$baseUrl/emby/Items/Filters?UserId=$userId&ParentId=$parentId" +
                            "&IncludeItemTypes=Movie,Episode,Video,Series,MusicVideo&Recursive=true"
                    )
                    .header("X-Emby-Token", accessToken)
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IllegalStateException("读取媒体库筛选项失败：${response.code}")
                    }

                    val json = JSONObject(response.body?.string().orEmpty())
                    val options = LibraryFilterOptionsUiModel(
                        genres = parseSimpleNames(json.optJSONArray("Genres")),
                        tags = parseSimpleNames(json.optJSONArray("Tags"))
                    )
                    localMediaCache.writeJson(cacheKey, buildLibraryFilterOptionsCache(options).toString())
                    options
                }
            } catch (error: Throwable) {
                val staleJson = localMediaCache.readJsonAnyAge(cacheKey)
                if (!staleJson.isNullOrBlank()) {
                    parseLibraryFilterOptions(JSONObject(staleJson))
                } else {
                    throw error
                }
            }
        }
    }

    fun searchMediaItemsPage(
        baseUrl: String,
        userId: String,
        accessToken: String,
        query: String,
        startIndex: Int,
        limit: Int
    ): Result<LibraryItemsPageUiModel> {
        return runCatching {
            val request = Request.Builder()
                .url(buildSearchItemsUrl(baseUrl, userId, query, startIndex, limit))
                .header("X-Emby-Token", accessToken)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("搜索内容失败：${response.code}")
                }

                val json = JSONObject(response.body?.string().orEmpty())
                val items = parseMediaItems(baseUrl, json.optJSONArray("Items"))
                LibraryItemsPageUiModel(
                    items = items,
                    totalCount = json.optInt("TotalRecordCount", items.size + startIndex)
                )
            }
        }
    }

    fun fetchPlaybackHistoryPage(
        baseUrl: String,
        userId: String,
        accessToken: String,
        startIndex: Int,
        limit: Int,
        category: PlaybackHistoryCategory = PlaybackHistoryCategory.ALL
    ): Result<PlaybackHistoryPageUiModel> {
        return runCatching {
            val request = Request.Builder()
                .url(buildPlaybackHistoryUrl(baseUrl, userId, startIndex, limit, category))
                .header("X-Emby-Token", accessToken)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("读取播放历史失败：${response.code}")
                }

                val json = JSONObject(response.body?.string().orEmpty())
                val libraryTitleMap = runCatching {
                    fetchViews(baseUrl, userId, accessToken).associate { it.id to it.title }
                }.getOrDefault(emptyMap())
                val libraryNameCache = mutableMapOf<String, String>()
                val items = buildPlaybackHistoryItems(
                    baseUrl = baseUrl,
                    accessToken = accessToken,
                    items = json.optJSONArray("Items"),
                    libraryTitleMap = libraryTitleMap,
                    libraryNameCache = libraryNameCache
                )

                PlaybackHistoryPageUiModel(
                    items = items,
                    totalCount = json.optInt("TotalRecordCount", startIndex + items.size)
                )
            }
        }
    }

    fun clearPlayedState(
        baseUrl: String,
        userId: String,
        accessToken: String,
        itemId: String
    ): Result<Unit> {
        return runCatching {
            val request = Request.Builder()
                .url("$baseUrl/emby/Users/$userId/PlayedItems/$itemId")
                .header("X-Emby-Token", accessToken)
                .delete()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("清除播放历史失败：${response.code}")
                }
            }
        }
    }

    fun fetchFavoriteItemsPage(
        baseUrl: String,
        userId: String,
        accessToken: String,
        startIndex: Int,
        limit: Int
    ): Result<FavoriteItemsPageUiModel> {
        return runCatching {
            val request = Request.Builder()
                .url(buildFavoriteItemsUrl(baseUrl, userId, startIndex, limit))
                .header("X-Emby-Token", accessToken)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("读取收藏夹失败：${response.code}")
                }

                val json = JSONObject(response.body?.string().orEmpty())
                val libraryTitleMap = runCatching {
                    fetchViews(baseUrl, userId, accessToken).associate { it.id to it.title }
                }.getOrDefault(emptyMap())
                val libraryNameCache = mutableMapOf<String, String>()
                val items = buildFavoriteItems(
                    baseUrl = baseUrl,
                    accessToken = accessToken,
                    items = json.optJSONArray("Items"),
                    libraryTitleMap = libraryTitleMap,
                    libraryNameCache = libraryNameCache
                )

                FavoriteItemsPageUiModel(
                    items = items,
                    totalCount = json.optInt("TotalRecordCount", startIndex + items.size)
                )
            }
        }
    }

    private fun fetchResumeItems(baseUrl: String, userId: String, accessToken: String): List<MediaPosterUiModel> {
        val request = Request.Builder()
            .url(
                "$baseUrl/emby/Users/$userId/Items/Resume?Limit=12&MediaTypes=Video" +
                    "&Recursive=true&Fields=ImageTags,PrimaryImageAspectRatio,UserData,SeriesName" +
                    "&EnableImageTypes=Primary,Thumb,Backdrop&ImageTypeLimit=1"
            )
            .header("X-Emby-Token", accessToken)
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("读取继续观看失败：${response.code}")
            }

            val json = JSONObject(response.body?.string().orEmpty())
            return parseMediaItems(baseUrl, json.optJSONArray("Items"))
        }
    }

    private fun fetchViews(baseUrl: String, userId: String, accessToken: String): List<MediaLibraryUiModel> {
        val request = Request.Builder()
            .url("$baseUrl/emby/Users/$userId/Views")
            .header("X-Emby-Token", accessToken)
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("读取媒体库失败：${response.code}")
            }

            val json = JSONObject(response.body?.string().orEmpty())
            val items = json.optJSONArray("Items")
            return buildList {
                for (index in 0 until (items?.length() ?: 0)) {
                    val item = items?.optJSONObject(index) ?: continue
                    val imageInfo = resolveImageInfo(item)
                    add(
                        MediaLibraryUiModel(
                            id = item.optString("Id"),
                            title = item.optString("Name", context.getString(R.string.media_library)),
                            style = styleForIndex(index),
                            imageUrl = buildImageUrl(
                                baseUrl = baseUrl,
                                imageItemId = imageInfo.first,
                                imageType = imageInfo.second,
                                imageTag = imageInfo.third,
                                maxWidth = 240,
                                maxHeight = 240
                            ),
                            totalCount = item.optInt("ChildCount", 0)
                        )
                    )
                }
            }
        }
    }

    private fun buildPlaybackHistoryItems(
        baseUrl: String,
        accessToken: String,
        items: JSONArray?,
        libraryTitleMap: Map<String, String>,
        libraryNameCache: MutableMap<String, String>
    ): List<PlaybackHistoryItemUiModel> {
        return buildList {
            for (index in 0 until (items?.length() ?: 0)) {
                val item = items?.optJSONObject(index) ?: continue
                val userData = item.optJSONObject("UserData")
                val lastPlayedDate = userData?.optString("LastPlayedDate").orEmpty()
                val playbackPositionTicks = userData?.optLong("PlaybackPositionTicks") ?: 0L
                val played = userData?.optBoolean("Played") == true
                if (lastPlayedDate.isBlank() && playbackPositionTicks <= 0L && !played) continue

                val itemId = item.optString("Id")
                if (itemId.isBlank()) continue

                val imageInfo = resolveImageInfo(item)
                add(
                    PlaybackHistoryItemUiModel(
                        itemId = itemId,
                        title = item.optString("Name", context.getString(R.string.untitled_media)),
                        libraryName = resolveHistoryLibraryName(
                            baseUrl = baseUrl,
                            accessToken = accessToken,
                            itemId = itemId,
                            libraryTitleMap = libraryTitleMap,
                            libraryNameCache = libraryNameCache
                        ),
                        playedTimeLabel = formatPlaybackHistoryTime(lastPlayedDate),
                        imageUrl = buildImageUrl(
                            baseUrl = baseUrl,
                            imageItemId = imageInfo.first,
                            imageType = imageInfo.second,
                            imageTag = imageInfo.third,
                            maxWidth = 420,
                            maxHeight = 236
                        ),
                        itemType = item.optString("Type"),
                        playbackPositionTicks = playbackPositionTicks,
                        runtimeTicks = item.optLong("RunTimeTicks"),
                        played = played
                    )
                )
            }
        }
    }

    private fun buildFavoriteItems(
        baseUrl: String,
        accessToken: String,
        items: JSONArray?,
        libraryTitleMap: Map<String, String>,
        libraryNameCache: MutableMap<String, String>
    ): List<PlaybackHistoryItemUiModel> {
        return buildList {
            for (index in 0 until (items?.length() ?: 0)) {
                val item = items?.optJSONObject(index) ?: continue
                val itemId = item.optString("Id")
                if (itemId.isBlank()) continue
                val imageInfo = resolveImageInfo(item)
                add(
                    PlaybackHistoryItemUiModel(
                        itemId = itemId,
                        title = item.optString("Name", context.getString(R.string.untitled_media)),
                        libraryName = resolveHistoryLibraryName(
                            baseUrl = baseUrl,
                            accessToken = accessToken,
                            itemId = itemId,
                            libraryTitleMap = libraryTitleMap,
                            libraryNameCache = libraryNameCache
                        ),
                        playedTimeLabel = "",
                        imageUrl = buildImageUrl(
                            baseUrl = baseUrl,
                            imageItemId = imageInfo.first,
                            imageType = imageInfo.second,
                            imageTag = imageInfo.third,
                            maxWidth = 420,
                            maxHeight = 236
                        ),
                        itemType = item.optString("Type"),
                        playbackPositionTicks = 0L,
                        runtimeTicks = item.optLong("RunTimeTicks"),
                        played = false
                    )
                )
            }
        }
    }

    private fun parseMediaItems(baseUrl: String, items: JSONArray?): List<MediaPosterUiModel> {
        return buildList {
            for (index in 0 until (items?.length() ?: 0)) {
                val item = items?.optJSONObject(index) ?: continue
                val userData = item.optJSONObject("UserData")
                val playedPercentage = userData?.optDouble("PlayedPercentage", Double.NaN) ?: Double.NaN
                val imageInfo = resolveImageInfo(item)
                add(
                    MediaPosterUiModel(
                        id = item.optString("Id"),
                        title = item.optString("Name", context.getString(R.string.untitled_media)),
                        subtitle = if (!playedPercentage.isNaN()) {
                            context.getString(R.string.played_percentage, playedPercentage.toInt())
                        } else if (item.optBoolean("IsFolder")) {
                            item.optInt("ChildCount").takeIf { it > 0 }?.let {
                                context.getString(R.string.library_total_count, it)
                            }.orEmpty()
                        } else {
                            item.optString("SeriesName")
                        },
                        style = styleForIndex(index),
                        imageUrl = buildImageUrl(
                            baseUrl = baseUrl,
                            imageItemId = imageInfo.first,
                            imageType = imageInfo.second,
                            imageTag = imageInfo.third,
                            maxWidth = 480,
                            maxHeight = 320
                        ),
                        isFolder = item.optBoolean("IsFolder"),
                        itemType = item.optString("Type")
                    )
                )
            }
        }
    }

    private fun resolveImageInfo(item: JSONObject): Triple<String, String, String> {
        val imageTags = item.optJSONObject("ImageTags")
        val itemId = item.optString("Id")
        val primaryTag = item.optString("PrimaryImageTag").ifBlank { imageTags?.optString("Primary").orEmpty() }
        if (primaryTag.isNotBlank()) {
            val imageItemId = item.optString("PrimaryImageItemId").ifBlank { itemId }
            return Triple(imageItemId, "Primary", primaryTag)
        }

        val thumbTag = imageTags?.optString("Thumb").orEmpty().ifBlank { item.optString("ThumbImageTag") }
        if (thumbTag.isNotBlank()) {
            val imageItemId = item.optString("ParentThumbItemId").ifBlank { itemId }
            return Triple(imageItemId, "Thumb", thumbTag)
        }

        val backdropTags = item.optJSONArray("BackdropImageTags")
        val backdropTag = backdropTags?.optString(0).orEmpty()
        if (backdropTag.isNotBlank()) {
            return Triple(itemId, "Backdrop", backdropTag)
        }

        return Triple(itemId, "Primary", "")
    }

    private fun buildVideoDetailUiModel(
        baseUrl: String,
        item: JSONObject,
        playbackInfo: JSONObject
    ): VideoDetailUiModel {
        val mediaSources = playbackInfo.optJSONArray("MediaSources")
            ?: item.optJSONArray("MediaSources")
            ?: JSONArray()
        val firstMediaSource = mediaSources.optJSONObject(0) ?: JSONObject()
        val mediaStreams = firstMediaSource.optJSONArray("MediaStreams")
            ?: item.optJSONArray("MediaStreams")
            ?: JSONArray()

        val videoStreams = mutableListOf<String>()
        val audioStreams = mutableListOf<String>()

        for (index in 0 until mediaStreams.length()) {
            val stream = mediaStreams.optJSONObject(index) ?: continue
            when (stream.optString("Type")) {
                "Video" -> {
                    videoStreams.add("类型：${stream.optString("Type", "Video")}")
                    if (stream.optString("Codec").isNotBlank()) videoStreams.add("编码：${stream.optString("Codec")}")
                    if (stream.optString("DisplayTitle").isNotBlank()) videoStreams.add("显示标题：${stream.optString("DisplayTitle")}")
                    if (stream.optString("Language").isNotBlank()) videoStreams.add("语言：${stream.optString("Language")}")
                    val bitrate = stream.optLong("BitRate")
                    if (bitrate > 0) videoStreams.add("码率：${formatBitrate(bitrate)}")
                    if (stream.optInt("Width") > 0 && stream.optInt("Height") > 0) {
                        videoStreams.add("宽高：${stream.optInt("Width")} x ${stream.optInt("Height")}")
                    }
                    val frameRate = stream.optDouble("RealFrameRate", Double.NaN)
                    if (!frameRate.isNaN()) videoStreams.add("帧率：${"%.3f".format(frameRate)}")
                }

                "Audio" -> {
                    audioStreams.add("类型：${stream.optString("Type", "Audio")}")
                    if (stream.optString("Codec").isNotBlank()) audioStreams.add("编码：${stream.optString("Codec")}")
                    if (stream.optString("DisplayTitle").isNotBlank()) audioStreams.add("显示标题：${stream.optString("DisplayTitle")}")
                    if (stream.optString("Language").isNotBlank()) audioStreams.add("语言：${stream.optString("Language")}")
                    val bitrate = stream.optLong("BitRate")
                    if (bitrate > 0) audioStreams.add("码率：${formatBitrate(bitrate)}")
                    audioStreams.add("默认：${stream.optBoolean("IsDefault")}")
                    audioStreams.add("外部：${stream.optBoolean("IsExternal")}")
                }
            }
        }

        val chapters = buildList {
            val chapterArray = item.optJSONArray("Chapters") ?: JSONArray()
            for (index in 0 until chapterArray.length()) {
                val chapter = chapterArray.optJSONObject(index) ?: continue
                add(
                    ChapterUiModel(
                        title = chapter.optString("Name", "章节 ${index + 1}"),
                        startLabel = formatTicks(chapter.optLong("StartPositionTicks")),
                        imageUrl = buildChapterImageUrl(
                            baseUrl = baseUrl,
                            itemId = item.optString("Id"),
                            chapterIndex = index,
                            imageTag = chapter.optString("ImageTag")
                        )
                    )
                )
            }
        }

        val imageInfo = resolveImageInfo(item)
        val studios = item.optJSONArray("Studios")
        val people = item.optJSONArray("People")
        val userData = item.optJSONObject("UserData")
        val playbackPositionTicks = userData?.optLong("PlaybackPositionTicks") ?: 0L
        val tagLines = item.optJSONArray("Taglines")
        val mediaSourceId = firstMediaSource.optString("Id")
        val directStreamUrl = firstMediaSource.optString("DirectStreamUrl")
        val playSessionId = playbackInfo.optString("PlaySessionId")

        return VideoDetailUiModel(
            itemId = item.optString("Id"),
            title = item.optString("Name", context.getString(R.string.untitled_media)),
            overview = item.optString("Overview").ifBlank { item.optString("Tagline") },
            runtimeLabel = formatTicks(item.optLong("RunTimeTicks")),
            versionLine = buildVersionLine(item, firstMediaSource, tagLines),
            audioLine = buildAudioLine(mediaStreams),
            subtitleLine = buildSubtitleLine(item, firstMediaSource, studios, people),
            studioLine = buildStudioLine(studios, people),
            mediaTitleLine = buildMediaTitleLine(item),
            chapters = chapters,
            mediaInfoCards = listOf(
                MediaInfoCardUiModel("视频", if (videoStreams.isEmpty()) listOf("暂无视频流信息") else videoStreams),
                MediaInfoCardUiModel("音频", if (audioStreams.isEmpty()) listOf("暂无音频流信息") else audioStreams)
            ),
            heroImageUrl = buildImageUrl(
                baseUrl = baseUrl,
                imageItemId = imageInfo.first,
                imageType = imageInfo.second,
                imageTag = imageInfo.third,
                maxWidth = 960,
                maxHeight = 720
            ),
            isFavorite = userData?.optBoolean("IsFavorite") == true,
            playbackPositionTicks = playbackPositionTicks,
            playbackUrl = buildPlaybackUrl(
                baseUrl = baseUrl,
                itemId = item.optString("Id"),
                mediaSourceId = mediaSourceId,
                directStreamUrl = directStreamUrl,
                staticBuild = true
            ),
            playSessionId = playSessionId
        )
    }

    private fun buildVersionLine(item: JSONObject, mediaSource: JSONObject, tagLines: JSONArray?): String {
        val parts = mutableListOf<String>()
        val fileName = mediaSource.optString("Name").ifBlank { item.optString("Name") }
        if (fileName.isNotBlank()) parts.add(fileName)
        val dateCreated = item.optString("DateCreated")
        if (dateCreated.isNotBlank()) parts.add(dateCreated.replace("T", " ").replace("Z", ""))
        val size = mediaSource.optLong("Size")
        if (size > 0) parts.add(formatBytes(size))
        if (tagLines != null && tagLines.length() > 0) {
            val tag = tagLines.optString(0)
            if (tag.isNotBlank()) parts.add(tag)
        }
        return parts.joinToString("  ")
    }

    private fun buildAudioLine(mediaStreams: JSONArray): String {
        for (index in 0 until mediaStreams.length()) {
            val stream = mediaStreams.optJSONObject(index) ?: continue
            if (stream.optString("Type") == "Audio") {
                val parts = mutableListOf<String>()
                if (stream.optString("Language").isNotBlank()) parts.add(stream.optString("Language"))
                if (stream.optString("DisplayTitle").isNotBlank()) parts.add(stream.optString("DisplayTitle"))
                if (stream.optString("Codec").isNotBlank()) parts.add(stream.optString("Codec"))
                return parts.joinToString(" ")
            }
        }
        return "暂无音频信息"
    }

    private fun buildSubtitleLine(
        item: JSONObject,
        mediaSource: JSONObject,
        studios: JSONArray?,
        people: JSONArray?
    ): String {
        val parts = mutableListOf<String>()
        val container = mediaSource.optString("Container")
        if (container.isNotBlank()) parts.add(container.uppercase())
        val actors = buildPeopleLine(people)
        if (actors.isNotBlank()) parts.add(actors)
        val studioText = studios?.optJSONObject(0)?.optString("Name").orEmpty()
        if (studioText.isNotBlank()) parts.add(studioText)
        return parts.joinToString("  ")
    }

    private fun buildStudioLine(studios: JSONArray?, people: JSONArray?): String {
        val studioNames = buildList {
            for (index in 0 until (studios?.length() ?: 0)) {
                val studio = studios?.optJSONObject(index) ?: continue
                val name = studio.optString("Name")
                if (name.isNotBlank()) add(name)
            }
        }
        val actorNames = buildPeopleLine(people)
        return listOf(studioNames.joinToString(", "), actorNames).filter { it.isNotBlank() }.joinToString("\n")
    }

    private fun buildMediaTitleLine(item: JSONObject): String {
        val parts = mutableListOf<String>()
        val productionYear = item.optInt("ProductionYear")
        if (productionYear > 0) parts.add(productionYear.toString())
        if (item.optLong("RunTimeTicks") > 0) parts.add(formatTicks(item.optLong("RunTimeTicks")))
        return parts.joinToString(" ")
    }

    private fun buildPeopleLine(people: JSONArray?): String {
        return buildList {
            for (index in 0 until (people?.length() ?: 0)) {
                val person = people?.optJSONObject(index) ?: continue
                val name = person.optString("Name")
                if (name.isNotBlank()) add(name)
            }
        }.take(5).joinToString(", ")
    }

    private fun buildChapterImageUrl(
        baseUrl: String,
        itemId: String,
        chapterIndex: Int,
        imageTag: String
    ): String? {
        if (itemId.isBlank()) return null
        return buildString {
            append(baseUrl)
            append("/emby/Items/")
            append(itemId)
            append("/Images/Chapter/")
            append(chapterIndex)
            append("?maxWidth=320&maxHeight=180")
            if (imageTag.isNotBlank()) {
                append("&tag=")
                append(imageTag)
            }
        }
    }

    private fun formatTicks(ticks: Long): String {
        if (ticks <= 0) return "00:00"
        val totalSeconds = ticks / 10_000_000L
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    private fun formatBytes(bytes: Long): String {
        val mb = bytes / 1024.0 / 1024.0
        val gb = mb / 1024.0
        return if (gb >= 1) {
            String.format("%.1f GB", gb)
        } else {
            String.format("%.1f MB", mb)
        }
    }

    private fun formatBitrate(bitRate: Long): String {
        return String.format("%.1f Kbps", bitRate / 1000.0)
    }

    private fun buildPlaybackUrl(
        baseUrl: String,
        itemId: String,
        mediaSourceId: String,
        directStreamUrl: String,
        staticBuild: Boolean
    ): String? {
        if (directStreamUrl.isNotBlank()) {
            return if (directStreamUrl.startsWith("http")) directStreamUrl else "$baseUrl$directStreamUrl"
        }
        if (itemId.isBlank()) return null

        return buildString {
            append(baseUrl)
            append("/emby/Videos/")
            append(itemId)
            append("/stream")
            if (staticBuild) append(".mp4")
            append("?static=true")
            if (mediaSourceId.isNotBlank()) {
                append("&MediaSourceId=")
                append(mediaSourceId)
            }
        }
    }

    private fun buildImageUrl(
        baseUrl: String,
        imageItemId: String,
        imageType: String,
        imageTag: String,
        maxWidth: Int,
        maxHeight: Int
    ): String? {
        if (imageItemId.isBlank()) return null
        return buildString {
            append(baseUrl)
            append("/emby/Items/")
            append(imageItemId)
            append("/Images/")
            append(imageType)
            append("?maxWidth=")
            append(maxWidth)
            append("&maxHeight=")
            append(maxHeight)
            if (imageTag.isNotBlank()) {
                append("&tag=")
                append(imageTag)
            }
        }
    }

    private fun styleForIndex(index: Int): ServerIconStyle {
        val styles = ServerIconStyle.entries
        return styles[index % styles.size]
    }

    private fun buildHomeCacheKey(baseUrl: String, userId: String): String {
        return "home::$baseUrl::$userId"
    }

    private fun buildLibraryPageCacheKey(
        baseUrl: String,
        userId: String,
        parentId: String,
        startIndex: Int,
        limit: Int,
        mode: LibraryBrowseMode,
        filterValue: String?,
        sortField: LibrarySortField,
        sortDescending: Boolean
    ): String {
        return "library::$baseUrl::$userId::$parentId::$startIndex::$limit::${mode.name}::${filterValue.orEmpty()}::${sortField.name}::$sortDescending"
    }

    private fun buildLibraryItemsUrl(
        baseUrl: String,
        userId: String,
        parentId: String,
        startIndex: Int,
        limit: Int,
        mode: LibraryBrowseMode,
        filterValue: String?,
        sortField: LibrarySortField,
        sortDescending: Boolean
    ): String {
        val builder = StringBuilder()
        when (mode) {
            LibraryBrowseMode.CONTINUE -> {
                builder.append("$baseUrl/emby/Users/$userId/Items/Resume?")
                builder.append("ParentId=$parentId&Limit=$limit&StartIndex=$startIndex")
                builder.append("&MediaTypes=Video&Recursive=true")
            }

            else -> {
                builder.append("$baseUrl/emby/Users/$userId/Items?")
                builder.append("ParentId=$parentId&StartIndex=$startIndex&Limit=$limit")
                when (mode) {
                    LibraryBrowseMode.COLLECTIONS -> {
                        builder.append("&Recursive=false&IncludeItemTypes=BoxSet")
                    }

                    LibraryBrowseMode.FOLDERS -> {
                        builder.append("&Recursive=false&Filters=IsFolder")
                    }

                    else -> {
                        builder.append("&Recursive=true&IncludeItemTypes=Movie,Episode,Video,Series,MusicVideo,BoxSet")
                    }
                }

                when (mode) {
                    LibraryBrowseMode.FAVORITES -> builder.append("&Filters=IsFavorite")
                    LibraryBrowseMode.GENRES -> if (!filterValue.isNullOrBlank()) builder.append("&Genres=${encodeQueryValue(filterValue)}")
                    LibraryBrowseMode.TAGS -> if (!filterValue.isNullOrBlank()) builder.append("&Tags=${encodeQueryValue(filterValue)}")
                    else -> Unit
                }
            }
        }

        builder.append("&Fields=PrimaryImageAspectRatio,Overview,People,ImageTags,PrimaryImageAspectRatio,ChildCount")
        builder.append("&EnableImageTypes=Primary,Thumb,Backdrop&ImageTypeLimit=1")
        builder.append("&SortBy=${sortField.apiValue}")
        builder.append("&SortOrder=${if (sortDescending) "Descending" else "Ascending"}")
        return builder.toString()
    }

    private fun buildSearchItemsUrl(
        baseUrl: String,
        userId: String,
        query: String,
        startIndex: Int,
        limit: Int
    ): String {
        return buildString {
            append("$baseUrl/emby/Users/$userId/Items?")
            append("Recursive=true")
            append("&SearchTerm=${encodeQueryValue(query)}")
            append("&StartIndex=$startIndex")
            append("&Limit=$limit")
            append("&IncludeItemTypes=Movie,Episode,Video,Series,MusicVideo,BoxSet,Folder")
            append("&Fields=PrimaryImageAspectRatio,Overview,People,ImageTags,PrimaryImageAspectRatio,ChildCount")
            append("&EnableImageTypes=Primary,Thumb,Backdrop&ImageTypeLimit=1")
            append("&SortBy=SortName")
            append("&SortOrder=Ascending")
        }
    }

    private fun buildPlaybackHistoryUrl(
        baseUrl: String,
        userId: String,
        startIndex: Int,
        limit: Int,
        category: PlaybackHistoryCategory
    ): String {
        return buildString {
            append("$baseUrl/emby/Users/$userId/Items?")
            append("Recursive=true")
            append("&StartIndex=$startIndex")
            append("&Limit=$limit")
            append("&IncludeItemTypes=${encodeQueryValue(category.includeItemTypes)}")
            append("&Fields=ImageTags,UserData,PrimaryImageAspectRatio,RunTimeTicks")
            append("&EnableImageTypes=Primary,Thumb,Backdrop&ImageTypeLimit=1")
            append("&EnableUserData=true")
            append("&SortBy=DatePlayed")
            append("&SortOrder=Descending")
        }
    }

    private fun buildFavoriteItemsUrl(
        baseUrl: String,
        userId: String,
        startIndex: Int,
        limit: Int
    ): String {
        return buildString {
            append("$baseUrl/emby/Users/$userId/Items?")
            append("Recursive=true")
            append("&StartIndex=$startIndex")
            append("&Limit=$limit")
            append("&Filters=IsFavorite")
            append("&IncludeItemTypes=Movie,Episode,Video,MusicVideo")
            append("&Fields=ImageTags,UserData,PrimaryImageAspectRatio,RunTimeTicks")
            append("&EnableImageTypes=Primary,Thumb,Backdrop&ImageTypeLimit=1")
            append("&EnableUserData=true")
            append("&SortBy=SortName")
            append("&SortOrder=Ascending")
        }
    }

    private fun resolveHistoryLibraryName(
        baseUrl: String,
        accessToken: String,
        itemId: String,
        libraryTitleMap: Map<String, String>,
        libraryNameCache: MutableMap<String, String>
    ): String {
        libraryNameCache[itemId]?.let { return it }
        if (itemId.isBlank()) return context.getString(R.string.media_library)

        return runCatching {
            val request = Request.Builder()
                .url("$baseUrl/emby/Items/$itemId/Ancestors")
                .header("X-Emby-Token", accessToken)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("读取媒体库祖先信息失败：${response.code}")
                }

                val raw = response.body?.string().orEmpty()
                val ancestors = if (raw.trim().startsWith("[")) JSONArray(raw) else {
                    JSONObject(raw).optJSONArray("Items") ?: JSONArray()
                }

                val parsedAncestors = buildList {
                    for (index in 0 until ancestors.length()) {
                        val ancestor = ancestors.optJSONObject(index) ?: continue
                        val ancestorId = ancestor.optString("Id")
                        val ancestorName = ancestor.optString("Name")
                        val ancestorType = ancestor.optString("Type")
                        if (ancestorId.isNotBlank() && ancestorName.isNotBlank()) {
                            add(Triple(ancestorId, ancestorName, ancestorType))
                        }
                    }
                }

                val matchedName = parsedAncestors.firstNotNullOfOrNull { (ancestorId, ancestorName, _) ->
                    libraryTitleMap[ancestorId] ?: ancestorName.takeIf {
                        ancestorId in libraryTitleMap.keys
                    }
                }

                matchedName
                    ?: parsedAncestors.firstOrNull { (_, ancestorName, ancestorType) ->
                        ancestorType in HISTORY_LIBRARY_ANCESTOR_TYPES && !isSystemAncestorName(ancestorName)
                    }?.second
                    ?: parsedAncestors.firstOrNull { (_, ancestorName, _) ->
                        !isSystemAncestorName(ancestorName)
                    }?.second
                    ?: context.getString(R.string.media_library)
            }
        }.getOrDefault(context.getString(R.string.media_library)).also {
            libraryNameCache[itemId] = it
        }
    }

    private fun isSystemAncestorName(name: String): Boolean {
        val normalized = name.trim().lowercase(Locale.getDefault())
        return normalized.isBlank() || normalized == "root"
    }

    private fun formatPlaybackHistoryTime(lastPlayedDate: String): String {
        val playedAt = parseUtcDate(lastPlayedDate)
        if (playedAt <= 0L) return ""

        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply { timeInMillis = playedAt }
        val timeLabel = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(playedAt))

        return when {
            isSameDay(now, target) -> context.getString(R.string.playback_history_time_today, timeLabel)
            isYesterday(now, target) -> context.getString(R.string.playback_history_time_yesterday, timeLabel)
            else -> SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(playedAt))
        }
    }

    private fun parseUtcDate(value: String): Long {
        if (value.isBlank()) return 0L
        val normalized = value
            .replace(Regex("\\.(\\d{3})\\d*Z$"), ".$1Z")
            .replace(Regex("\\.(\\d{1,2})Z$")) { match ->
                "." + match.groupValues[1].padEnd(3, '0') + "Z"
            }
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'"
        )
        for (pattern in patterns) {
            val formatter = SimpleDateFormat(pattern, Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            runCatching {
                return formatter.parse(normalized)?.time ?: 0L
            }
        }
        return 0L
    }

    private fun isSameDay(first: Calendar, second: Calendar): Boolean {
        return first.get(Calendar.YEAR) == second.get(Calendar.YEAR) &&
            first.get(Calendar.DAY_OF_YEAR) == second.get(Calendar.DAY_OF_YEAR)
    }

    private fun isYesterday(today: Calendar, target: Calendar): Boolean {
        val copy = today.clone() as Calendar
        copy.add(Calendar.DAY_OF_YEAR, -1)
        return isSameDay(copy, target)
    }

    private fun buildServerHomeCache(home: ServerHomeUiModel): JSONObject {
        val sectionsJson = JSONObject()
        home.librarySections.forEach { (libraryId, items) ->
            sectionsJson.put(libraryId, items.toPosterJsonArray())
        }
        return JSONObject()
            .put("continueWatching", home.continueWatching.toPosterJsonArray())
            .put("mediaLibraries", home.mediaLibraries.toLibraryJsonArray())
            .put("librarySections", sectionsJson)
    }

    private fun parseServerHomeCache(json: JSONObject): ServerHomeUiModel {
        val sectionsJson = json.optJSONObject("librarySections") ?: JSONObject()
        val sectionMap = linkedMapOf<String, List<MediaPosterUiModel>>()
        val sectionKeys = sectionsJson.keys()
        while (sectionKeys.hasNext()) {
            val key = sectionKeys.next()
            sectionMap[key] = parsePosterItems(sectionsJson.optJSONArray(key))
        }
        return ServerHomeUiModel(
            continueWatching = parsePosterItems(json.optJSONArray("continueWatching")),
            mediaLibraries = parseLibraryItems(json.optJSONArray("mediaLibraries")),
            librarySections = sectionMap
        )
    }

    private fun buildLibraryItemsPageCache(page: LibraryItemsPageUiModel): JSONObject {
        return JSONObject()
            .put("totalCount", page.totalCount)
            .put("items", page.items.toPosterJsonArray())
    }

    private fun buildLibraryFilterOptionsCache(options: LibraryFilterOptionsUiModel): JSONObject {
        return JSONObject()
            .put("genres", JSONArray(options.genres))
            .put("tags", JSONArray(options.tags))
    }

    private fun parseLibraryItemsPageCache(json: JSONObject): LibraryItemsPageUiModel {
        return LibraryItemsPageUiModel(
            items = parsePosterItems(json.optJSONArray("items")),
            totalCount = json.optInt("totalCount", 0)
        )
    }

    private fun parseLibraryFilterOptions(json: JSONObject): LibraryFilterOptionsUiModel {
        return LibraryFilterOptionsUiModel(
            genres = parseSimpleStrings(json.optJSONArray("genres")),
            tags = parseSimpleStrings(json.optJSONArray("tags"))
        )
    }

    private fun parsePosterItems(items: JSONArray?): List<MediaPosterUiModel> {
        return buildList {
            for (index in 0 until (items?.length() ?: 0)) {
                val item = items?.optJSONObject(index) ?: continue
                add(
                    MediaPosterUiModel(
                        id = item.optString("id"),
                        title = item.optString("title"),
                        subtitle = item.optString("subtitle"),
                        style = ServerIconStyle.valueOf(
                            item.optString("style", ServerIconStyle.INDIGO.name)
                        ),
                        imageUrl = item.optString("imageUrl").ifBlank { null },
                        isFolder = item.optBoolean("isFolder"),
                        itemType = item.optString("itemType")
                    )
                )
            }
        }
    }

    private fun parseSimpleNames(items: JSONArray?): List<String> {
        return buildList {
            for (index in 0 until (items?.length() ?: 0)) {
                val item = items?.optJSONObject(index) ?: continue
                val name = item.optString("Name")
                if (name.isNotBlank()) add(name)
            }
        }
    }

    private fun parseSimpleStrings(items: JSONArray?): List<String> {
        return buildList {
            for (index in 0 until (items?.length() ?: 0)) {
                val value = items?.optString(index).orEmpty()
                if (value.isNotBlank()) add(value)
            }
        }
    }

    private fun encodeQueryValue(value: String): String {
        return java.net.URLEncoder.encode(value, Charsets.UTF_8.name())
    }

    private fun parseLibraryItems(items: JSONArray?): List<MediaLibraryUiModel> {
        return buildList {
            for (index in 0 until (items?.length() ?: 0)) {
                val item = items?.optJSONObject(index) ?: continue
                add(
                    MediaLibraryUiModel(
                        id = item.optString("id"),
                        title = item.optString("title"),
                        style = ServerIconStyle.valueOf(
                            item.optString("style", ServerIconStyle.INDIGO.name)
                        ),
                        imageUrl = item.optString("imageUrl").ifBlank { null },
                        totalCount = item.optInt("totalCount", 0)
                    )
                )
            }
        }
    }

    private fun buildAuthorizationHeader(): String {
        val deviceId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ).orEmpty()

        return "Emby Client=\"EmbyPro\", Device=\"Android\", DeviceId=\"$deviceId\", Version=\"1.0\""
    }

    private fun List<MediaPosterUiModel>.toPosterJsonArray(): JSONArray {
        return JSONArray().also { array ->
            forEach { item ->
                array.put(
                    JSONObject()
                        .put("id", item.id)
                        .put("title", item.title)
                        .put("subtitle", item.subtitle)
                        .put("style", item.style.name)
                        .put("imageUrl", item.imageUrl)
                        .put("isFolder", item.isFolder)
                        .put("itemType", item.itemType)
                )
            }
        }
    }

    private fun List<MediaLibraryUiModel>.toLibraryJsonArray(): JSONArray {
        return JSONArray().also { array ->
            forEach { item ->
                array.put(
                    JSONObject()
                        .put("id", item.id)
                        .put("title", item.title)
                        .put("style", item.style.name)
                        .put("imageUrl", item.imageUrl)
                        .put("totalCount", item.totalCount)
                )
            }
        }
    }

    companion object {
        private const val HOME_CACHE_MAX_AGE_MS = 15L * 60L * 1000L
        private const val LIBRARY_CACHE_MAX_AGE_MS = 15L * 60L * 1000L
        private const val FILTER_CACHE_MAX_AGE_MS = 30L * 60L * 1000L
        private val HISTORY_LIBRARY_ANCESTOR_TYPES = setOf("CollectionFolder", "UserView", "Channel")
    }
}
