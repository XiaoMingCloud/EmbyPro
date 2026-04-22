package com.liujiaming.embypro

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Manages server session storage and active server selection.
 * Persists server list and active server ID using SharedPreferences with JSON serialization.
 */
class ServerSessionStore(context: Context) {

    private val preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    /**
     * Loads the list of saved servers from preferences.
     * Parses JSON array into ServerUiModel objects.
     */
    fun loadServers(): List<ServerUiModel> {
        val raw = preferences.getString(KEY_SERVERS, null).orEmpty()
        if (raw.isBlank()) return emptyList()

        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    add(
                        ServerUiModel(
                            id = item.optLong("id"),
                            name = item.optString("name"),
                            username = item.optString("username"),
                            status = item.optString("status"),
                            address = item.optString("address"),
                            port = item.optString("port"),
                            password = item.optString("password"),
                            iconStyle = ServerIconStyle.valueOf(
                                item.optString("iconStyle", ServerIconStyle.INDIGO.name)
                            ),
                            avatarUrl = item.optString("avatarUrl"),
                            customAvatarUri = item.optString("customAvatarUri"),
                            accessToken = item.optString("accessToken"),
                            userId = item.optString("userId")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    /**
     * Saves the server list to preferences.
     * Updates active server ID if current active server was removed.
     */
    fun saveServers(servers: List<ServerUiModel>) {
        val array = JSONArray()
        servers.forEach { server ->
            array.put(
                JSONObject().apply {
                    put("id", server.id)
                    put("name", server.name)
                    put("username", server.username)
                    put("status", server.status)
                    put("address", server.address)
                    put("port", server.port)
                    put("password", server.password)
                    put("iconStyle", server.iconStyle.name)
                    put("avatarUrl", server.avatarUrl)
                    put("customAvatarUri", server.customAvatarUri)
                    put("accessToken", server.accessToken)
                    put("userId", server.userId)
                }
            )
        }

        preferences.edit().putString(KEY_SERVERS, array.toString()).apply()
        val activeId = loadActiveServerId()
        if (activeId != null && servers.none { it.id == activeId }) {
            saveActiveServerId(servers.firstOrNull()?.id)
        }
    }

    /**
     * Loads the currently active server.
     * Returns the active server by ID, or the first server if no active ID is set.
     */
    fun loadCurrentServer(): ServerUiModel? {
        val servers = loadServers()
        if (servers.isEmpty()) return null
        val activeId = loadActiveServerId()
        return servers.firstOrNull { it.id == activeId } ?: servers.first()
    }

    /**
     * Saves the active server ID to preferences.
     * Removes the key if serverId is null.
     */
    fun saveActiveServerId(serverId: Long?) {
        if (serverId == null) {
            preferences.edit().remove(KEY_ACTIVE_SERVER_ID).apply()
        } else {
            preferences.edit().putLong(KEY_ACTIVE_SERVER_ID, serverId).apply()
        }
    }

    /**
     * Activates a server by saving its ID as the active server.
     */
    fun activateServer(server: ServerUiModel) {
        saveActiveServerId(server.id)
    }

    /**
     * Resolves server connection from intent extras or current active server.
     * First tries to read direct connection from intent, falls back to active server.
     *
     * @param intent The intent that may contain connection data
     * @param serverRepository Repository for building base URLs
     * @return Valid ServerConnection or null if unavailable
     */
    fun resolveConnection(
        intent: android.content.Intent,
        serverRepository: ServerRepository
    ): ServerConnection? {
        val directConnection = intent.readServerConnection()
        if (directConnection.isValid) {
            return directConnection
        }

        val currentServer = loadCurrentServer() ?: return null
        return ServerConnection(
            baseUrl = serverRepository.buildBaseUrl(currentServer.address, currentServer.port),
            userId = currentServer.userId,
            accessToken = currentServer.accessToken
        )
    }

    /**
     * Loads the active server ID from preferences.
     * Returns null if not set.
     */
    private fun loadActiveServerId(): Long? {
        return if (preferences.contains(KEY_ACTIVE_SERVER_ID)) {
            preferences.getLong(KEY_ACTIVE_SERVER_ID, 0L)
        } else {
            null
        }
    }

    companion object {
        private const val PREF_NAME = "emby_server_session"
        private const val KEY_SERVERS = "servers"
        private const val KEY_ACTIVE_SERVER_ID = "active_server_id"
    }
}
