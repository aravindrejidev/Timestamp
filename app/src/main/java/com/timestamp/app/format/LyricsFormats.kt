package com.timestamp.app.format

import com.timestamp.app.data.LyricLine
import com.timestamp.app.data.WordTiming
import java.util.Locale

enum class ExportFormat(val label: String, val ext: String) {
    LRC("LRC", "lrc"),
    TTML("TTML", "ttml"),
    ELARC("ELARC", "elrc")
}

data class TtmlStyle(
    val fontSizePx: Int = 24,
    val colorHex: String = "#FFFFFF",
    /** top | center | bottom */
    val position: String = "bottom"
)

object TimeFmt {
    private fun f(fmt: String, vararg a: Any) = String.format(Locale.US, fmt, *a)

    /** mm:ss.xx (LRC) */
    fun lrc(ms: Long): String {
        val cs = (ms.coerceAtLeast(0L) + 5) / 10
        return f("%02d:%02d.%02d", cs / 6000, (cs / 100) % 60, cs % 100)
    }

    /** mm:ss.mmm (UI) */
    fun precise(ms: Long): String {
        val t = ms.coerceAtLeast(0L)
        return f("%02d:%02d.%03d", t / 60000, (t / 1000) % 60, t % 1000)
    }

    /** HH:MM:SS.mmm (TTML) */
    fun ttml(ms: Long): String {
        val t = ms.coerceAtLeast(0L)
        return f("%02d:%02d:%02d.%03d", t / 3600000, (t / 60000) % 60, (t / 1000) % 60, t % 1000)
    }

    /** m:ss */
    fun length(ms: Long): String {
        val t = ms.coerceAtLeast(0L)
        return f("%d:%02d", t / 60000, (t / 1000) % 60)
    }
}

fun splitWords(text: String): List<String> =
    text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }

/** Spreads the words of [text] over [startMs]..[endMs], weighted by word length. */
fun distributeWords(text: String, startMs: Long, endMs: Long): List<WordTiming> {
    val ws = splitWords(text)
    if (ws.isEmpty()) return emptyList()
    val total = ws.sumOf { it.length + 1 }.toDouble()
    val span = (endMs - startMs).coerceAtLeast(ws.size * 100L)
    var acc = 0.0
    return ws.map { w ->
        val s = startMs + (span * acc / total).toLong()
        acc += (w.length + 1).toDouble()
        val e = startMs + (span * acc / total).toLong()
        WordTiming(w, s, e)
    }
}

/** End time of line [index]: explicit end, else next line's start (max 10 s), else start + 4 s. */
fun effectiveEnd(lines: List<LyricLine>, index: Int): Long {
    val l = lines[index]
    if (l.startMs < 0) return -1L
    if (l.endMs > l.startMs) return l.endMs
    for (j in index + 1 until lines.size) {
        if (lines[j].startMs > l.startMs) return minOf(lines[j].startMs, l.startMs + 10_000L)
    }
    return l.startMs + 4_000L
}

/** Real word timings if the line was word-synced, otherwise (optionally) an even estimate. */
fun wordsFor(lines: List<LyricLine>, index: Int, estimate: Boolean): List<WordTiming> {
    val l = lines[index]
    if (l.words.isNotEmpty() && l.words.all { it.startMs >= 0 }) return l.words
    if (!estimate || l.startMs < 0) return emptyList()
    return distributeWords(l.text, l.startMs, effectiveEnd(lines, index))
}

object LyricsParser {
    private val leadingTime = Regex("^\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?\\]")
    private val metaTag = Regex("^\\[[A-Za-z]{2,}:.*\\]\\s*$")
    private val wordTag = Regex("<(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?>")

    private fun toMs(min: String, sec: String, frac: String): Long {
        val f = if (frac.isEmpty()) 0L else frac.padEnd(3, '0').take(3).toLong()
        return min.toLong() * 60_000L + sec.toLong() * 1000L + f
    }

    /** Parses plain text, LRC and Enhanced LRC. Lines without a time tag stay unsynced. */
    fun parse(raw: String): List<LyricLine> {
        val out = ArrayList<LyricLine>()
        for (rawLine in raw.lines()) {
            var line = rawLine.trim()
            if (line.isEmpty() || metaTag.matches(line)) continue
            val times = ArrayList<Long>()
            while (true) {
                val m = leadingTime.find(line) ?: break
                times.add(toMs(m.groupValues[1], m.groupValues[2], m.groupValues[3]))
                line = line.substring(m.value.length)
            }
            if (times.isEmpty()) {
                out.add(LyricLine(0, line))
                continue
            }
            val (text, words) = parseWords(line)
            if (text.isBlank()) continue
            if (words.isNotEmpty()) {
                out.add(LyricLine(0, text, times.first(), words.last().endMs, words))
            } else {
                times.forEach { out.add(LyricLine(0, text, it)) }
            }
        }
        return out.mapIndexed { i, l -> l.copy(id = i.toLong()) }
    }

    private fun parseWords(rest: String): Pair<String, List<WordTiming>> {
        val tags = wordTag.findAll(rest).toList()
        if (tags.isEmpty()) return rest.trim() to emptyList()
        val segs = tags.mapIndexed { i, m ->
            val t = toMs(m.groupValues[1], m.groupValues[2], m.groupValues[3])
            val end = if (i + 1 < tags.size) tags[i + 1].range.first else rest.length
            t to rest.substring(m.range.last + 1, end)
        }
        val words = ArrayList<WordTiming>()
        segs.forEachIndexed { i, (t, s) ->
            val text = s.trim()
            if (text.isNotEmpty()) {
                val e = if (i + 1 < segs.size) segs[i + 1].first else t + 500L
                words.add(WordTiming(text, t, e))
            }
        }
        if (words.isEmpty()) return rest.replace(wordTag, "").trim() to emptyList()
        return words.joinToString(" ") { it.text } to words
    }
}

