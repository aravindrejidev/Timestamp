package com.timestamp.app.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import java.io.File

object FileUtils {

    fun safeName(artist: String, title: String, ext: String): String {
        val base = listOf(artist.trim(), title.trim()).filter { it.isNotEmpty() }
            .joinToString(" - ").ifBlank { "lyrics" }
        return base.replace(Regex("[\\\\/:*?\"<>|\\r\\n]"), "_").trim() + "." + ext
    }

    fun displayName(context: Context, uri: Uri): String {
        val name = try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        } catch (e: Exception) {
            null
        }
        return name ?: uri.lastPathSegment ?: "audio"
    }

    fun readText(context: Context, uri: Uri): String? = try {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
    } catch (e: Exception) {
        null
    }

    fun writeToUri(context: Context, uri: Uri, text: String): Boolean = try {
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
        true
    } catch (e: Exception) {
        false
    }

    /** Creates [name] inside the chosen folder and returns its Uri, or null on failure. */
    fun saveToFolder(context: Context, folderUri: Uri, name: String, text: String): Uri? {
        return try {
            val dir = DocumentFile.fromTreeUri(context, folderUri) ?: return null
            val file = dir.createFile("application/octet-stream", name) ?: return null
            if (writeToUri(context, file.uri, text)) file.uri else null
        } catch (e: Exception) {
            null
        }
    }

    fun share(context: Context, name: String, text: String) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, name)
        file.writeText(text)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Share $name").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
