package com.liujiaming.embypro

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.UUID

data class GlobalBackgroundImage(
    val id: String,
    val absolutePath: String,
    val selected: Boolean,
    val builtIn: Boolean = false
)

class GlobalBackgroundLibrary(private val context: Context) {
    private val themeStore = GlobalThemeStore(context)
    private val storageDir = File(context.filesDir, BACKGROUND_DIR_NAME).apply { mkdirs() }

    fun listImages(): List<GlobalBackgroundImage> {
        val selectedPath = themeStore.loadBackgroundImageUri()
        val uploadedItems = storageDir.listFiles()
            ?.filter { it.isFile }
            ?.sortedByDescending { it.lastModified() }
            ?.map { file ->
                GlobalBackgroundImage(
                    id = file.name,
                    absolutePath = file.absolutePath,
                    selected = file.absolutePath == selectedPath
                )
            }
            .orEmpty()
        val builtInWhite = GlobalBackgroundImage(
            id = BUILT_IN_WHITE_ID,
            absolutePath = BUILT_IN_WHITE_ID,
            selected = selectedPath.isNullOrBlank() || selectedPath == BUILT_IN_WHITE_ID,
            builtIn = true
        )
        return listOf(builtInWhite) + uploadedItems
    }

    fun importImages(uris: List<Uri>): Int {
        var importedCount = 0
        uris.forEach { uri ->
            runCatching {
                val extension = guessExtension(uri)
                val targetFile = File(storageDir, "${System.currentTimeMillis()}_${UUID.randomUUID()}.$extension")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                } ?: return@runCatching
                importedCount += 1
            }
        }
        return importedCount
    }

    fun selectImage(path: String) {
        if (path == BUILT_IN_WHITE_ID) {
            themeStore.clearBackgroundImageUri()
            return
        }
        if (!File(path).exists()) return
        themeStore.saveBackgroundImageUri(path)
    }

    fun deleteImage(path: String) {
        if (path == BUILT_IN_WHITE_ID) return
        val selectedPath = themeStore.loadBackgroundImageUri()
        runCatching { File(path).delete() }
        if (selectedPath == path) {
            themeStore.clearBackgroundImageUri()
        }
    }

    fun imageCount(): Int = storageDir.listFiles()?.count { it.isFile } ?: 0

    private fun guessExtension(uri: Uri): String {
        val lastSegment = uri.lastPathSegment.orEmpty()
        val rawExtension = lastSegment.substringAfterLast('.', "")
        return rawExtension.ifBlank { "jpg" }
            .lowercase()
            .takeIf { it.all { ch -> ch.isLetterOrDigit() } }
            ?: "jpg"
    }

    companion object {
        private const val BACKGROUND_DIR_NAME = "global_backgrounds"
        const val BUILT_IN_WHITE_ID = "__builtin_white_background__"
    }
}
