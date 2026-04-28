package com.liujiaming.embypro

import android.content.Context
import android.provider.Settings
import android.util.Base64
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Data class representing basic server information.
 * Contains server ID, name, and version details.
 */
data class ServerInfo(
    val serverId: String,
    val serverName: String,
    val version: String
)

/**
 * Data class representing the result of a login operation.
 * Contains access token, user ID, and user name.
 */
data class LoginResult(
    val accessToken: String,
    val userId: String,
    val userName: String
)

/**
 * Data class representing a library section with its UI model and items.
 * Combines library metadata with a list of media posters.
 */
data class LibrarySectionUiModel(
    val library: MediaLibraryUiModel,
    val items: List<MediaPosterUiModel>
)

/**
 * Data class representing the home screen UI model.
 * Contains continue watching items, media libraries, and library sections.
 */
data class ServerHomeUiModel(
    val continueWatching: List<MediaPosterUiModel>,
    val mediaLibraries: List<MediaLibraryUiModel>,
    val librarySections: Map<String, List<MediaPosterUiModel>>
)

/**
 * Data class representing a paginated list of library items.
 * Contains the items and total count for pagination.
 */
data class LibraryItemsPageUiModel(
    val items: List<MediaPosterUiModel>,
    val totalCount: Int
)

/**
 * Data class representing filter options for a library.
 * Contains available genres and tags for filtering.
 */
data class LibraryFilterOptionsUiModel(
    val genres: List<String>,
    val tags: List<String>
)

/**
 * Data class representing a playback history item.
 * Contains item details, playback position, and library information.
 */
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

/**
 * Data class representing a paginated list of playback history items.
 * Contains the items and total count for pagination.
 */
data class PlaybackHistoryPageUiModel(
    val items: List<PlaybackHistoryItemUiModel>,
    val totalCount: Int
)

/**
 * Data class representing a paginated list of favorite items.
 * Contains the items and total count for pagination.
 */
data class FavoriteItemsPageUiModel(
    val items: List<PlaybackHistoryItemUiModel>,
    val totalCount: Int
)

/**
 * Data class representing music library statistics.
 * Contains counts for songs, albums, artists, and playlists.
 */
data class MusicLibraryStatsUiModel(
    val songsCount: Int,
    val albumsCount: Int,
    val artistsCount: Int,
    val playlistsCount: Int
)

/**
 * Enum representing different library browsing modes.
 * Used to filter and display library items in various ways.
 */
enum class LibraryBrowseMode {
    ALL,
    CONTINUE,
    FAVORITES,
    GENRES,
    TAGS,
    COLLECTIONS,
    FOLDERS
}

/**
 * Enum representing playback history categories.
 * Each category defines which item types to include and has a corresponding label resource.
 */
enum class PlaybackHistoryCategory(val includeItemTypes: String, val labelRes: Int) {
    ALL("Movie,Episode,Video,MusicVideo,Series,Season,BoxSet,Program,TvChannel", R.string.tab_all),
    VIDEO("Movie,Episode,Video,MusicVideo", R.string.playback_history_tab_video),
    AUDIO("Audio", R.string.playback_history_tab_audio),
    LIVE("Program,TvChannel", R.string.playback_history_tab_live),
    COLUMN("Series,Season,BoxSet", R.string.playback_history_tab_column)
}

/**
 * Enum representing library sort fields.
 * Each field defines the API value and has a corresponding label resource.
 */
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

/**
 * Data class representing a chapter in a media item.
 * Contains chapter title, start time label, start position, and optional image URL.
 */
data class ChapterUiModel(
    val title: String,
    val startLabel: String,
    val startPositionTicks: Long,
    val imageUrl: String?
)

/**
 * Data class representing a media information card.
 * Contains a title and list of information lines.
 */
data class MediaInfoCardUiModel(
    val title: String,
    val lines: List<String>
)

/**
 * Data class representing video detail UI model.
 * Contains comprehensive information about a video item including metadata,
 * media sources, chapters, and playback information.
 */
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
    val mediaSourceId: String,
    val playbackUrl: String?,
    val playSessionId: String
)

/**
 * Service class for interacting with the Emby API.
 * Provides methods for authentication, media retrieval, playback, and user management.
 * Implements caching strategies to improve performance and offline support.
 */
