package com.timestamp.app.viewmodel

import android.app.Application
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.timestamp.app.TimestampApp
import com.timestamp.app.data.LyricsJson
import com.timestamp.app.data.ProjectEntity
import com.timestamp.app.format.LyricsParser
import com.timestamp.app.util.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ImportViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as TimestampApp).repository

    var title by mutableStateOf("")
    var artist by mutableStateOf("")
    var lyrics by mutableStateOf("")
    var audioUri by mutableStateOf<Uri?>(null)
        private set
    var audioName by mutableStateOf("")
        private set
    var audioDurationMs by mutableLongStateOf(0L)
        private set
    var busy by mutableStateOf(false)
        private set

    val canCreate: Boolean get() = audioUri != null && lyrics.isNotBlank()

    private fun ctx(): Application = getApplication()

    fun onAudioPicked(uri: Uri) {
        try {
            ctx().contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) {
        }
        audioUri = uri
        audioName = FileUtils.displayName(ctx(), uri)
        val fallbackTitle = audioName.substringBeforeLast('.')
        viewModelScope.launch {
            var t: String? = null
            var a: String? = null
            var d = 0L
            withContext(Dispatchers.IO) {
                val r = MediaMetadataRetriever()
                try {
                    r.setDataSource(ctx(), uri)
                    t = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                    a = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                    d = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                } catch (_: Exception) {
                } finally {
                    try { r.release() } catch (_: Exception) {}
                }
            }
            if (title.isBlank()) title = t?.takeIf { it.isNotBlank() } ?: fallbackTitle
            if (artist.isBlank()) artist = a?.takeIf { it.isNotBlank() } ?: ""
            audioDurationMs = d
        }
    }

    fun onLyricsFilePicked(uri: Uri) {
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) { FileUtils.readText(ctx(), uri) }
            if (text != null) lyrics = text
        }
    }

    fun createProject(onDone: (Long) -> Unit) {
        val uri = audioUri ?: return
        if (busy || lyrics.isBlank()) return
        busy = true
        viewModelScope.launch {
            val lines = LyricsParser.parse(lyrics)
            val id = repo.upsert(
                ProjectEntity(
                    title = title.ifBlank { audioName.substringBeforeLast('.') },
                    artist = artist.trim(),
                    audioUri = uri.toString(),
                    lyricsJson = LyricsJson.encode(lines),
                    durationMs = audioDurationMs,
                    updatedAt = System.currentTimeMillis(),
                    totalLines = lines.size,
                    syncedLines = lines.count { it.startMs >= 0 }
                )
            )
            busy = false
            title = ""
            artist = ""
            lyrics = ""
            audioUri = null
            audioName = ""
            audioDurationMs = 0L
            onDone(id)
        }
    }
}