object LyricsExporter {
    fun render(
        format: ExportFormat,
        title: String,
        artist: String,
        durationMs: Long,
        lines: List<LyricLine>,
        style: TtmlStyle,
        estimateWords: Boolean
    ): String {
        val synced = lines.filter { it.startMs >= 0 && it.text.isNotBlank() }.sortedBy { it.startMs }
        return when (format) {
            ExportFormat.LRC -> toLrc(title, artist, durationMs, synced)
            ExportFormat.ELARC -> toElarc(title, artist, durationMs, synced, estimateWords)
            ExportFormat.TTML -> toTtml(title, artist, synced, style, estimateWords)
        }
    }

    private fun header(sb: StringBuilder, title: String, artist: String, durationMs: Long) {
        if (title.isNotBlank()) sb.append("[ti:").append(title.trim()).append("]\n")
        if (artist.isNotBlank()) sb.append("[ar:").append(artist.trim()).append("]\n")
        if (durationMs > 0) sb.append("[length:").append(TimeFmt.length(durationMs)).append("]\n")
        sb.append("[by:Timestamp]\n")
    }

    private fun toLrc(title: String, artist: String, durationMs: Long, synced: List<LyricLine>): String {
        val sb = StringBuilder()
        header(sb, title, artist, durationMs)
        synced.forEach { l ->
            sb.append('[').append(TimeFmt.lrc(l.startMs)).append(']').append(l.text.trim()).append('\n')
        }
        return sb.toString()
    }

    /** Enhanced LRC: [mm:ss.xx]<mm:ss.xx>word <mm:ss.xx>word <mm:ss.xx>  (last tag = end of line). */
    private fun toElarc(
        title: String, artist: String, durationMs: Long,
        synced: List<LyricLine>, estimate: Boolean
    ): String {
        val sb = StringBuilder()
        header(sb, title, artist, durationMs)
        synced.forEachIndexed { i, l ->
            val words = wordsFor(synced, i, estimate)
            sb.append('[').append(TimeFmt.lrc(l.startMs)).append(']')
            if (words.isEmpty()) {
                sb.append(l.text.trim())
            } else {
                sb.append(words.joinToString(" ") { "<${TimeFmt.lrc(it.startMs)}>${it.text}" })
                sb.append(" <").append(TimeFmt.lrc(words.last().endMs)).append('>')
            }
            sb.append('\n')
        }
        return sb.toString()
    }

    private fun esc(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    private fun toTtml(
        title: String, artist: String,
        synced: List<LyricLine>, style: TtmlStyle, estimate: Boolean
    ): String {
        val (origin, extent, align) = when (style.position) {
            "top" -> Triple("10% 6%", "80% 22%", "before")
            "center" -> Triple("10% 39%", "80% 22%", "center")
            else -> Triple("10% 72%", "80% 22%", "after")
        }
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<tt xmlns=\"http://www.w3.org/ns/ttml\" xmlns:tts=\"http://www.w3.org/ns/ttml#styling\" ")
        sb.append("xmlns:ttm=\"http://www.w3.org/ns/ttml#metadata\" xmlns:ttp=\"http://www.w3.org/ns/ttml#parameter\" ")
        sb.append("ttp:timeBase=\"media\" xml:lang=\"en\">\n")
        sb.append("  <head>\n    <metadata>\n")
        if (title.isNotBlank()) sb.append("      <ttm:title>").append(esc(title.trim())).append("</ttm:title>\n")
        if (artist.isNotBlank()) sb.append("      <ttm:desc>").append(esc(artist.trim())).append("</ttm:desc>\n")
        sb.append("    </metadata>\n    <styling>\n")
        sb.append("      <style xml:id=\"s1\" tts:fontSize=\"${style.fontSizePx}px\" tts:color=\"${style.colorHex}\" ")
        sb.append("tts:fontFamily=\"sansSerif\" tts:textAlign=\"center\"/>\n")
        sb.append("    </styling>\n    <layout>\n")
        sb.append("      <region xml:id=\"r1\" tts:origin=\"$origin\" tts:extent=\"$extent\" tts:displayAlign=\"$align\"/>\n")
        sb.append("    </layout>\n  </head>\n  <body>\n    <div>\n")
        synced.forEachIndexed { i, l ->
            val end = effectiveEnd(synced, i)
            val words = wordsFor(synced, i, estimate)
            sb.append("      <p begin=\"").append(TimeFmt.ttml(l.startMs))
                .append("\" end=\"").append(TimeFmt.ttml(end))
                .append("\" region=\"r1\" style=\"s1\">")
            if (words.isEmpty()) {
                sb.append(esc(l.text.trim()))
            } else {
                sb.append(words.joinToString(" ") {
                    "<span begin=\"${TimeFmt.ttml(it.startMs)}\" end=\"${TimeFmt.ttml(it.endMs)}\">${esc(it.text)}</span>"
                })
            }
            sb.append("</p>\n")
        }
        sb.append("    </div>\n  </body>\n</tt>\n")
        return sb.toString()
    }
}
