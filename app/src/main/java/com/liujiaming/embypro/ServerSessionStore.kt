package com.liujiaming.embypro

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class ServerSessionStore(context: Context) {

    private val preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

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
    }

    companion object {
        private const val PREF_NAME = "emby_server_session"
        private const val KEY_SERVERS = "servers"
    }
}
