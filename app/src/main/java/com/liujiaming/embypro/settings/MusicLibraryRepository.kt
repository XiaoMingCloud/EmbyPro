package com.liujiaming.embypro

import android.content.Context
import android.os.Handler
import android.os.Looper

data class MusicLibraryState(
    val baseUrl: String = "",
    val userId: String = "",
    val accessToken: String = "",
    val musicLibraries: List<MediaLibraryUiModel> = emptyList(),
    val currentLibraryId: String? = null,
    val libraryStats: MusicLibraryStatsUiModel? = null,
    val aliasMap: Map<String, String> = emptyMap(),
    val isLoadingLibraries: Boolean = false,
    val isLoadingStats: Boolean = false,
    val errorMessage: String? = null,
    val statsErrorMessage: String? = null
) {
    val currentMusicLibrary: MediaLibraryUiModel?
        get() = musicLibraries.firstOrNull { it.id == currentLibraryId }

    val isEmpty: Boolean
        get() = !isLoadingLibraries && errorMessage == null && musicLibraries.isEmpty()
}

fun interface MusicLibraryStateListener {
    fun onStateChanged(state: MusicLibraryState)
}

object MusicLibraryRepository {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val listeners = linkedSetOf<MusicLibraryStateListener>()

    private var appContext: Context? = null
    private var embyApiService: EmbyApiService? = null
    private var aliasStore: MusicLibraryAliasStore? = null
    private var selectionStore: MusicLibrarySelectionStore? = null
    private var state: MusicLibraryState = MusicLibraryState()
    private var currentSessionKey: String = ""

    fun currentState(): MusicLibraryState = state

    fun subscribe(listener: MusicLibraryStateListener, emitImmediately: Boolean = true) {
        listeners.add(listener)
        if (emitImmediately) {
            dispatch(listener, state)
        }
    }

    fun unsubscribe(listener: MusicLibraryStateListener) {
        listeners.remove(listener)
    }

    fun connect(
        context: Context,
        baseUrl: String,
        userId: String,
        accessToken: String,
        forceRefresh: Boolean = false
    ) {
        if (baseUrl.isBlank() || userId.isBlank() || accessToken.isBlank()) {
            updateState {
                it.copy(
                    baseUrl = baseUrl,
                    userId = userId,
                    accessToken = accessToken,
                    isLoadingLibraries = false,
                    isLoadingStats = false,
                    errorMessage = context.getString(R.string.server_data_missing)
                )
            }
            return
        }

        ensureInitialized(context)
        val sessionKey = buildSessionKey(baseUrl, userId)
        val shouldReload = forceRefresh || currentSessionKey != sessionKey || state.musicLibraries.isEmpty()

        currentSessionKey = sessionKey
        if (!shouldReload) {
            updateState {
                it.copy(
                    baseUrl = baseUrl,
                    userId = userId,
                    accessToken = accessToken,
                    aliasMap = buildAliasMap(baseUrl, userId, it.musicLibraries)
                )
            }
            if (state.libraryStats == null && !state.isLoadingStats) {
                refreshStats()
            } else {
                notifyListeners()
            }
            return
        }

        updateState {
            it.copy(
                baseUrl = baseUrl,
                userId = userId,
                accessToken = accessToken,
                isLoadingLibraries = true,
                isLoadingStats = false,
                errorMessage = null,
                statsErrorMessage = null,
                libraryStats = null
            )
        }

        AppExecutors.io.execute {
            val api = embyApiService ?: return@execute
            val librariesResult = api.fetchMusicLibraries(baseUrl, userId, accessToken)
            mainHandler.post {
                if (currentSessionKey != sessionKey) return@post
                librariesResult.onSuccess { libraries ->
                    val selection = selectionStore?.loadSelectedLibraryId(baseUrl, userId)
                    val currentLibraryId = libraries.firstOrNull { it.id == selection }?.id ?: libraries.firstOrNull()?.id
                    updateState {
                        it.copy(
                            baseUrl = baseUrl,
                            userId = userId,
                            accessToken = accessToken,
                            musicLibraries = libraries,
                            currentLibraryId = currentLibraryId,
                            aliasMap = buildAliasMap(baseUrl, userId, libraries),
                            isLoadingLibraries = false,
                            errorMessage = null,
                            statsErrorMessage = null,
                            libraryStats = null
                        )
                    }
                    if (currentLibraryId.isNullOrBlank()) {
                        notifyListeners()
                    } else {
                        selectionStore?.saveSelectedLibraryId(baseUrl, userId, currentLibraryId)
                        refreshStats()
                    }
                }.onFailure { error ->
                    updateState {
                        it.copy(
                            isLoadingLibraries = false,
                            isLoadingStats = false,
                            musicLibraries = emptyList(),
                            currentLibraryId = null,
                            libraryStats = null,
                            errorMessage = error.message ?: context.getString(R.string.music_settings_load_failed)
                        )
                    }
                }
            }
        }
    }