class EmbyApiService(
    private val context: Context
) {
    private val client = NetworkClientProvider.client
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val localMediaCache = LocalMediaCache(context.applicationContext)

    /**
     * Builds a normalized base URL from address and port.
     * Adds HTTPS scheme if not present and properly formats the port.
     */
    fun buildBaseUrl(address: String, port: String): String {
        val trimmedAddress = address.trim().removeSuffix("/")
        val hasScheme = trimmedAddress.contains(Regex("^https?://", RegexOption.IGNORE_CASE))
        val normalizedAddress = if (hasScheme) {
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

    /**
     * Parses a base URL into address and port components.
     * Returns a pair of (address, port), with port being empty if using default.
     */
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

    fun fetchMusicLibraries(baseUrl: String, userId: String, accessToken: String): Result<List<MediaLibraryUiModel>> {
        return runCatching {
            val cachedLibraries = fetchMediaLibraries(baseUrl, userId, accessToken).getOrThrow()
            val cachedMusicLibraries = cachedLibraries.filter { it.collectionType.equals("music", ignoreCase = true) }
            if (cachedMusicLibraries.isNotEmpty() || cachedLibraries.any { it.collectionType.isNotBlank() }) {
                return@runCatching cachedMusicLibraries
            }

            val freshLibraries = fetchViews(baseUrl, userId, accessToken)
            localMediaCache.writeJson("views::$baseUrl::$userId", freshLibraries.toLibraryJsonArray().toString())
            freshLibraries.filter { it.collectionType.equals("music", ignoreCase = true) }
        }
    }

    fun fetchMusicLibraryStats(
        baseUrl: String,
        userId: String,
        accessToken: String,
        libraryId: String
    ): Result<MusicLibraryStatsUiModel> {
        return runCatching {
            if (libraryId.isBlank()) {
                throw IllegalArgumentException("音乐分区不存在")
            }

            val songsCount = fetchItemsTotalCount(
                url = buildMusicItemsCountUrl(
                    baseUrl = baseUrl,
                    userId = userId,
                    libraryId = libraryId,
                    includeItemTypes = "Audio",
                    recursive = true
                ),
                accessToken = accessToken,
                errorMessage = "读取歌曲数量失败"
            )

            val albumsCount = fetchItemsTotalCount(
                url = buildMusicItemsCountUrl(
                    baseUrl = baseUrl,
                    userId = userId,
                    libraryId = libraryId,
                    includeItemTypes = "MusicAlbum",
                    recursive = true
                ),
                accessToken = accessToken,
                errorMessage = "读取专辑数量失败"
            )

            val artistsCount = fetchItemsTotalCount(
                url = buildMusicItemsCountUrl(
                    baseUrl = baseUrl,
                    userId = userId,
                    libraryId = libraryId,
                    includeItemTypes = "MusicArtist",
                    recursive = true
                ),
                accessToken = accessToken,
                errorMessage = "读取歌手数量失败"
            )

            val libraryPlaylistCount = fetchItemsTotalCount(
                url = buildPlaylistCountUrl(
                    baseUrl = baseUrl,
                    userId = userId,
                    libraryId = libraryId,
                    includeParent = true
                ),
                accessToken = accessToken,
                errorMessage = "读取歌单数量失败"
            )
            val playlistsCount = if (libraryPlaylistCount > 0) {
                libraryPlaylistCount
            } else {
                fetchItemsTotalCount(
                    url = buildPlaylistCountUrl(
                        baseUrl = baseUrl,
                        userId = userId,
                        libraryId = libraryId,
                        includeParent = false
                    ),
                    accessToken = accessToken,
                    errorMessage = "读取歌单数量失败"
                )
            }

            MusicLibraryStatsUiModel(
                songsCount = songsCount,
                albumsCount = albumsCount,
                artistsCount = artistsCount,
                playlistsCount = playlistsCount
            )
        }
    }

    fun fetchMusicBrowsePage(
        baseUrl: String,
        userId: String,
        accessToken: String,
        libraryId: String,
        browseType: MusicBrowseType,
        containerId: String? = null,
        containerTitle: String? = null
    ): Result<MusicListPageUiModel> {
        return runCatching {
            when (browseType) {
                MusicBrowseType.SONGS -> {
                    val parentId = containerId?.takeIf { it.isNotBlank() } ?: libraryId
                    fetchMusicSongsPage(
                        baseUrl = baseUrl,
                        userId = userId,
                        accessToken = accessToken,
                        libraryId = libraryId,
                        parentId = parentId,
                        pageTitle = containerTitle ?: context.getString(R.string.music_library_entry_songs),
                        pageSubtitle = containerTitle.orEmpty()
                    )
                }

                MusicBrowseType.ALBUMS -> {
                    if (!containerId.isNullOrBlank()) {
                        fetchMusicSongsPage(
                            baseUrl = baseUrl,
                            userId = userId,
                            accessToken = accessToken,
                            libraryId = libraryId,
                            parentId = containerId,
                            pageTitle = containerTitle ?: context.getString(R.string.music_library_entry_albums),
                            pageSubtitle = context.getString(R.string.music_list_album_tracks)
                        )
                    } else {
                        fetchMusicAlbumsPage(baseUrl, userId, accessToken, libraryId)
                    }
                }

                MusicBrowseType.ARTISTS -> {
                    if (!containerId.isNullOrBlank()) {
                        fetchMusicArtistSongsPage(
                            baseUrl = baseUrl,
                            userId = userId,
                            accessToken = accessToken,
                            libraryId = libraryId,
                            artistId = containerId,
                            artistName = containerTitle.orEmpty()
                        )
                    } else {
                        fetchMusicArtistsPage(baseUrl, userId, accessToken, libraryId)
                    }
                }

                MusicBrowseType.PLAYLISTS -> {
                    if (!containerId.isNullOrBlank()) {
                        fetchMusicPlaylistSongsPage(
                            baseUrl = baseUrl,
                            userId = userId,
                            accessToken = accessToken,
                            libraryId = libraryId,
                            playlistId = containerId,
                            playlistName = containerTitle.orEmpty()
                        )
                    } else {
                        fetchMusicPlaylistsPage(baseUrl, userId, accessToken, libraryId)
                    }
                }

                MusicBrowseType.FAVORITES -> {
                    fetchMusicSongsPage(
                        baseUrl = baseUrl,
                        userId = userId,
                        accessToken = accessToken,
                        libraryId = libraryId,
                        parentId = libraryId,
                        pageTitle = context.getString(R.string.music_library_entry_favorites),
                        pageSubtitle = context.getString(R.string.music_list_favorites_subtitle),
                        favoriteOnly = true
                    )
                }

                MusicBrowseType.FOLDERS -> {
                    if (!containerId.isNullOrBlank()) {
                        fetchMusicSongsPage(
                            baseUrl = baseUrl,
                            userId = userId,
                            accessToken = accessToken,
                            libraryId = libraryId,
                            parentId = containerId,
                            pageTitle = containerTitle ?: context.getString(R.string.music_library_entry_folders),
                            pageSubtitle = context.getString(R.string.music_list_folder_tracks)
                        )
                    } else {
                        fetchMusicFoldersPage(baseUrl, userId, accessToken, libraryId)
                    }
                }

                MusicBrowseType.LOCAL -> {
                    throw IllegalArgumentException("Local music page should be loaded from offline cache")
                }
            }
        }
    }

    fun fetchAudioPlayback(
        baseUrl: String,
        userId: String,
        accessToken: String,
        itemId: String
    ): Result<MusicPlaybackUiModel> {
        return runCatching {
            val itemRequest = Request.Builder()
                .url(
                    "$baseUrl/emby/Users/$userId/Items/$itemId" +
                        "?Fields=ImageTags,PrimaryImageTag,PrimaryImageItemId,AlbumPrimaryImageTag,AlbumId,Artists,Album,UserData"
                )
                .header("X-Emby-Token", accessToken)
                .get()
                .build()

            val itemJson = client.newCall(itemRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("读取音频详情失败：${response.code}")
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
                    throw IllegalStateException("读取音频播放信息失败：${response.code}")
                }
                JSONObject(response.body?.string().orEmpty())
            }

            val mediaSource = playbackJson.optJSONArray("MediaSources")?.optJSONObject(0) ?: JSONObject()
            val mediaSourceId = mediaSource.optString("Id")
            val playSessionId = playbackJson.optString("PlaySessionId")
            val imageInfo = resolveImageInfo(itemJson)
            val artists = parseSimpleStrings(itemJson.optJSONArray("Artists")).joinToString(" / ")
            val album = itemJson.optString("Album")
            val subtitle = listOfNotNull(
                artists.takeIf { it.isNotBlank() },
                album.takeIf { it.isNotBlank() }
            ).joinToString(" • ")

            MusicPlaybackUiModel(
                itemId = itemId,
                title = itemJson.optString("Name", context.getString(R.string.untitled_media)),
                subtitle = subtitle,
                coverImageUrl = buildImageUrl(
                    baseUrl = baseUrl,
                    imageItemId = imageInfo.first,
                    imageType = imageInfo.second,
                    imageTag = imageInfo.third,
                    maxWidth = 720,
                    maxHeight = 720
                ),
                playbackUrl = buildAudioPlaybackUrl(
                    baseUrl = baseUrl,
                    userId = userId,
                    accessToken = accessToken,
                    itemId = itemId,
                    mediaSourceId = mediaSourceId,
                    playSessionId = playSessionId
                ),
                playbackPositionMs = (itemJson.optJSONObject("UserData")?.optLong("PlaybackPositionTicks")
                    ?: 0L) / 10_000L,
                isFavorite = itemJson.optJSONObject("UserData")?.optBoolean("IsFavorite") == true,
                runtimeMs = itemJson.optLong("RunTimeTicks") / 10_000L
            )
        }
    }

    fun fetchLyrics(
        baseUrl: String,
        userId: String,
        accessToken: String,
        itemId: String
    ): Result<LyricsUiModel> {
        // Strategy 1: Try dedicated lyrics endpoints (Jellyfin format first, then Emby variant)
        val lyricsEndpoints = listOf(
            "$baseUrl/emby/Audio/$itemId/Lyrics",
            "$baseUrl/emby/Items/$itemId/Lyrics"
        )

        for (endpoint in lyricsEndpoints) {
            val result = fetchLyricsFromEndpoint(endpoint, accessToken)
            if (result.isSuccess && result.getOrNull()?.lines?.isNotEmpty() == true) {
                return result
            }
        }

        // Strategy 2: Fetch the audio item metadata and try to extract lyrics from it.
        // Emby may include a "Lyrics" field or lyrics data within the item response.
        val itemResult = runCatching {
            val itemRequest = Request.Builder()
                .url("$baseUrl/emby/Users/$userId/Items/$itemId?Fields=MediaSources,MediaStreams")
                .header("X-Emby-Token", accessToken)
                .get()
                .build()

            client.newCall(itemRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("获取音频详情失败：${response.code}")
                }
                val body = response.body?.string().orEmpty()
                val parsed = parseAnyLyricsFormat(body)
                if (parsed.isNotEmpty()) {
                    return@runCatching LyricsUiModel(lines = parsed)
                }

                // Also check MediaStreams for embedded lyrics
                val json = try { JSONObject(body) } catch (_: Exception) { null }
                if (json != null) {
                    val mediaStreams = json.optJSONArray("MediaStreams")
                    if (mediaStreams != null) {
                        for (i in 0 until mediaStreams.length()) {
                            val stream = mediaStreams.optJSONObject(i) ?: continue
                            val codec = stream.optString("Codec", "")
                            // Some audio files have embedded lyrics as a separate stream
                            if (codec.equals("lyric", ignoreCase = true) ||
                                stream.optString("Type", "").equals("Lyric", ignoreCase = true)) {
                                // Return a non-empty model to indicate lyrics exist but need different fetching
                                return@runCatching LyricsUiModel(
                                    lines = listOf(
                                        LyricLineUiModel(
                                            text = context.getString(R.string.music_player_lyrics_embedded),
                                            startMs = 0L
                                        )
                                    )
                                )
                            }
                        }
                    }

                    // Check for Lyrics field directly on the item
                    val lyricsField = json.optString("Lyrics", "").ifBlank {
                        json.optString("lyrics", "")
                    }
                    if (lyricsField.isNotBlank()) {
                        val parsed = parseLrcText(lyricsField)
                        if (parsed.isNotEmpty()) {
                            return@runCatching LyricsUiModel(lines = parsed)
                        }
                    }
                }

                LyricsUiModel(lines = emptyList())
            }
        }

        if (itemResult.isSuccess && itemResult.getOrNull()?.lines?.isNotEmpty() == true) {
            return itemResult
        }

        // All strategies exhausted – return empty (no lyrics available)
        if (itemResult.isFailure) {
            return Result.failure(itemResult.exceptionOrNull()!!)
        }
        return Result.success(LyricsUiModel(lines = emptyList()))
    }

    private fun fetchLyricsFromEndpoint(endpoint: String, accessToken: String): Result<LyricsUiModel> {
        return runCatching {
            val request = Request.Builder()
                .url(endpoint)
                .header("X-Emby-Token", accessToken)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("读取歌词失败($endpoint)：${response.code}")
                }
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) return@runCatching LyricsUiModel(lines = emptyList())
                val parsed = parseAnyLyricsFormat(body)
                LyricsUiModel(lines = parsed)
            }
        }
    }

    /**
     * Try to parse lyrics from a response body, attempting multiple formats:
     * JSON array, JSON string field, and raw LRC text.
     */
    private fun parseAnyLyricsFormat(body: String): List<LyricLineUiModel> {
        val trimmedBody = body.trim()
        if (trimmedBody.isBlank()) return emptyList()

        runCatching {
            val parsedLines = parseLyricsJsonObject(JSONObject(trimmedBody))
            if (parsedLines.isNotEmpty()) return parsedLines
        }

        runCatching {
            val parsedLines = parseLyricsJsonArray(JSONArray(trimmedBody))
            if (parsedLines.isNotEmpty()) return parsedLines
        }

        val normalizedBody = normalizeLyricsPayload(trimmedBody)
        if (normalizedBody != trimmedBody) {
            runCatching {
                val parsedLines = parseLyricsJsonObject(JSONObject(normalizedBody))
                if (parsedLines.isNotEmpty()) return parsedLines
            }

            runCatching {
                val parsedLines = parseLyricsJsonArray(JSONArray(normalizedBody))
                if (parsedLines.isNotEmpty()) return parsedLines
            }
        }

        return parseLrcText(normalizedBody)
    }

    private fun parseLyricsJsonObject(json: JSONObject): List<LyricLineUiModel> {
        val lyricsArray = json.optJSONArray("Lyrics")
            ?: json.optJSONArray("lyrics")
            ?: json.optJSONArray("Lines")
            ?: json.optJSONArray("lines")
            ?: json.optJSONArray("LyricList")
            ?: json.optJSONArray("Items")

        if (lyricsArray != null) {
            val parsedLines = parseLyricsJsonArray(lyricsArray)
            if (parsedLines.isNotEmpty()) return parsedLines
        }

        val rawCandidates = listOf(
            json.optString("Lyrics", ""),
            json.optString("lyrics", ""),
            json.optString("Text", ""),
            json.optString("text", ""),
            json.optString("Value", ""),
            json.optString("value", "")
        )
        rawCandidates.forEach { candidate ->
            if (candidate.isBlank()) return@forEach
            val parsedLines = parseAnyLyricsFormat(candidate)
            if (parsedLines.isNotEmpty()) return parsedLines
        }

        val wrapped = json.optJSONObject("Response")
            ?: json.optJSONObject("Result")
            ?: json.optJSONObject("data")
        if (wrapped != null) {
            val parsedWrapped = parseLyricsJsonObject(wrapped)
            if (parsedWrapped.isNotEmpty()) return parsedWrapped
        }

        return emptyList()
    }

    private fun parseLyricsJsonArray(array: JSONArray): List<LyricLineUiModel> {
        val parsedLines = mutableListOf<LyricLineUiModel>()
        for (index in 0 until array.length()) {
            val value = array.opt(index)
            when (value) {
                is JSONObject -> parsedLines.addAll(parseLyricsJsonEntry(value))
                is String -> parsedLines.addAll(parseAnyLyricsFormat(value))
            }
        }
        return parsedLines.sortedBy { it.startMs }
    }

    private fun parseLyricsJsonEntry(lineJson: JSONObject): List<LyricLineUiModel> {
        val rawText = lineJson.optString("Text", "").ifBlank {
            lineJson.optString("text", "")
        }.ifBlank {
            lineJson.optString("Line", "")
        }.ifBlank {
            lineJson.optString("line", "")
        }.ifBlank {
            lineJson.optString("Value", "")
        }.ifBlank {
            lineJson.optString("value", "")
        }
        val normalizedText = normalizeLyricsPayload(rawText)
        if (normalizedText.isNotBlank()) {
            val expandedLines = parseLrcText(normalizedText)
            if (expandedLines.size > 1) {
                return expandedLines
            }
        }

        val startTicks = lineJson.optLong(
            "Start",
            lineJson.optLong(
                "start",
                lineJson.optLong(
                    "StartTicks",
                    lineJson.optLong("startTicks", 0L)
                )
            )
        )
        val startMsFromField = startTicks / 10_000L
        val startMsFromText = extractFirstTimestampMs(normalizedText)
        val finalStartMs = if (startMsFromField > 0L) startMsFromField else startMsFromText
        val cleanedText = cleanLyricDisplayText(normalizedText)
        if (cleanedText.isBlank()) return emptyList()
        return listOf(LyricLineUiModel(text = cleanedText, startMs = finalStartMs))
    }

    /**
     * Parses LRC format lyrics text.
     * Format: [mm:ss.xx]Lyric text or [mm:ss]Lyric text
     */
    private fun parseLrcText(body: String): List<LyricLineUiModel> {
        val normalizedBody = normalizeLyricsPayload(body)
        val lrcTimestampRegex = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{2,3}))?\]""")
        val lrcMetaRegex = Regex("""^\[(ti|ar|al|by|offset|length|re|ve|la|id):.*]$""", RegexOption.IGNORE_CASE)
        val lines = mutableListOf<LyricLineUiModel>()

        for (rawLine in normalizedBody.lines()) {
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty() || lrcMetaRegex.matches(trimmed)) continue

            val matches = lrcTimestampRegex.findAll(trimmed).toList()
            if (matches.isEmpty()) continue

            val text = cleanLyricDisplayText(trimmed)
            if (text.isEmpty()) continue

            matches.forEach { match ->
                val startMs = timestampMatchToMs(match)
                lines.add(LyricLineUiModel(text = text, startMs = startMs))
            }
        }

        return lines.sortedBy { it.startMs }
    }

    private fun normalizeLyricsPayload(raw: String): String {
        var text = raw.trim().removePrefix("\uFEFF")
        if ((text.startsWith("\"") && text.endsWith("\"")) ||
            (text.startsWith("'") && text.endsWith("'"))) {
            text = text.substring(1, text.length - 1)
        }
        return text
            .replace("\\r\\n", "\n")
            .replace("\\n", "\n")
            .replace("\\r", "\n")
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .replace("\\t", " ")
            .replace("\\\"", "\"")
            .replace("\\/", "/")
            .trim()
    }

    private fun cleanLyricDisplayText(raw: String): String {
        return normalizeLyricsPayload(raw)
            .replace(Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{2,3}))?\]"""), "")
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(" ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun extractFirstTimestampMs(raw: String): Long {
        val match = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{2,3}))?\]""").find(raw) ?: return 0L
        return timestampMatchToMs(match)
    }

    private fun timestampMatchToMs(match: MatchResult): Long {
        val minutes = match.groupValues[1].toLongOrNull() ?: return 0L
        val seconds = match.groupValues[2].toLongOrNull() ?: return 0L
        val fraction = match.groupValues[3]
        val fractionValue = fraction.toLongOrNull() ?: 0L
        val fractionMs = when (fraction.length) {
            0 -> 0L
            1 -> fractionValue * 100L
            2 -> fractionValue * 10L
            else -> fractionValue
        }
        return (minutes * 60_000L) + (seconds * 1_000L) + fractionMs
    }

    fun searchMusicItems(
        baseUrl: String,
        userId: String,
        accessToken: String,
        libraryId: String,
        query: String
    ): Result<MusicListPageUiModel> {
        return runCatching {
            val request = Request.Builder()
                .url(buildMusicSearchUrl(baseUrl, userId, libraryId, query))
                .header("X-Emby-Token", accessToken)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("搜索音乐失败：${response.code}")
                }

                val json = JSONObject(response.body?.string().orEmpty())
                val items = buildList {
                    val rawItems = json.optJSONArray("Items") ?: return@buildList
                    
                    for (index in 0 until rawItems.length()) {
                        val item = rawItems.optJSONObject(index) ?: continue
                        val itemType = item.optString("Type")
                        
                        when (itemType) {
                            "Audio" -> {
                                val songEntries = buildMusicSongEntries(baseUrl, JSONArray().put(item))
                                addAll(songEntries)
                            }
                            "MusicAlbum" -> {
                                val albumEntries = buildMusicContainerEntries(
                                    baseUrl,
                                    JSONArray().put(item),
                                    MusicBrowseType.ALBUMS
                                )
                                addAll(albumEntries)
                            }
                            "MusicArtist" -> {
                                val artistEntries = buildMusicArtistEntries(baseUrl, JSONArray().put(item))
                                addAll(artistEntries)
                            }
                            "Playlist" -> {
                                val playlistEntries = buildMusicContainerEntries(
                                    baseUrl,
                                    JSONArray().put(item),
                                    MusicBrowseType.PLAYLISTS
                                )
                                addAll(playlistEntries)
                            }
                        }
                    }
                }

                MusicListPageUiModel(
                    title = context.getString(R.string.music_search_hint),
                    subtitle = if (items.isEmpty()) {
                        context.getString(R.string.music_search_no_results)
                    } else {
                        context.getString(R.string.library_total_count, items.size)
                    },
                    items = items,
                    totalCount = items.size,
                    isSongList = false,
                    libraryId = libraryId
                )
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

    fun reportVideoPlaybackStarted(
        baseUrl: String,
        accessToken: String,
        itemId: String,
        mediaSourceId: String,
        playSessionId: String,
        playbackPositionMs: Long,
        isPaused: Boolean
    ): Result<Unit> = reportVideoPlaybackSession(
        baseUrl = baseUrl,
        accessToken = accessToken,
        endpoint = "Playing",
        itemId = itemId,
        mediaSourceId = mediaSourceId,
        playSessionId = playSessionId,
        playbackPositionMs = playbackPositionMs,
        isPaused = isPaused
    )

    fun reportVideoPlaybackProgress(
        baseUrl: String,
        accessToken: String,
        itemId: String,
        mediaSourceId: String,
        playSessionId: String,
        playbackPositionMs: Long,
        isPaused: Boolean
    ): Result<Unit> = reportVideoPlaybackSession(
        baseUrl = baseUrl,
        accessToken = accessToken,
        endpoint = "Playing/Progress",
        itemId = itemId,
        mediaSourceId = mediaSourceId,
        playSessionId = playSessionId,
        playbackPositionMs = playbackPositionMs,
        isPaused = isPaused
    )

    fun reportVideoPlaybackStopped(
        baseUrl: String,
        accessToken: String,
        itemId: String,
        mediaSourceId: String,
        playSessionId: String,
        playbackPositionMs: Long
    ): Result<Unit> = reportVideoPlaybackSession(
        baseUrl = baseUrl,
        accessToken = accessToken,
        endpoint = "Playing/Stopped",
        itemId = itemId,
        mediaSourceId = mediaSourceId,
        playSessionId = playSessionId,
        playbackPositionMs = playbackPositionMs,
        isPaused = true
    )

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
        sortDescending: Boolean = true,
        contentCategory: LibraryContentCategory = LibraryContentCategory.VIDEO
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
            sortDescending,
            contentCategory
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
                        sortDescending,
                        contentCategory
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
        category: PlaybackHistoryCategory = PlaybackHistoryCategory.ALL,
        parentId: String? = null
    ): Result<PlaybackHistoryPageUiModel> {
        return runCatching {
            val request = Request.Builder()
                .url(buildPlaybackHistoryUrl(baseUrl, userId, startIndex, limit, category, parentId))
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
                val items = buildPlaybackHistoryItems(
                    baseUrl = baseUrl,
                    items = json.optJSONArray("Items"),
                    libraryTitleMap = libraryTitleMap
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
                val items = buildFavoriteItems(
                    baseUrl = baseUrl,
                    items = json.optJSONArray("Items"),
                    libraryTitleMap = libraryTitleMap
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

    private fun fetchMusicSongsPage(
        baseUrl: String,
        userId: String,
        accessToken: String,
        libraryId: String,
        parentId: String,
        pageTitle: String,
        pageSubtitle: String,
        favoriteOnly: Boolean = false
    ): MusicListPageUiModel {
        val cacheKey = buildMusicSongsPageCacheKey(
            baseUrl = baseUrl,
            userId = userId,
            libraryId = libraryId,
            parentId = parentId,
            pageTitle = pageTitle,
            pageSubtitle = pageSubtitle,
            favoriteOnly = favoriteOnly
        )
        if (!favoriteOnly) {
            val cachedJson = localMediaCache.readJson(cacheKey, MUSIC_CACHE_MAX_AGE_MS)
            if (!cachedJson.isNullOrBlank()) {
                return parseMusicListPageCache(JSONObject(cachedJson))
            }
        } else {
            return fetchFavoriteMusicSongsPage(
                baseUrl = baseUrl,
                userId = userId,
                accessToken = accessToken,
                libraryId = libraryId,
                pageTitle = pageTitle,
                pageSubtitle = pageSubtitle
            )
        }

        try {
            val request = Request.Builder()
                .url(
                    buildMusicSongsUrl(
                        baseUrl = baseUrl,
                        userId = userId,
                        parentId = parentId,
                        startIndex = 0,
                        limit = MUSIC_ITEMS_PAGE_SIZE
                    )
                )
                .header("X-Emby-Token", accessToken)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("读取歌曲列表失败：${response.code}")
                }

                val json = JSONObject(response.body?.string().orEmpty())
                val items = buildMusicSongEntries(baseUrl, json.optJSONArray("Items"))

                return MusicListPageUiModel(
                    title = pageTitle,
                    subtitle = pageSubtitle,
                    items = items,
                    totalCount = json.optInt("TotalRecordCount", items.size),
                    isSongList = true,
                    libraryId = libraryId
                ).also { page ->
                    localMediaCache.writeJson(cacheKey, buildMusicListPageCache(page).toString())
                }
            }
        } catch (error: Throwable) {
            val staleJson = localMediaCache.readJsonAnyAge(cacheKey)
            if (!staleJson.isNullOrBlank()) {
                return parseMusicListPageCache(JSONObject(staleJson))
            }
            throw error
        }
    }

    private fun fetchFavoriteMusicSongsPage(
        baseUrl: String,
        userId: String,
        accessToken: String,
        libraryId: String,
        pageTitle: String,
        pageSubtitle: String
    ): MusicListPageUiModel {
        val allItems = mutableListOf<MusicListEntryUiModel>()
        var totalCount = 0
        var startIndex = 0

        do {
            val request = Request.Builder()
                .url(
                    buildMusicFavoritesUrl(
                        baseUrl = baseUrl,
                        userId = userId,
                        libraryId = libraryId,
                        startIndex = startIndex,
                        limit = MUSIC_ITEMS_PAGE_SIZE
                    )
                )
                .header("X-Emby-Token", accessToken)
                .get()
                .build()

            val pageItems = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("读取收藏歌曲失败：${response.code}")
                }

                val json = JSONObject(response.body?.string().orEmpty())
                totalCount = json.optInt("TotalRecordCount", allItems.size)
                buildMusicSongEntries(baseUrl, json.optJSONArray("Items"))
            }

            allItems.addAll(pageItems)
            startIndex += MUSIC_ITEMS_PAGE_SIZE
        } while (startIndex < totalCount)

        return MusicListPageUiModel(
            title = pageTitle,
            subtitle = pageSubtitle,
            items = allItems,
            totalCount = totalCount,
            isSongList = true,
            libraryId = libraryId
        )
    }

    private fun fetchMusicAlbumsPage(
        baseUrl: String,
        userId: String,
        accessToken: String,
        libraryId: String
    ): MusicListPageUiModel {
        val request = Request.Builder()
            .url(buildMusicAlbumsUrl(baseUrl, userId, libraryId))
            .header("X-Emby-Token", accessToken)
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("读取专辑列表失败：${response.code}")
            }

            val json = JSONObject(response.body?.string().orEmpty())
            val items = buildMusicContainerEntries(
                baseUrl = baseUrl,
                items = json.optJSONArray("Items"),
                browseType = MusicBrowseType.ALBUMS
            )
            return MusicListPageUiModel(
                title = context.getString(R.string.music_library_entry_albums),
                subtitle = context.getString(R.string.music_list_albums_subtitle),
                items = items,
                totalCount = json.optInt("TotalRecordCount", items.size),
                isSongList = false,
                libraryId = libraryId
            )
        }
    }

    private fun fetchMusicArtistsPage(
        baseUrl: String,
        userId: String,
        accessToken: String,
        libraryId: String
    ): MusicListPageUiModel {
        val request = Request.Builder()
            .url(buildMusicArtistsUrl(baseUrl, userId, libraryId))
            .header("X-Emby-Token", accessToken)
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("读取歌手列表失败：${response.code}")
            }

            val json = JSONObject(response.body?.string().orEmpty())
            val items = buildMusicArtistEntries(baseUrl, json.optJSONArray("Items"))
            return MusicListPageUiModel(
                title = context.getString(R.string.music_library_entry_artists_short),
                subtitle = context.getString(R.string.music_list_artists_subtitle),
                items = items,
                totalCount = json.optInt("TotalRecordCount", items.size),
                isSongList = false,
                libraryId = libraryId
            )
        }
    }

    private fun fetchMusicPlaylistsPage(
        baseUrl: String,
        userId: String,
        accessToken: String,
        libraryId: String
    ): MusicListPageUiModel {
        val request = Request.Builder()
            .url(buildMusicPlaylistsUrl(baseUrl, userId, libraryId))
            .header("X-Emby-Token", accessToken)
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("读取歌单列表失败：${response.code}")
            }

            val json = JSONObject(response.body?.string().orEmpty())
            val items = buildMusicContainerEntries(
                baseUrl = baseUrl,
                items = json.optJSONArray("Items"),
                browseType = MusicBrowseType.PLAYLISTS
            )
            return MusicListPageUiModel(
                title = context.getString(R.string.music_library_entry_playlists),
                subtitle = context.getString(R.string.music_list_playlists_subtitle),
                items = items,
                totalCount = json.optInt("TotalRecordCount", items.size),
                isSongList = false,
                libraryId = libraryId
            )
        }
    }

    private fun fetchMusicFoldersPage(
        baseUrl: String,
        userId: String,
        accessToken: String,
        libraryId: String
    ): MusicListPageUiModel {
        val request = Request.Builder()
            .url(buildMusicFoldersUrl(baseUrl, userId, libraryId))
            .header("X-Emby-Token", accessToken)
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("读取文件夹列表失败：${response.code}")
            }

            val json = JSONObject(response.body?.string().orEmpty())
            val items = buildMusicContainerEntries(
                baseUrl = baseUrl,
                items = json.optJSONArray("Items"),
                browseType = MusicBrowseType.FOLDERS
            )
            return MusicListPageUiModel(
                title = context.getString(R.string.music_library_entry_folders),
                subtitle = context.getString(R.string.music_list_folders_subtitle),
                items = items,
                totalCount = json.optInt("TotalRecordCount", items.size),
                isSongList = false,
                libraryId = libraryId
            )
        }
    }

    private fun fetchMusicArtistSongsPage(
        baseUrl: String,
        userId: String,
        accessToken: String,
        libraryId: String,
        artistId: String,
        artistName: String
    ): MusicListPageUiModel {
        val request = Request.Builder()
            .url(buildMusicArtistSongsUrl(baseUrl, userId, libraryId, artistId))
            .header("X-Emby-Token", accessToken)
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("读取歌手歌曲失败：${response.code}")
            }

            val json = JSONObject(response.body?.string().orEmpty())
            val items = buildMusicSongEntries(baseUrl, json.optJSONArray("Items"))
            return MusicListPageUiModel(
                title = artistName.ifBlank { context.getString(R.string.music_library_entry_artists_short) },
                subtitle = context.getString(R.string.music_list_artist_tracks),
                items = items,
                totalCount = json.optInt("TotalRecordCount", items.size),
                isSongList = true,
                libraryId = libraryId
            )
        }
    }

    private fun fetchMusicPlaylistSongsPage(
        baseUrl: String,
        userId: String,
        accessToken: String,
        libraryId: String,
        playlistId: String,
        playlistName: String
    ): MusicListPageUiModel {
        val request = Request.Builder()
            .url(buildMusicPlaylistItemsUrl(baseUrl, userId, playlistId))
            .header("X-Emby-Token", accessToken)
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("读取歌单歌曲失败：${response.code}")
            }

            val json = JSONObject(response.body?.string().orEmpty())
            val items = buildMusicSongEntries(baseUrl, json.optJSONArray("Items"))
            return MusicListPageUiModel(
                title = playlistName.ifBlank { context.getString(R.string.music_library_entry_playlists) },
                subtitle = context.getString(R.string.music_list_playlist_tracks),
                items = items,
                totalCount = json.optInt("TotalRecordCount", items.size),
                isSongList = true,
                libraryId = libraryId
            )
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
                            totalCount = item.optInt("ChildCount", 0),
                            collectionType = item.optString("CollectionType")
                        )
                    )
                }
            }
        }
    }

    private fun buildPlaybackHistoryItems(
        baseUrl: String,
        items: JSONArray?,
        libraryTitleMap: Map<String, String>
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
                        libraryName = resolveLibraryNameFromItem(item, libraryTitleMap),
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
        items: JSONArray?,
        libraryTitleMap: Map<String, String>
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
                        libraryName = resolveLibraryNameFromItem(item, libraryTitleMap),
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
                val artists = parseSimpleStrings(item.optJSONArray("Artists")).joinToString(" / ")
                val albumTitle = item.optString("Album")
                add(
                    MediaPosterUiModel(
                        id = item.optString("Id"),
                        title = item.optString("Name", context.getString(R.string.untitled_media)),
                        subtitle = if (item.optString("Type").equals("Audio", ignoreCase = true)) {
                            listOfNotNull(
                                artists.takeIf { it.isNotBlank() },
                                albumTitle.takeIf { it.isNotBlank() }
                            ).joinToString(" • ")
                        } else if (!playedPercentage.isNaN()) {
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
                        startPositionTicks = chapter.optLong("StartPositionTicks"),
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
        val mediaSourceId = firstMediaSource.optString("Id")
        val directStreamUrl = firstMediaSource.optString("DirectStreamUrl")
        val playSessionId = playbackInfo.optString("PlaySessionId")

        return VideoDetailUiModel(
            itemId = item.optString("Id"),
            title = item.optString("Name", context.getString(R.string.untitled_media)),
            overview = item.optString("Overview").ifBlank { item.optString("Tagline") },
            runtimeLabel = buildRuntimeLabel(item.optString("DateCreated")),
            versionLine = buildVersionLine(item, firstMediaSource),
            audioLine = buildAudioLine(mediaStreams),
            subtitleLine = buildSubtitleLine(firstMediaSource, studios, people),
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
            mediaSourceId = mediaSourceId,
            playbackUrl = buildPlaybackUrl(
                baseUrl = baseUrl,
                itemId = item.optString("Id"),
                mediaSourceId = mediaSourceId,
                directStreamUrl = directStreamUrl,
                playSessionId = playSessionId,
                staticBuild = true
            ),
            playSessionId = playSessionId
        )
    }

    private fun buildRuntimeLabel(dateCreated: String): String {
        return dateCreated.takeIf { it.isNotBlank() }?.let(::formatCreatedDate).orEmpty()
    }

    private fun buildVersionLine(item: JSONObject, mediaSource: JSONObject): String {
        val metadataParts = mutableListOf<String>()
        val container = mediaSource.optString("Container")
        if (container.isNotBlank()) metadataParts.add(container.uppercase())
        val size = mediaSource.optLong("Size")
        if (size > 0) metadataParts.add(formatBytes(size))
        val runtime = formatTicks(item.optLong("RunTimeTicks"))
        if (runtime.isNotBlank()) metadataParts.add(runtime)
        return metadataParts.joinToString("  ")
    }

    private fun formatCreatedDate(rawValue: String): String {
        val trimmed = rawValue.trim()
        if (trimmed.isBlank()) return rawValue

        val inputPatterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss"
        )
        inputPatterns.forEach { pattern ->
            runCatching {
                val parser = SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                    isLenient = false
                }
                val parsed = parser.parse(trimmed) ?: return@runCatching null
                SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(parsed)
            }.getOrNull()?.let { return it }
        }
        return trimmed
            .replace("T", " ")
            .replace("Z", "")
            .substringBefore(".")
            .replace(Regex(":(\\d{2})$"), "")
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
        playSessionId: String,
        staticBuild: Boolean
    ): String? {
        if (directStreamUrl.isNotBlank()) {
            val resolvedUrl = if (directStreamUrl.startsWith("http")) directStreamUrl else "$baseUrl$directStreamUrl"
            return appendQueryParameter(resolvedUrl, "PlaySessionId", playSessionId)
        }
        if (itemId.isBlank()) return null

        val url = buildString {
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
        return appendQueryParameter(url, "PlaySessionId", playSessionId)
    }

    private fun reportVideoPlaybackSession(
        baseUrl: String,
        accessToken: String,
        endpoint: String,
        itemId: String,
        mediaSourceId: String,
        playSessionId: String,
        playbackPositionMs: Long,
        isPaused: Boolean
    ): Result<Unit> {
        return runCatching {
            if (itemId.isBlank() || playSessionId.isBlank()) return@runCatching
            val ticks = playbackPositionMs.coerceAtLeast(0L) * 10_000L
            val requestBody = JSONObject()
                .put("ItemId", itemId)
                .put("MediaSourceId", mediaSourceId)
                .put("PlaySessionId", playSessionId)
                .put("PositionTicks", ticks)
                .put("PlaybackStartTimeTicks", System.currentTimeMillis() * 10_000L)
                .put("CanSeek", true)
                .put("IsPaused", isPaused)
                .put("PlayMethod", "DirectPlay")
                .toString()
                .toRequestBody(jsonMediaType)

            val request = Request.Builder()
                .url("$baseUrl/emby/Sessions/$endpoint")
                .header("X-Emby-Token", accessToken)
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("视频播放会话上报失败：${response.code}")
                }
            }
        }
    }

    fun fetchFavoriteAudioItemsPage(
        baseUrl: String,
        userId: String,
        accessToken: String,
        libraryId: String
    ): Result<MusicListPageUiModel> {
        return runCatching {
            fetchFavoriteMusicSongsPage(
                baseUrl = baseUrl,
                userId = userId,
                accessToken = accessToken,
                libraryId = libraryId,
                pageTitle = context.getString(R.string.music_library_entry_favorites),
                pageSubtitle = context.getString(R.string.music_list_favorites_subtitle)
            )
        }
    }

    private fun appendQueryParameter(url: String, key: String, value: String): String {
        if (url.isBlank() || value.isBlank()) return url
        if (url.contains("$key=")) return url
        val separator = if (url.contains("?")) "&" else "?"
        return url + separator + key + "=" + value
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
        sortDescending: Boolean,
        contentCategory: LibraryContentCategory
    ): String {
        return "library::$baseUrl::$userId::$parentId::$startIndex::$limit::${mode.name}::${filterValue.orEmpty()}::${sortField.name}::$sortDescending::${contentCategory.name}"
    }

    private fun buildMusicSongsPageCacheKey(
        baseUrl: String,
        userId: String,
        libraryId: String,
        parentId: String,
        pageTitle: String,
        pageSubtitle: String,
        favoriteOnly: Boolean
    ): String {
        return "music-songs::$baseUrl::$userId::$libraryId::$parentId::$pageTitle::$pageSubtitle::$favoriteOnly"
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
        sortDescending: Boolean,
        contentCategory: LibraryContentCategory
    ): String {
        val builder = StringBuilder()
        when (mode) {
            LibraryBrowseMode.CONTINUE -> {
                builder.append("$baseUrl/emby/Users/$userId/Items/Resume?")
                builder.append("ParentId=$parentId&Limit=$limit&StartIndex=$startIndex")
                builder.append(
                    if (contentCategory == LibraryContentCategory.AUDIO) {
                        "&MediaTypes=Audio&Recursive=true"
                    } else {
                        "&MediaTypes=Video&Recursive=true"
                    }
                )
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
                        if (contentCategory == LibraryContentCategory.AUDIO) {
                            builder.append("&Recursive=true&IncludeItemTypes=Audio&MediaTypes=Audio")
                        } else {
                            builder.append("&Recursive=true&IncludeItemTypes=Movie,Episode,Video,Series,MusicVideo,BoxSet")
                        }
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

        builder.append("&Fields=PrimaryImageAspectRatio,Overview,People,ImageTags,PrimaryImageAspectRatio,ChildCount,PrimaryImageTag,PrimaryImageItemId,AlbumPrimaryImageTag,AlbumId,Artists,Album,RunTimeTicks,UserData")
        builder.append("&EnableImageTypes=Primary,Thumb,Backdrop&ImageTypeLimit=1")
        builder.append("&SortBy=${sortField.apiValue}")
        builder.append("&SortOrder=${if (sortDescending) "Descending" else "Ascending"}")
        return builder.toString()
    }

    private fun buildMusicItemsCountUrl(
        baseUrl: String,
        userId: String,
        libraryId: String,
        includeItemTypes: String,
        recursive: Boolean
    ): String {
        return buildString {
            append("$baseUrl/emby/Users/$userId/Items?")
            append("ParentId=$libraryId")
            append("&Recursive=$recursive")
            append("&IncludeItemTypes=${encodeQueryValue(includeItemTypes)}")
            append("&Limit=0")
            append("&Fields=BasicSyncInfo")
        }
    }

    private fun buildMusicSongEntries(
        baseUrl: String,
        items: JSONArray?
    ): List<MusicListEntryUiModel> {
        return buildList {
            for (index in 0 until (items?.length() ?: 0)) {
                val item = items?.optJSONObject(index) ?: continue
                val itemId = item.optString("Id")
                if (itemId.isBlank()) continue
                val imageInfo = resolveImageInfo(item)
                val artists = parseSimpleStrings(item.optJSONArray("Artists")).joinToString(" / ")
                val albumTitle = item.optString("Album")
                val runtimeLabel = formatTicks(item.optLong("RunTimeTicks"))
                add(
                    MusicListEntryUiModel(
                        id = itemId,
                        title = item.optString("Name", context.getString(R.string.untitled_media)),
                        subtitle = listOfNotNull(
                            artists.takeIf { it.isNotBlank() },
                            albumTitle.takeIf { it.isNotBlank() }
                        ).joinToString(" • "),
                        detail = runtimeLabel,
                        imageUrl = buildImageUrl(
                            baseUrl = baseUrl,
                            imageItemId = imageInfo.first,
                            imageType = imageInfo.second,
                            imageTag = imageInfo.third,
                            maxWidth = 320,
                            maxHeight = 320
                        ),
                        kind = MusicEntryKind.SONG,
                        browseType = MusicBrowseType.SONGS,
                        itemType = item.optString("Type"),
                        runtimeTicks = item.optLong("RunTimeTicks"),
                        albumTitle = albumTitle,
                        artistLine = artists
                    )
                )
            }
        }
    }

    private fun buildMusicContainerEntries(
        baseUrl: String,
        items: JSONArray?,
        browseType: MusicBrowseType
    ): List<MusicListEntryUiModel> {
        return buildList {
            for (index in 0 until (items?.length() ?: 0)) {
                val item = items?.optJSONObject(index) ?: continue
                val itemId = item.optString("Id")
                if (itemId.isBlank()) continue
                val imageInfo = resolveImageInfo(item)
                val artists = parseSimpleStrings(item.optJSONArray("Artists")).joinToString(" / ")
                val year = item.optInt("ProductionYear").takeIf { it > 0 }?.toString().orEmpty()
                val childCount = item.optInt("ChildCount")
                val subtitle = when (browseType) {
                    MusicBrowseType.ALBUMS -> listOfNotNull(
                        artists.takeIf { it.isNotBlank() },
                        year.takeIf { it.isNotBlank() }
                    ).joinToString(" • ")

                    MusicBrowseType.PLAYLISTS,
                    MusicBrowseType.FOLDERS -> childCount.takeIf { it > 0 }?.let {
                        context.getString(R.string.library_total_count, it)
                    }.orEmpty()

                    MusicBrowseType.LOCAL -> ""

                    else -> item.optString("Type")
                }
                add(
                    MusicListEntryUiModel(
                        id = itemId,
                        title = item.optString("Name", context.getString(R.string.untitled_media)),
                        subtitle = subtitle,
                        detail = context.getString(R.string.music_list_open_container),
                        imageUrl = buildImageUrl(
                            baseUrl = baseUrl,
                            imageItemId = imageInfo.first,
                            imageType = imageInfo.second,
                            imageTag = imageInfo.third,
                            maxWidth = 320,
                            maxHeight = 320
                        ),
                        kind = MusicEntryKind.CONTAINER,
                        browseType = browseType,
                        itemType = item.optString("Type")
                    )
                )
            }
        }
    }

    private fun buildMusicArtistEntries(
        baseUrl: String,
        items: JSONArray?
    ): List<MusicListEntryUiModel> {
        return buildList {
            for (index in 0 until (items?.length() ?: 0)) {
                val item = items?.optJSONObject(index) ?: continue
                val itemId = item.optString("Id")
                if (itemId.isBlank()) continue
                val imageInfo = resolveImageInfo(item)
                val albumCount = item.optInt("AlbumCount")
                val songCount = item.optInt("SongCount")
                val subtitleParts = buildList {
                    if (albumCount > 0) add(context.getString(R.string.music_list_artist_album_count, albumCount))
                    if (songCount > 0) add(context.getString(R.string.music_list_artist_song_count, songCount))
                }
                add(
                    MusicListEntryUiModel(
                        id = itemId,
                        title = item.optString("Name", context.getString(R.string.untitled_media)),
                        subtitle = subtitleParts.joinToString(" • "),
                        detail = context.getString(R.string.music_list_open_container),
                        imageUrl = buildImageUrl(
                            baseUrl = baseUrl,
                            imageItemId = imageInfo.first,
                            imageType = imageInfo.second,
                            imageTag = imageInfo.third,
                            maxWidth = 320,
                            maxHeight = 320
                        ),
                        kind = MusicEntryKind.CONTAINER,
                        browseType = MusicBrowseType.ARTISTS,
                        itemType = item.optString("Type")
                    )
                )
            }
        }
    }

    private fun buildPlaylistCountUrl(
        baseUrl: String,
        userId: String,
        libraryId: String,
        includeParent: Boolean
    ): String {
        return buildString {
            append("$baseUrl/emby/Users/$userId/Items?")
            if (includeParent) {
                append("ParentId=$libraryId&")
            }
            append("Recursive=true")
            append("&IncludeItemTypes=Playlist")
            append("&MediaTypes=Audio")
            append("&Limit=0")
            append("&Fields=BasicSyncInfo")
        }
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
            append("&IncludeItemTypes=Movie,Episode,Video,Series,MusicVideo,BoxSet,Folder,Audio,MusicAlbum,MusicArtist,Playlist")
            append("&Fields=PrimaryImageAspectRatio,Overview,People,ImageTags,PrimaryImageAspectRatio,ChildCount")
            append("&EnableImageTypes=Primary,Thumb,Backdrop&ImageTypeLimit=1")
            append("&SortBy=SortName")
            append("&SortOrder=Ascending")
        }
    }

    private fun buildMusicSongsUrl(
        baseUrl: String,
        userId: String,
        parentId: String,
        startIndex: Int = 0,
        limit: Int = MUSIC_ITEMS_PAGE_SIZE
    ): String {
        return buildString {
            append("$baseUrl/emby/Users/$userId/Items?")
            append("ParentId=$parentId")
            append("&")
            append("Recursive=true")
            append("&StartIndex=$startIndex")
            append("&Limit=$limit")
            append("&IncludeItemTypes=Audio")
            append("&MediaTypes=Audio")
            append("&Fields=ImageTags,PrimaryImageTag,PrimaryImageItemId,AlbumPrimaryImageTag,AlbumId,Artists,Album,RunTimeTicks,UserData")
            append("&EnableImageTypes=Primary,Thumb,Backdrop")
            append("&EnableUserData=true")
            append("&SortBy=SortName")
            append("&SortOrder=Ascending")
        }
    }

    private fun buildMusicFavoritesUrl(
        baseUrl: String,
        userId: String,
        libraryId: String,
        startIndex: Int = 0,
        limit: Int = MUSIC_ITEMS_PAGE_SIZE
    ): String {
        return buildString {
            append("$baseUrl/emby/Users/$userId/Items?")
            append("ParentId=$libraryId")
            append("&Recursive=true")
            append("&StartIndex=$startIndex")
            append("&Limit=$limit")
            append("&IsFavorite=true")
            append("&IncludeItemTypes=Audio")
            append("&Fields=ImageTags,PrimaryImageTag,PrimaryImageItemId,AlbumPrimaryImageTag,AlbumId,Artists,Album,RunTimeTicks,UserData")
            append("&EnableImageTypes=Primary,Thumb,Backdrop")
            append("&EnableUserData=true")
            append("&SortBy=SortName")
            append("&SortOrder=Ascending")
        }
    }

    private fun buildMusicAlbumsUrl(
        baseUrl: String,
        userId: String,
        libraryId: String
    ): String {
        return buildString {
            append("$baseUrl/emby/Users/$userId/Items?")
            append("ParentId=$libraryId")
            append("&Recursive=true")
            append("&IncludeItemTypes=MusicAlbum")
            append("&Fields=ImageTags,PrimaryImageTag,PrimaryImageItemId,Artists,ProductionYear,ChildCount")
            append("&EnableImageTypes=Primary,Thumb,Backdrop")
            append("&SortBy=SortName")
            append("&SortOrder=Ascending")
            append("&Limit=500")
        }
    }

    private fun buildMusicArtistsUrl(
        baseUrl: String,
        userId: String,
        libraryId: String
    ): String {
        return buildString {
            append("$baseUrl/emby/Artists?")
            append("UserId=$userId")
            append("&ParentId=$libraryId")
            append("&Recursive=true")
            append("&Fields=ImageTags,PrimaryImageTag,PrimaryImageItemId,AlbumCount,SongCount")
            append("&SortBy=SortName")
            append("&SortOrder=Ascending")
            append("&Limit=500")
        }
    }

    private fun buildMusicArtistSongsUrl(
        baseUrl: String,
        userId: String,
        libraryId: String,
        artistId: String
    ): String {
        return buildString {
            append("$baseUrl/emby/Users/$userId/Items?")
            append("ParentId=$libraryId")
            append("&Recursive=true")
            append("&IncludeItemTypes=Audio")
            append("&MediaTypes=Audio")
            append("&ArtistIds=$artistId")
            append("&Fields=ImageTags,PrimaryImageTag,PrimaryImageItemId,AlbumPrimaryImageTag,AlbumId,Artists,Album,RunTimeTicks,UserData")
            append("&EnableImageTypes=Primary,Thumb,Backdrop")
            append("&EnableUserData=true")
            append("&SortBy=SortName")
            append("&SortOrder=Ascending")
            append("&Limit=500")
        }
    }

    private fun buildMusicPlaylistsUrl(
        baseUrl: String,
        userId: String,
        libraryId: String
    ): String {
        return buildString {
            append("$baseUrl/emby/Users/$userId/Items?")
            append("ParentId=$libraryId")
            append("&Recursive=true")
            append("&IncludeItemTypes=Playlist")
            append("&MediaTypes=Audio")
            append("&Fields=ImageTags,PrimaryImageTag,PrimaryImageItemId,ChildCount")
            append("&EnableImageTypes=Primary,Thumb,Backdrop")
            append("&SortBy=SortName")
            append("&SortOrder=Ascending")
            append("&Limit=500")
        }
    }

    private fun buildMusicPlaylistItemsUrl(
        baseUrl: String,
        userId: String,
        playlistId: String
    ): String {
        return buildString {
            append("$baseUrl/emby/Playlists/$playlistId/Items?")
            append("UserId=$userId")
            append("&Fields=ImageTags,PrimaryImageTag,PrimaryImageItemId,AlbumPrimaryImageTag,AlbumId,Artists,Album,RunTimeTicks,UserData")
            append("&EnableImageTypes=Primary,Thumb,Backdrop")
            append("&EnableUserData=true")
        }
    }

    private fun buildMusicFoldersUrl(
        baseUrl: String,
        userId: String,
        libraryId: String
    ): String {
        return buildString {
            append("$baseUrl/emby/Users/$userId/Items?")
            append("ParentId=$libraryId")
            append("&Recursive=false")
            append("&Filters=IsFolder")
            append("&Fields=ImageTags,PrimaryImageTag,PrimaryImageItemId,ChildCount")
            append("&EnableImageTypes=Primary,Thumb,Backdrop")
            append("&SortBy=SortName")
            append("&SortOrder=Ascending")
            append("&Limit=500")
        }
    }

    private fun buildMusicSearchUrl(
        baseUrl: String,
        userId: String,
        libraryId: String,
        query: String
    ): String {
        return buildString {
            append("$baseUrl/emby/Users/$userId/Items?")
            append("ParentId=$libraryId")
            append("&Recursive=true")
            append("&SearchTerm=${encodeQueryValue(query)}")
            append("&IncludeItemTypes=Audio,MusicAlbum,MusicArtist,Playlist")
            append("&Fields=ImageTags,PrimaryImageTag,PrimaryImageItemId,AlbumPrimaryImageTag,AlbumId,Artists,Album,RunTimeTicks,UserData,ChildCount")
            append("&EnableImageTypes=Primary,Thumb,Backdrop")
            append("&EnableUserData=true")
            append("&SortBy=SortName")
            append("&SortOrder=Ascending")
            append("&Limit=100")
        }
    }

    private fun buildPlaybackHistoryUrl(
        baseUrl: String,
        userId: String,
        startIndex: Int,
        limit: Int,
        category: PlaybackHistoryCategory,
        parentId: String? = null
    ): String {
        return buildString {
            append("$baseUrl/emby/Users/$userId/Items?")
            if (!parentId.isNullOrBlank()) {
                append("ParentId=$parentId&")
            }
            append("Recursive=true")
            append("&StartIndex=$startIndex")
            append("&Limit=$limit")
            append("&IncludeItemTypes=${encodeQueryValue(category.includeItemTypes)}")
            append("&Fields=ImageTags,UserData,PrimaryImageAspectRatio,RunTimeTicks,ParentId,SeriesName")
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
            append("&Fields=ImageTags,UserData,PrimaryImageAspectRatio,RunTimeTicks,ParentId,SeriesName")
            append("&EnableImageTypes=Primary,Thumb,Backdrop&ImageTypeLimit=1")
            append("&EnableUserData=true")
            append("&SortBy=SortName")
            append("&SortOrder=Ascending")
        }
    }

    private fun buildAudioPlaybackUrl(
        baseUrl: String,
        userId: String,
        accessToken: String,
        itemId: String,
        mediaSourceId: String,
        playSessionId: String
    ): String {
        return buildString {
            append(baseUrl)
            append("/emby/Audio/")
            append(itemId)
            append("/stream")
            append("?UserId=")
            append(userId)
            append("&api_key=")
            append(accessToken)
            append("&static=true")
            if (mediaSourceId.isNotBlank()) {
                append("&MediaSourceId=")
                append(mediaSourceId)
            }
            if (playSessionId.isNotBlank()) {
                append("&PlaySessionId=")
                append(playSessionId)
            }
        }
    }

    private fun fetchItemsTotalCount(
        url: String,
        accessToken: String,
        errorMessage: String
    ): Int {
        val request = Request.Builder()
            .url(url)
            .header("X-Emby-Token", accessToken)
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("$errorMessage：${response.code}")
            }

            val json = JSONObject(response.body?.string().orEmpty())
            return json.optInt("TotalRecordCount", 0)
        }
    }

    private fun resolveLibraryNameFromItem(
        item: JSONObject,
        libraryTitleMap: Map<String, String>
    ): String {
        val parentId = item.optString("ParentId")
        val seriesName = item.optString("SeriesName")
        return libraryTitleMap[parentId]
            ?: seriesName.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.media_library)
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

    private fun buildMusicListPageCache(page: MusicListPageUiModel): JSONObject {
        return JSONObject()
            .put("title", page.title)
            .put("subtitle", page.subtitle)
            .put("totalCount", page.totalCount)
            .put("isSongList", page.isSongList)
            .put("libraryId", page.libraryId)
            .put("items", page.items.toMusicListEntryJsonArray())
    }

    private fun parseMusicListPageCache(json: JSONObject): MusicListPageUiModel {
        return MusicListPageUiModel(
            title = json.optString("title"),
            subtitle = json.optString("subtitle"),
            items = parseMusicListEntries(json.optJSONArray("items")),
            totalCount = json.optInt("totalCount", 0),
            isSongList = json.optBoolean("isSongList", true),
            libraryId = json.optString("libraryId")
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
                        totalCount = item.optInt("totalCount", 0),
                        collectionType = item.optString("collectionType")
                    )
                )
            }
        }
    }

    private fun parseMusicListEntries(items: JSONArray?): List<MusicListEntryUiModel> {
        return buildList {
            for (index in 0 until (items?.length() ?: 0)) {
                val item = items?.optJSONObject(index) ?: continue
                val kind = runCatching {
                    MusicEntryKind.valueOf(item.optString("kind", MusicEntryKind.SONG.name))
                }.getOrDefault(MusicEntryKind.SONG)
                val browseType = runCatching {
                    MusicBrowseType.valueOf(item.optString("browseType", MusicBrowseType.SONGS.name))
                }.getOrDefault(MusicBrowseType.SONGS)
                add(
                    MusicListEntryUiModel(
                        id = item.optString("id"),
                        title = item.optString("title"),
                        subtitle = item.optString("subtitle"),
                        detail = item.optString("detail"),
                        imageUrl = item.optString("imageUrl").ifBlank { null },
                        kind = kind,
                        browseType = browseType,
                        itemType = item.optString("itemType"),
                        runtimeTicks = item.optLong("runtimeTicks"),
                        albumTitle = item.optString("albumTitle"),
                        artistLine = item.optString("artistLine")
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
                        .put("collectionType", item.collectionType)
                )
            }
        }
    }

    private fun List<MusicListEntryUiModel>.toMusicListEntryJsonArray(): JSONArray {
        return JSONArray().also { array ->
            forEach { item ->
                array.put(
                    JSONObject()
                        .put("id", item.id)
                        .put("title", item.title)
                        .put("subtitle", item.subtitle)
                        .put("detail", item.detail)
                        .put("imageUrl", item.imageUrl)
                        .put("kind", item.kind.name)
                        .put("browseType", item.browseType.name)
                        .put("itemType", item.itemType)
                        .put("runtimeTicks", item.runtimeTicks)
                        .put("albumTitle", item.albumTitle)
                        .put("artistLine", item.artistLine)
                )
            }
        }
    }

    companion object {
        private const val HOME_CACHE_MAX_AGE_MS = 15L * 60L * 1000L
        private const val LIBRARY_CACHE_MAX_AGE_MS = 15L * 60L * 1000L
        private const val MUSIC_CACHE_MAX_AGE_MS = 15L * 60L * 1000L
        private const val FILTER_CACHE_MAX_AGE_MS = 30L * 60L * 1000L
        private const val MUSIC_ITEMS_PAGE_SIZE = 500
    }
}
