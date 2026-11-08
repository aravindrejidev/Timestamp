package com.timestamp.app.data

import org.json.JSONArray
import org.json.JSONObject

/** One word with its own timing (used for ELARC / TTML word-level sync). -1 means "not stamped yet". */
data class WordTiming(
    val text: String,
    val startMs: Long = -1L,
    val endMs: Long = -1L
)

data class LyricLine(
    val id: Long,
    val text: String,
    val startMs: Long = -1L,
    /** -1 means "auto": ends where the next synced line starts. */
    val endMs: Long = -1L,
    val words: List<WordTiming> = emptyList()
) {
    val isSynced: Boolean get() = startMs >= 0

    /** Moves the line and all of its word timings by [delta] ms. */
    fun shifted(delta: Long): LyricLine {
        fun s(v: Long) = if (v >= 0) (v + delta).coerceAtLeast(0L) else v
        return copy(
            startMs = s(startMs),
            endMs = s(endMs),
            words = words.map { it.copy(startMs = s(it.startMs), endMs = s(it.endMs)) }
        )
    }
}

enum class SyncMode { LINE, WORD }

/** Compact JSON used to store lyrics inside the Room database. */
object LyricsJson {
    fun encode(lines: List<LyricLine>): String {
        val arr = JSONArray()
        lines.forEach { l ->
            val words = JSONArray()
            l.words.forEach { w ->
                words.put(JSONObject().put("t", w.text).put("s", w.startMs).put("e", w.endMs))
            }
            arr.put(
                JSONObject()
                    .put("t", l.text)
                    .put("s", l.startMs)
                    .put("e", l.endMs)
                    .put("w", words)
            )
        }
        return arr.toString()
    }

    fun decode(json: String): List<LyricLine> {
        val arr = JSONArray(json)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val wa = o.optJSONArray("w")
            val words = if (wa == null) emptyList() else (0 until wa.length()).map { j ->
                val w = wa.getJSONObject(j)
                WordTiming(w.getString("t"), w.optLong("s", -1L), w.optLong("e", -1L))
            }
            LyricLine(i.toLong(), o.getString("t"), o.optLong("s", -1L), o.optLong("e", -1L), words)
        }
    }
}