    fun refreshStats() {
        val current = state.currentMusicLibrary ?: return
        if (state.baseUrl.isBlank() || state.userId.isBlank() || state.accessToken.isBlank()) return
        val sessionKey = currentSessionKey

        updateState {
            it.copy(
                isLoadingStats = true,
                statsErrorMessage = null,
                errorMessage = null
            )
        }

        AppExecutors.io.execute {
            val api = embyApiService ?: return@execute
            val statsResult = api.fetchMusicLibraryStats(
                baseUrl = state.baseUrl,
                userId = state.userId,
                accessToken = state.accessToken,
                libraryId = current.id
            )
            mainHandler.post {
                if (currentSessionKey != sessionKey) return@post
                statsResult.onSuccess { stats ->
                    if (state.currentLibraryId != current.id) return@onSuccess
                    updateState {
                        it.copy(
                            isLoadingStats = false,
                            libraryStats = stats,
                            statsErrorMessage = null
                        )
                    }
                }.onFailure { error ->
                    if (state.currentLibraryId != current.id) return@onFailure
                    updateState {
                        it.copy(
                            isLoadingStats = false,
                            libraryStats = null,
                            statsErrorMessage = error.message ?: appContext?.getString(R.string.music_settings_stats_load_failed)
                        )
                    }
                }
            }
        }
    }

    fun selectLibrary(libraryId: String) {
        if (libraryId.isBlank() || libraryId == state.currentLibraryId) return
        if (state.musicLibraries.none { it.id == libraryId }) return

        selectionStore?.saveSelectedLibraryId(state.baseUrl, state.userId, libraryId)
        updateState {
            it.copy(
                currentLibraryId = libraryId,
                libraryStats = null,
                statsErrorMessage = null
            )
        }
        refreshStats()
    }

    fun saveAlias(libraryId: String, alias: String) {
        if (libraryId.isBlank()) return
        aliasStore?.saveAlias(state.baseUrl, state.userId, libraryId, alias)
        updateAliasMap()
    }

    fun clearAlias(libraryId: String) {
        if (libraryId.isBlank()) return
        aliasStore?.clearAlias(state.baseUrl, state.userId, libraryId)
        updateAliasMap()
    }

    fun displayName(library: MediaLibraryUiModel?): String {
        if (library == null) return ""
        return state.aliasMap[library.id] ?: library.title
    }

    private fun ensureInitialized(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        embyApiService = EmbyApiService(appContext!!)
        aliasStore = MusicLibraryAliasStore(appContext!!)
        selectionStore = MusicLibrarySelectionStore(appContext!!)
    }

    private fun updateAliasMap() {
        val baseUrl = state.baseUrl
        val userId = state.userId
        updateState {
            it.copy(aliasMap = buildAliasMap(baseUrl, userId, it.musicLibraries))
        }
    }

    private fun buildAliasMap(
        baseUrl: String,
        userId: String,
        libraries: List<MediaLibraryUiModel>
    ): Map<String, String> {
        val store = aliasStore ?: return emptyMap()
        return libraries.mapNotNull { library ->
            store.loadAlias(baseUrl, userId, library.id)?.let { alias ->
                library.id to alias
            }
        }.toMap()
    }

    private fun updateState(transform: (MusicLibraryState) -> MusicLibraryState) {
        state = transform(state)
        notifyListeners()
    }

    private fun notifyListeners() {
        val snapshot = state
        listeners.forEach { listener ->
            dispatch(listener, snapshot)
        }
    }

    private fun dispatch(listener: MusicLibraryStateListener, state: MusicLibraryState) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            listener.onStateChanged(state)
        } else {
            mainHandler.post { listener.onStateChanged(state) }
        }
    }

    private fun buildSessionKey(baseUrl: String, userId: String): String {
        return "$baseUrl::$userId"
    }
}
