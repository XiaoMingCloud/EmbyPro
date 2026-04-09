package com.liujiaming.embypro

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity(), ServerActionListener {

    private val serverItems = mutableListOf<ServerUiModel>()
    private lateinit var serverListAdapter: ServerListAdapter
    private lateinit var serverList: RecyclerView
    private lateinit var emptyStateText: TextView
    private val networkExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val embyApiService by lazy { EmbyApiService(this) }
    private val sessionStore by lazy { ServerSessionStore(this) }
    private var pendingAvatarServerId: Long? = null
    private val avatarPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        handleAvatarPicked(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EdgeToEdgeHelper.enable(this, lightSystemBars = true)
        setContentView(R.layout.activity_main)

        supportActionBar?.hide()

        serverList = findViewById(R.id.serverRecyclerView)
        emptyStateText = findViewById(R.id.emptyStateText)
        val addServerButton = findViewById<FloatingActionButton>(R.id.addServerButton)
        val moreButton = findViewById<ImageButton>(R.id.moreButton)
        val topBar = findViewById<TextView>(R.id.titleText).parent as View

        serverItems.addAll(sessionStore.loadServers())
        serverListAdapter = ServerListAdapter(serverItems, this)
        serverList.layoutManager = LinearLayoutManager(this)
        serverList.adapter = serverListAdapter
        updateEmptyState()

        addServerButton.setOnClickListener {
            showConnectServerDialog()
        }

        moreButton.setOnClickListener {
            Toast.makeText(this, getString(R.string.more_actions_pending), Toast.LENGTH_SHORT).show()
        }

        EdgeToEdgeHelper.applyInsets(topBar, applyTop = true)
        EdgeToEdgeHelper.applyInsets(serverList, applyBottom = true)
        EdgeToEdgeHelper.applyMargins(addServerButton, applyBottom = true)

        if (intent.getBooleanExtra(EXTRA_AUTO_OPEN_CONNECT, false) && serverItems.isEmpty()) {
            addServerButton.post { showConnectServerDialog() }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        networkExecutor.shutdownNow()
    }

    override fun onOpen(server: ServerUiModel) {
        startActivity(
            Intent(this, ServerHomeActivity::class.java)
                .putExtra(ServerHomeActivity.EXTRA_SERVER_NAME, server.name)
                .putExtra(ServerHomeActivity.EXTRA_BASE_URL, embyApiService.buildBaseUrl(server.address, server.port))
                .putExtra(ServerHomeActivity.EXTRA_USER_ID, server.userId)
                .putExtra(ServerHomeActivity.EXTRA_ACCESS_TOKEN, server.accessToken)
        )
    }

    override fun onRelogin(server: ServerUiModel) {
        showLoginDialog(
            baseUrl = embyApiService.buildBaseUrl(server.address, server.port),
            serverInfo = ServerInfo(server.id.toString(), server.name, ""),
            existingServer = server,
            hint = getString(R.string.relogin_hint)
        )
    }

    override fun onChangeIcon(server: ServerUiModel) {
        pendingAvatarServerId = server.id
        avatarPicker.launch(arrayOf("image/*"))
    }

    override fun onChangePassword(server: ServerUiModel) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_change_password, null)
        val passwordInput = dialogView.findViewById<EditText>(R.id.newPasswordInput)
        val saveButton = dialogView.findViewById<MaterialButton>(R.id.savePasswordButton)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        saveButton.setOnClickListener {
            val newPassword = passwordInput.text?.toString().orEmpty()
            if (newPassword.isBlank()) {
                passwordInput.error = getString(R.string.error_password_empty)
                return@setOnClickListener
            }

            updateServer(
                server.copy(
                    password = newPassword,
                    status = getString(R.string.status_updated)
                )
            )
            dialog.dismiss()
            Toast.makeText(this, getString(R.string.password_updated), Toast.LENGTH_SHORT).show()
        }

        dialog.show()
    }

    override fun onEdit(server: ServerUiModel) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_server, null)
        val nameInput = dialogView.findViewById<EditText>(R.id.editServerNameInput)
        val addressInput = dialogView.findViewById<EditText>(R.id.editServerAddressInput)
        val portInput = dialogView.findViewById<EditText>(R.id.editServerPortInput)
        val usernameInput = dialogView.findViewById<EditText>(R.id.editUsernameInput)
        val saveButton = dialogView.findViewById<MaterialButton>(R.id.saveEditButton)

        nameInput.setText(server.name)
        addressInput.setText(server.address)
        portInput.setText(server.port)
        usernameInput.setText(server.username)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        saveButton.setOnClickListener {
            val updatedName = nameInput.text?.toString()?.trim().orEmpty()
            val updatedAddress = addressInput.text?.toString()?.trim().orEmpty()
            val updatedPort = portInput.text?.toString()?.trim().orEmpty()
            val updatedUsername = usernameInput.text?.toString()?.trim().orEmpty()

            if (updatedName.isBlank()) {
                nameInput.error = getString(R.string.error_server_name_required)
                return@setOnClickListener
            }
            if (updatedAddress.isBlank()) {
                addressInput.error = getString(R.string.error_server_address_required)
                return@setOnClickListener
            }
            if (updatedUsername.isBlank()) {
                usernameInput.error = getString(R.string.error_username_required)
                return@setOnClickListener
            }

            updateServer(
                server.copy(
                    name = updatedName,
                    address = updatedAddress,
                    port = updatedPort,
                    username = updatedUsername,
                    status = getString(R.string.status_updated)
                )
            )
            dialog.dismiss()
            Toast.makeText(this, getString(R.string.server_updated), Toast.LENGTH_SHORT).show()
        }

        dialog.show()
    }

    override fun onDelete(server: ServerUiModel) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_server_title)
            .setMessage(getString(R.string.delete_server_message, server.name))
            .setPositiveButton(R.string.menu_delete_server) { _, _ ->
                serverItems.removeAll { it.id == server.id }
                serverListAdapter.removeItem(server)
                persistServers()
                updateEmptyState()
                Toast.makeText(this, getString(R.string.server_deleted), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showConnectServerDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_connect_server, null)
        val addressInput = dialogView.findViewById<EditText>(R.id.serverAddressInput)
        val portInput = dialogView.findViewById<EditText>(R.id.serverPortInput)
        val hintText = dialogView.findViewById<TextView>(R.id.serverConnectHint)
        val connectButton = dialogView.findViewById<MaterialButton>(R.id.connectServerButton)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        connectButton.setOnClickListener {
            val address = addressInput.text?.toString()?.trim().orEmpty()
            val port = portInput.text?.toString()?.trim().orEmpty()

            if (address.isBlank()) {
                addressInput.error = getString(R.string.error_server_address_required)
                return@setOnClickListener
            }

            setButtonLoading(connectButton, true, R.string.connect_server)
            hintText.text = getString(R.string.connect_server_hint)

            networkExecutor.execute {
                val baseUrl = embyApiService.buildBaseUrl(address, port)
                val result = embyApiService.fetchPublicServerInfo(baseUrl)

                runOnUiThread {
                    setButtonLoading(connectButton, false, R.string.connect_server)

                    result.onSuccess { serverInfo ->
                        dialog.dismiss()
                        showLoginDialog(baseUrl, serverInfo)
                    }.onFailure { error ->
                        hintText.text = error.message ?: getString(R.string.error_connect_server_failed)
                    }
                }
            }
        }

        dialog.show()
    }

    private fun showLoginDialog(
        baseUrl: String,
        serverInfo: ServerInfo,
        existingServer: ServerUiModel? = null,
        hint: String? = null
    ) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_server_login, null)
        val serverNameText = dialogView.findViewById<TextView>(R.id.connectedServerNameText)
        val addressText = dialogView.findViewById<TextView>(R.id.connectedServerAddressText)
        val usernameInput = dialogView.findViewById<EditText>(R.id.usernameInput)
        val passwordInput = dialogView.findViewById<EditText>(R.id.passwordInput)
        val loginHintText = dialogView.findViewById<TextView>(R.id.loginHintText)
        val loginButton = dialogView.findViewById<MaterialButton>(R.id.loginButton)

        serverNameText.text = serverInfo.serverName.ifBlank { getString(R.string.server_default_name) }
        addressText.text = baseUrl
        usernameInput.setText(existingServer?.username.orEmpty())
        passwordInput.setText(existingServer?.password.orEmpty())
        loginHintText.text = hint ?: getString(R.string.login_hint)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        loginButton.setOnClickListener {
            val username = usernameInput.text?.toString()?.trim().orEmpty()
            val password = passwordInput.text?.toString().orEmpty()

            if (username.isBlank()) {
                usernameInput.error = getString(R.string.error_username_required)
                return@setOnClickListener
            }

            if (password.isBlank()) {
                passwordInput.error = getString(R.string.error_password_required)
                return@setOnClickListener
            }

            setButtonLoading(loginButton, true, R.string.login_server)

            networkExecutor.execute {
                val result = embyApiService.authenticate(baseUrl, username, password)

                runOnUiThread {
                    setButtonLoading(loginButton, false, R.string.login_server)

                    result.onSuccess { authResult ->
                        val parsedBase = embyApiService.parseBaseUrl(baseUrl)
                        val avatarUrl = embyApiService.buildUserAvatarUrl(baseUrl, authResult.userId).orEmpty()
                        val updatedServer = ServerUiModel(
                            id = existingServer?.id ?: System.currentTimeMillis(),
                            name = serverInfo.serverName.ifBlank { username },
                            username = authResult.userName,
                            status = getString(R.string.status_just_logged_in),
                            address = parsedBase.first,
                            port = parsedBase.second,
                            password = password,
                            iconStyle = existingServer?.iconStyle ?: ServerIconStyle.INDIGO,
                            avatarUrl = avatarUrl,
                            customAvatarUri = existingServer?.customAvatarUri.orEmpty(),
                            accessToken = authResult.accessToken,
                            userId = authResult.userId
                        )

                        if (existingServer == null) {
                            serverItems.add(0, updatedServer)
                            serverListAdapter.prependItem(updatedServer)
                        } else {
                            replaceServer(updatedServer)
                        }

                        persistServers()
                        updateEmptyState()
                        dialog.dismiss()
                        Toast.makeText(this, getString(R.string.login_success, authResult.userName), Toast.LENGTH_SHORT).show()
                        if (intent.getBooleanExtra(EXTRA_RETURN_HOME_ON_SUCCESS, false)) {
                            startActivity(Intent(this, HomeTabsActivity::class.java))
                            finish()
                        }
                    }.onFailure { error ->
                        loginHintText.text = error.message ?: getString(R.string.error_login_failed)
                    }
                }
            }
        }

        dialog.show()
    }

    private fun setButtonLoading(button: MaterialButton, isLoading: Boolean, idleTextRes: Int) {
        button.isEnabled = !isLoading
        button.text = if (isLoading) getString(R.string.loading) else getString(idleTextRes)
    }

    private fun updateServer(server: ServerUiModel) {
        replaceServer(server)
        persistServers()
        updateEmptyState()
    }

    private fun handleAvatarPicked(uri: Uri?) {
        val serverId = pendingAvatarServerId
        pendingAvatarServerId = null

        if (serverId == null || uri == null) {
            return
        }

        runCatching {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }

        val server = serverItems.firstOrNull { it.id == serverId } ?: return
        val imageBytes = runCatching {
            contentResolver.openInputStream(uri)?.use { input -> input.readBytes() }
        }.getOrNull()

        if (imageBytes == null || imageBytes.isEmpty()) {
            Toast.makeText(this, getString(R.string.avatar_update_failed), Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(this, getString(R.string.avatar_uploading), Toast.LENGTH_SHORT).show()
        networkExecutor.execute {
            val baseUrl = embyApiService.buildBaseUrl(server.address, server.port)
            val result = embyApiService.updateUserAvatar(
                baseUrl = baseUrl,
                userId = server.userId,
                accessToken = server.accessToken,
                imageBytes = imageBytes
            )

            runOnUiThread {
                result.onSuccess { avatarUrl ->
                    updateServer(
                        server.copy(
                            avatarUrl = avatarUrl,
                            customAvatarUri = "",
                            status = getString(R.string.status_updated)
                        )
                    )
                    Toast.makeText(this, getString(R.string.avatar_updated), Toast.LENGTH_SHORT).show()
                }.onFailure {
                    Toast.makeText(this, getString(R.string.avatar_update_failed), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun replaceServer(server: ServerUiModel) {
        val index = serverItems.indexOfFirst { it.id == server.id }
        if (index == -1) return
        serverItems[index] = server
        serverListAdapter.updateItem(server)
    }

    private fun updateEmptyState() {
        val hasServers = serverItems.isNotEmpty()
        serverList.visibility = if (hasServers) View.VISIBLE else View.GONE
        emptyStateText.visibility = if (hasServers) View.GONE else View.VISIBLE
    }

    private fun persistServers() {
        sessionStore.saveServers(serverItems)
    }

    companion object {
        const val EXTRA_AUTO_OPEN_CONNECT = "extra_auto_open_connect"
        const val EXTRA_RETURN_HOME_ON_SUCCESS = "extra_return_home_on_success"
    }
}
