package com.timestamp.app.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.timestamp.app.TimestampApp
import com.timestamp.app.audio.Waveform
import com.timestamp.app.audio.WaveformExtractor
import com.timestamp.app.data.LyricLine
import com.timestamp.app.data.LyricsJson
import com.timestamp.app.data.ProjectEntity
import com.timestamp.app.data.SyncMode
import com.timestamp.app.data.WordTiming
import com.timestamp.app.format.ExportFormat
import com.timestamp.app.format.LyricsExporter
import com.timestamp.app.format.PreviewParser
import com.timestamp.app.format.PreviewReport
import com.timestamp.app.format.TtmlStyle
import com.timestamp.app.format.buildReport
import com.timestamp.app.format.distributeWords
import com.timestamp.app.format.effectiveEnd
import com.timestamp.app.format.splitWords
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class EditorMeta(
    val id: Long,
    val title: String,
    val artist: String,
    val audioUri: String,
    val durationMs: Long,
    val ttmlFontSize: Int,
    val ttmlColor: String,
    val ttmlPosition: String
)

/** A parsed export (or saved file) shown on the Preview screen. */
data class PreviewData(
    val label: String,
    val format: String,
    val lines: List<LyricLine>,
    val report: PreviewReport
)

@OptIn(FlowPreview::class)
class EditorViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as TimestampApp).repository
    private val settings = (app as TimestampApp).settings

    private val player: ExoPlayer = ExoPlayer.Builder(app).build()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()
    private val _meta = MutableStateFlow<EditorMeta?>(null)
    val meta: StateFlow<EditorMeta?> = _meta.asStateFlow()
    private val _lines = MutableStateFlow<List<LyricLine>>(emptyList())
    val lines: StateFlow<List<LyricLine>> = _lines.asStateFlow()
    private val _cursor = MutableStateFlow(0)
    val cursor: StateFlow<Int> = _cursor.asStateFlow()
    private val _mode = MutableStateFlow(SyncMode.LINE)
    val syncMode: StateFlow<SyncMode> = _mode.asStateFlow()
    private val _position = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _position.asStateFlow()
    private val _duration = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _duration.asStateFlow()
    private val _playing = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _playing.asStateFlow()
    private val _speed = MutableStateFlow(1f)
    val speed: StateFlow<Float> = _speed.asStateFlow()
    private val _waveform = MutableStateFlow<Waveform?>(null)
    val waveform: StateFlow<Waveform?> = _waveform.asStateFlow()
    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val playerError: StateFlow<String?> = _error.asStateFlow()
    private val _estimateWords = MutableStateFlow(true)
    val estimateWords: StateFlow<Boolean> = _estimateWords.asStateFlow()

    private val _preview = MutableStateFlow<PreviewData?>(null)
    val preview: StateFlow<PreviewData?> = _preview.asStateFlow()

    private val undo = kotlin.collections.ArrayDeque<List<LyricLine>>()
    private var nextId = 0L
    private var dirty = false
    private var ticker: Job? = null
    private var waveformJob: Job? = null

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _playing.value = isPlaying
                if (isPlaying) {
                    startTicker()
                } else {
                    ticker?.cancel()
                    _position.value = player.currentPosition
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    val d = player.duration
                    if (d != C.TIME_UNSET && d > 0) _duration.value = d
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                _error.value = "Audio could not be played. The file may have been moved or deleted."
            }
        })

        // Debounced autosave of lyric edits.
        viewModelScope.launch {
            _lines.drop(1).debounce(1500).collect {
                if (_loaded.value && dirty) persist()
            }
        }
    }

    // ---------------------------------------------------------------- project loading

    fun openProject(id: Long) {
        viewModelScope.launch {
            if (_loaded.value && _meta.value?.id == id) return@launch
            val p = repo.get(id) ?: return@launch
            applyProject(p)
        }
    }

    private fun applyProject(p: ProjectEntity) {
        val decoded = runCatching { LyricsJson.decode(p.lyricsJson) }.getOrDefault(emptyList())
        undo.clear()
        _canUndo.value = false
        nextId = decoded.size.toLong()
        dirty = false
        _lines.value = decoded
        _cursor.value = decoded.indexOfFirst { it.startMs < 0 }.let { if (it < 0) 0 else it }
        _mode.value = SyncMode.LINE
        _error.value = null
        _position.value = 0L
        _duration.value = p.durationMs
        _speed.value = 1f
        _meta.value = EditorMeta(
            p.id, p.title, p.artist, p.audioUri, p.durationMs,
            p.ttmlFontSize, p.ttmlColor, p.ttmlPosition
        )

        val uri = Uri.parse(p.audioUri)
        player.stop()
        player.setMediaItem(MediaItem.fromUri(uri))
        player.setPlaybackSpeed(1f)
        player.playWhenReady = false
        player.prepare()

        waveformJob?.cancel()
        _waveform.value = null
        waveformJob = viewModelScope.launch {
            _waveform.value = WaveformExtractor.extract(getApplication(), uri)
        }
        _loaded.value = true
    }

    /** Called when a project is deleted from the dashboard. */
    fun onDeleted(id: Long) {
        if (_meta.value?.id != id) return
        ticker?.cancel()
        waveformJob?.cancel()
        player.stop()
        player.clearMediaItems()
        dirty = false
        _loaded.value = false
        _meta.value = null
        _lines.value = emptyList()
        _waveform.value = null
        _playing.value = false
    }

    private suspend fun persist() {
        val m = _meta.value ?: return
        val ls = _lines.value
        dirty = false
        repo.upsert(
            ProjectEntity(
                id = m.id,
                title = m.title,
                artist = m.artist,
                audioUri = m.audioUri,
                lyricsJson = LyricsJson.encode(ls),
                durationMs = if (_duration.value > 0) _duration.value else m.durationMs,
                updatedAt = System.currentTimeMillis(),
                totalLines = ls.size,
                syncedLines = ls.count { it.startMs >= 0 },
                ttmlFontSize = m.ttmlFontSize,
                ttmlColor = m.ttmlColor,
                ttmlPosition = m.ttmlPosition
            )
        )
    }

    fun save() {
        if (_loaded.value && dirty) viewModelScope.launch { persist() }
    }

    // ---------------------------------------------------------------- playback

    private fun startTicker() {
        ticker?.cancel()
        ticker = viewModelScope.launch {
            while (isActive) {
                _position.value = player.currentPosition
                delay(30)
            }
        }
    }

    fun togglePlay() {
        if (player.isPlaying) {
            player.pause()
        } else {
            if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
            player.play()
        }
    }

    fun pause() {
        player.pause()
    }

    fun seekTo(ms: Long) {
        val d = _duration.value
        val target = if (d > 0) ms.coerceIn(0L, d) else ms.coerceAtLeast(0L)
        player.seekTo(target)
        _position.value = target
    }

    fun seekBy(deltaMs: Long) = seekTo(player.currentPosition + deltaMs)

    fun cycleSpeed() {
        val speeds = listOf(0.5f, 0.75f, 1f, 1.25f)
        val next = speeds[(speeds.indexOf(_speed.value) + 1).mod(speeds.size)]
        _speed.value = next
        player.setPlaybackSpeed(next)
    }

    // ---------------------------------------------------------------- state helpers

    fun setMode(mode: SyncMode) {
        _mode.value = mode
    }

    fun setEstimateWords(value: Boolean) {
        _estimateWords.value = value
    }

    fun setTtmlStyle(fontSize: Int? = null, color: String? = null, position: String? = null) {
        _meta.update {
            it?.copy(
                ttmlFontSize = fontSize ?: it.ttmlFontSize,
                ttmlColor = color ?: it.ttmlColor,
                ttmlPosition = position ?: it.ttmlPosition
            )
        }
        dirty = true
    }

    private fun newId(): Long = nextId++

    private fun mutate(block: (List<LyricLine>) -> List<LyricLine>) {
        val old = _lines.value
        val new = block(old)
        if (new == old) return
        undo.addLast(old)
        if (undo.size > 50) undo.removeFirst()
        _canUndo.value = true
        _lines.value = new
        dirty = true
    }

    fun undo() {
        if (undo.isEmpty()) return
        _lines.value = undo.removeLast()
        _canUndo.value = undo.isNotEmpty()
        _cursor.value = _cursor.value.coerceIn(0, (_lines.value.size - 1).coerceAtLeast(0))
        dirty = true
    }

    // ---------------------------------------------------------------- tap to sync

    fun selectLine(i: Int) {
        val ls = _lines.value
        if (i !in ls.indices) return
        _cursor.value = i
        val s = ls[i].startMs
        if (s >= 0) seekTo(s)
    }

    /** The big TAP button: stamps the current cursor line (or word) at the playhead. */
    fun tap() {
        if (_lines.value.isEmpty()) return
        val t = (player.currentPosition - settings.tapCompMs.value).coerceAtLeast(0L)
        if (_mode.value == SyncMode.LINE) tapLine(t) else tapWord(t)
    }

    private fun tapLine(t: Long) {
        val last = _lines.value.lastIndex
        val i = _cursor.value.coerceIn(0, last)
        mutate { ls ->
            ls.toMutableList().also { it[i] = ls[i].copy(startMs = t, endMs = -1L, words = emptyList()) }
        }
        _cursor.value = (i + 1).coerceAtMost(last)
    }

    private fun tentativeEnd(word: String): Long = (word.length * 90L).coerceIn(250L, 1200L)

    private fun tapWord(t: Long) {
        val ls = _lines.value
        var i = _cursor.value.coerceIn(0, ls.lastIndex)
        while (i < ls.lastIndex && splitWords(ls[i].text).isEmpty()) i++
        val line = ls[i]
        var words = if (line.words.isEmpty()) splitWords(line.text).map { WordTiming(it) } else line.words
        if (words.isEmpty()) return
        if (words.all { it.startMs >= 0 }) words = words.map { WordTiming(it.text) } // restart this line
        val wi = words.indexOfFirst { it.startMs < 0 }
        val ws = words.toMutableList()
        if (wi > 0) {
            val prev = ws[wi - 1]
            ws[wi - 1] = prev.copy(endMs = t.coerceAtLeast(prev.startMs))
        }
        ws[wi] = WordTiming(words[wi].text, t, t + tentativeEnd(words[wi].text))
        val newLine = line.copy(words = ws, startMs = ws[0].startMs, endMs = ws[wi].endMs)

        mutate { cur ->
            val m = cur.toMutableList()
            if (wi == 0 && i > 0) {
                // The previous line's last word must not run past the first word of this line.
                val p = m[i - 1]
                if (p.words.isNotEmpty() && p.words.all { it.startMs >= 0 }) {
                    val lw = p.words.last()
                    val ne = minOf(lw.endMs, t).coerceAtLeast(lw.startMs)
                    val pw = p.words.dropLast(1) + lw.copy(endMs = ne)
                    m[i - 1] = p.copy(words = pw, endMs = ne)
                }
            }
            m[i] = newLine
            m
        }
        _cursor.value = if (wi == ws.lastIndex) (i + 1).coerceAtMost(ls.lastIndex) else i
    }

    /** Row timestamp chip: lock the playhead time into this line. */
    fun stampLine(i: Int) {
        val t = player.currentPosition.coerceAtLeast(0L)
        mutate { ls ->
            if (i !in ls.indices) ls else ls.toMutableList().also { m ->
                val l = m[i]
                m[i] = if (l.startMs >= 0) l.shifted(t - l.startMs)
                else l.copy(startMs = t, endMs = -1L, words = emptyList())
            }
        }
    }

    // ---------------------------------------------------------------- editing tools

    fun nudgeLine(i: Int, deltaMs: Long) = mutate { ls ->
        if (i !in ls.indices || ls[i].startMs < 0) ls
        else ls.toMutableList().also { it[i] = ls[i].shifted(deltaMs) }
    }

    fun editText(i: Int, text: String) {
        val t = text.replace('\n', ' ').trim()
        if (t.isEmpty()) return
        mutate { ls ->
            if (i !in ls.indices || ls[i].text == t) ls
            else ls.toMutableList().also { m ->
                val l = m[i]
                val nw = splitWords(t)
                val keep = l.words.isNotEmpty() && nw.size == l.words.size
                m[i] = l.copy(
                    text = t,
                    words = if (keep) l.words.mapIndexed { k, w -> w.copy(text = nw[k]) } else emptyList()
                )
            }
        }
    }

    fun splitLine(i: Int, first: String, second: String) {
        val a = first.replace('\n', ' ').trim()
        val b = second.replace('\n', ' ').trim()
        if (a.isEmpty() || b.isEmpty()) return
        mutate { ls ->
            if (i !in ls.indices) return@mutate ls
            val l = ls[i]
            val end = effectiveEnd(ls, i)
            val na = splitWords(a).size
            val nb = splitWords(b).size
            val usable = l.words.isNotEmpty() && l.words.all { it.startMs >= 0 } && l.words.size == na + nb
            val lineA: LyricLine
            val lineB: LyricLine
            if (usable) {
                val wa = l.words.take(na)
                val wb = l.words.drop(na)
                lineA = l.copy(text = a, words = wa, endMs = wa.last().endMs)
                lineB = LyricLine(newId(), b, wb.first().startMs, wb.last().endMs, wb)
            } else if (l.startMs >= 0) {
                val ratio = a.length.toDouble() / (a.length + b.length)
                val mid = l.startMs + ((end - l.startMs) * ratio).toLong()
                lineA = l.copy(text = a, words = emptyList(), endMs = mid)
                lineB = LyricLine(newId(), b, mid, l.endMs, emptyList())
            } else {
                lineA = l.copy(text = a, words = emptyList())
                lineB = LyricLine(newId(), b)
            }
            ls.toMutableList().also {
                it[i] = lineA
                it.add(i + 1, lineB)
            }
        }
    }

    fun mergeWithNext(i: Int) = mutate { ls ->
        if (i !in 0 until ls.lastIndex) return@mutate ls
        val a = ls[i]
        val b = ls[i + 1]
        val bothWords = a.words.isNotEmpty() && b.words.isNotEmpty() &&
            (a.words + b.words).all { it.startMs >= 0 }
        val words = if (bothWords) a.words + b.words else emptyList()
        val merged = a.copy(
            text = a.text.trim() + " " + b.text.trim(),
            startMs = if (a.startMs >= 0) a.startMs else b.startMs,
            endMs = when {
                bothWords -> words.last().endMs
                b.endMs >= 0 -> b.endMs
                else -> -1L
            },
            words = words
        )
        ls.toMutableList().also {
            it[i] = merged
            it.removeAt(i + 1)
        }
    }

    fun insertLineBelow(i: Int, text: String) {
        val t = text.replace('\n', ' ').trim()
        if (t.isEmpty()) return
        mutate { ls ->
            val idx = (i + 1).coerceIn(0, ls.size)
            ls.toMutableList().also { it.add(idx, LyricLine(newId(), t)) }
        }
    }

    fun deleteLine(i: Int) {
        mutate { ls -> if (i !in ls.indices) ls else ls.toMutableList().also { it.removeAt(i) } }
        _cursor.value = _cursor.value.coerceIn(0, (_lines.value.size - 1).coerceAtLeast(0))
    }

    fun clearTimestamp(i: Int) = mutate { ls ->
        if (i !in ls.indices) ls
        else ls.toMutableList().also { it[i] = ls[i].copy(startMs = -1L, endMs = -1L, words = emptyList()) }
    }

    fun clearAllTimestamps() {
        mutate { ls -> ls.map { it.copy(startMs = -1L, endMs = -1L, words = emptyList()) } }
        _cursor.value = 0
    }

    /** Shifts every synced line and word by [deltaMs]. */
    fun offsetAll(deltaMs: Long) = mutate { ls ->
        if (deltaMs == 0L) ls else ls.map { if (it.startMs >= 0) it.shifted(deltaMs) else it }
    }

    /** Fills in word timings for synced lines that were only line-synced. Returns how many lines changed. */
    fun estimateWordTimings(): Int {
        var n = 0
        mutate { ls ->
            ls.mapIndexed { i, l ->
                val done = l.words.isNotEmpty() && l.words.all { it.startMs >= 0 }
                if (l.startMs < 0 || done) {
                    l
                } else {
                    n++
                    val end = effectiveEnd(ls, i)
                    l.copy(words = distributeWords(l.text, l.startMs, end), endMs = end)
                }
            }
        }
        return n
    }

    // ---------------------------------------------------------------- preview / verify

    /**
     * Parses [text] exactly like a player would read the file, so what you see is what the file contains.
     */
    fun setPreview(text: String, label: String) {
        val parsed = PreviewParser.parse(text)
        val dur = if (_duration.value > 0) _duration.value else (_meta.value?.durationMs ?: 0L)
        val report = buildReport(parsed.lines, dur)
        val shown = parsed.lines
            .filter { it.startMs >= 0 }
            .sortedBy { it.startMs }
            .mapIndexed { i, l -> l.copy(id = i.toLong()) }
        _preview.value = PreviewData(label, parsed.format, shown, report)
        player.pause()
        seekTo(0L)
    }

    /** Renders the current project (ELARC if it has word timings, else LRC) and opens it as a preview. */
    fun previewCurrent(): Boolean {
        val m = _meta.value ?: return false
        val ls = _lines.value
        if (ls.none { it.startMs >= 0 }) return false
        val hasWords = ls.any { l -> l.words.isNotEmpty() && l.words.all { it.startMs >= 0 } }
        val format = if (hasWords) ExportFormat.ELARC else ExportFormat.LRC
        val dur = if (_duration.value > 0) _duration.value else m.durationMs
        val text = LyricsExporter.render(
            format, m.title, m.artist, dur, ls,
            TtmlStyle(m.ttmlFontSize, m.ttmlColor, m.ttmlPosition), false
        )
        setPreview(text, "${m.title}.${format.ext} (current result)")
        return true
    }

    /** Saves right now, even if nothing changed since the last autosave. */
    fun saveNow() {
        if (_loaded.value) viewModelScope.launch { persist() }
    }

    override fun onCleared() {
        ticker?.cancel()
        waveformJob?.cancel()
        player.release()
        super.onCleared()
    }
}
