package com.timestamp.app.data

import org.json.JSONArray
import org.json.JSONObject

/** One word with its own timing (used for ELARC / TTML word-level sync). -1 means "not stamped yet". */
data class WordTiming(
    val text: String,
    val startMs: Long = -1L,
    val endMs: Long = -1L
)

/**
 * A background vocal (ad-lib, backing line). It belongs to one lyric line and is shown, in smaller
 * text under that line, only while the line is being sung.
 */
data class BgVocal(
    val text: String,
    /** -1 means "not timed": it appears together with its line. */
    val startMs: Long = -1L,
    val endMs: Long = -1L,
    val words: List<WordTiming> = emptyList()
) {
    val isTimed: Boolean get() = startMs >= 0

    fun shifted(delta: Long): BgVocal {
        fun s(v: Long) = if (v >= 0) (v + delta).coerceAtLeast(0L) else v
        return copy(
            startMs = s(startMs),
            endMs = s(endMs),
            words = words.map { it.copy(startMs = s(it.startMs), endMs = s(it.endMs)) }
        )
    }

    fun untimed(): BgVocal = copy(startMs = -1L, endMs = -1L, words = emptyList())
}

data class LyricLine(
    val id: Long,
    val text: String,
    val startMs: Long = -1L,
    /** -1 means "auto": ends where the next synced line starts. */
    val endMs: Long = -1L,
    val words: List<WordTiming> = emptyList(),
    /** 1 or 2: which singer sings the line (duets). */
    val singer: Int = 1,
    /** Optional background vocal shown under the line while it is sung. */
    val bg: BgVocal? = null
) {
    val isSynced: Boolean get() = startMs >= 0

    /** Moves the line and all of its word timings (and its background vocal) by [delta] ms. */
    fun shifted(delta: Long): LyricLine {
        fun s(v: Long) = if (v >= 0) (v + delta).coerceAtLeast(0L) else v
        return copy(
            startMs = s(startMs),
            endMs = s(endMs),
            words = words.map { it.copy(startMs = s(it.startMs), endMs = s(it.endMs)) },
            bg = bg?.shifted(delta)
        )
    }
}

enum class SyncMode { LINE, WORD }

/** Compact JSON used to store lyrics inside the Room database. */
object LyricsJson {
    private fun encodeWords(words: List<WordTiming>): JSONArray {
        val arr = JSONArray()
        words.forEach { w ->
            arr.put(JSONObject().put("t", w.text).put("s", w.startMs).put("e", w.endMs))
        }
        return arr
    }

    private fun decodeWords(wa: JSONArray?): List<WordTiming> =
        if (wa == null) emptyList() else (0 until wa.length()).map { j ->
            val w = wa.getJSONObject(j)
            WordTiming(w.getString("t"), w.optLong("s", -1L), w.optLong("e", -1L))
        }

    fun encode(lines: List<LyricLine>): String {
        val arr = JSONArray()
        lines.forEach { l ->
            val o = JSONObject()
                .put("t", l.text)
                .put("s", l.startMs)
                .put("e", l.endMs)
                .put("w", encodeWords(l.words))
                .put("g", l.singer)
            l.bg?.let { b ->
                o.put(
                    "b",
                    JSONObject().put("t", b.text).put("s", b.startMs).put("e", b.endMs).put("w", encodeWords(b.words))
                )
            }
            arr.put(o)
        }
        return arr.toString()
    }

    fun decode(json: String): List<LyricLine> {
        val arr = JSONArray(json)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val bo = o.optJSONObject("b")
            val bg = if (bo == null) null else BgVocal(
                bo.getString("t"), bo.optLong("s", -1L), bo.optLong("e", -1L), decodeWords(bo.optJSONArray("w"))
            )
            LyricLine(
                i.toLong(), o.getString("t"), o.optLong("s", -1L), o.optLong("e", -1L),
                decodeWords(o.optJSONArray("w")), o.optInt("g", 1), bg
            )
        }
    }
}
