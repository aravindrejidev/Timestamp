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
    private val singerPrefix = Regex("^(?:v([12])\\s*:|\\[v([12])\\])\\s*", RegexOption.IGNORE_CASE)

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
            var singer = 1
            val sm = singerPrefix.find(line)
            if (sm != null) {
                singer = (sm.groupValues[1].ifEmpty { sm.groupValues[2] }).toIntOrNull() ?: 1
                line = line.substring(sm.value.length)
            }
            if (times.isEmpty()) {
                out.add(LyricLine(0, line, singer = singer))
                continue
            }
            val (text, words) = parseWords(line)
            if (text.isBlank()) continue
            if (words.isNotEmpty()) {
                out.add(LyricLine(0, text, times.first(), words.last().endMs, words, singer))
            } else {
                times.forEach { out.add(LyricLine(0, text, it, singer = singer)) }
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
        estimateWords: Boolean,
        singerTags: Boolean = false
    ): String {
        val synced = lines.filter { it.startMs >= 0 && it.text.isNotBlank() }.sortedBy { it.startMs }
        return when (format) {
            ExportFormat.LRC -> toLrc(title, artist, durationMs, synced, singerTags)
            ExportFormat.ELARC -> toElarc(title, artist, durationMs, synced, estimateWords, singerTags)
            ExportFormat.TTML -> toTtml(title, artist, synced, style, estimateWords)
        }
    }

    private fun header(sb: StringBuilder, title: String, artist: String, durationMs: Long) {
        if (title.isNotBlank()) sb.append("[ti:").append(title.trim()).append("]\n")
        if (artist.isNotBlank()) sb.append("[ar:").append(artist.trim()).append("]\n")
        if (durationMs > 0) sb.append("[length:").append(TimeFmt.length(durationMs)).append("]\n")
        sb.append("[by:Timestamp]\n")
    }

    private fun singerTag(l: LyricLine, on: Boolean): String =
        if (on) "v${l.singer.coerceIn(1, 2)}: " else ""

    private fun toLrc(
        title: String, artist: String, durationMs: Long,
        synced: List<LyricLine>, singerTags: Boolean
    ): String {
        val sb = StringBuilder()
        header(sb, title, artist, durationMs)
        synced.forEach { l ->
            sb.append('[').append(TimeFmt.lrc(l.startMs)).append(']')
                .append(singerTag(l, singerTags)).append(l.text.trim()).append('\n')
        }
        return sb.toString()
    }

    /** Enhanced LRC: [mm:ss.xx]<mm:ss.xx>word <mm:ss.xx>word <mm:ss.xx>  (last tag = end of line). */
    private fun toElarc(
        title: String, artist: String, durationMs: Long,
        synced: List<LyricLine>, estimate: Boolean, singerTags: Boolean
    ): String {
        val sb = StringBuilder()
        header(sb, title, artist, durationMs)
        synced.forEachIndexed { i, l ->
            val words = wordsFor(synced, i, estimate)
            sb.append('[').append(TimeFmt.lrc(l.startMs)).append(']').append(singerTag(l, singerTags))
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

    /**
     * TTML. With two singers each line carries ttm:agent="v1" / "v2" (declared in the head),
     * and singer 2 lines are right-aligned, like duets in Apple Music.
     */
    private fun toTtml(
        title: String, artist: String,
        synced: List<LyricLine>, style: TtmlStyle, estimate: Boolean
    ): String {
        val duet = synced.any { it.singer == 2 }
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
        if (duet) {
            sb.append("      <ttm:agent type=\"person\" xml:id=\"v1\"><ttm:name type=\"full\">Singer 1</ttm:name></ttm:agent>\n")
            sb.append("      <ttm:agent type=\"person\" xml:id=\"v2\"><ttm:name type=\"full\">Singer 2</ttm:name></ttm:agent>\n")
        }
        sb.append("    </metadata>\n    <styling>\n")
        sb.append("      <style xml:id=\"s1\" tts:fontSize=\"${style.fontSizePx}px\" tts:color=\"${style.colorHex}\" ")
        sb.append("tts:fontFamily=\"sansSerif\" tts:textAlign=\"center\"/>\n")
        if (duet) sb.append("      <style xml:id=\"s2\" style=\"s1\" tts:textAlign=\"end\"/>\n")
        sb.append("    </styling>\n    <layout>\n")
        sb.append("      <region xml:id=\"r1\" tts:origin=\"$origin\" tts:extent=\"$extent\" tts:displayAlign=\"$align\"/>\n")
        sb.append("    </layout>\n  </head>\n  <body>\n    <div>\n")
        synced.forEachIndexed { i, l ->
            val end = effectiveEnd(synced, i)
            val words = wordsFor(synced, i, estimate)
            val styleId = if (duet && l.singer == 2) "s2" else "s1"
            sb.append("      <p begin=\"").append(TimeFmt.ttml(l.startMs))
                .append("\" end=\"").append(TimeFmt.ttml(end))
                .append("\" region=\"r1\" style=\"").append(styleId).append('"')
            if (duet) sb.append(" ttm:agent=\"v").append(l.singer.coerceIn(1, 2)).append('"')
            sb.append('>')
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

/** Index of the line that is "current" at [posMs] (last line whose start has passed), or -1. */
fun currentLineIndex(lines: List<LyricLine>, posMs: Long): Int {
    var best = -1
    var bestStart = -1L
    lines.forEachIndexed { i, l ->
        if (l.startMs in 0..posMs && l.startMs >= bestStart) {
            best = i
            bestStart = l.startMs
        }
    }
    return best
}

data class ParsedFile(val format: String, val lines: List<LyricLine>)

/** Reads a TTML file back into lines (and word spans when present). */
object TtmlParser {
    private val pTag = Regex("<p\\b([^>]*)>(.*?)</p>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val spanTag = Regex("<span\\b([^>]*)>(.*?)</span>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val anyTag = Regex("<[^>]+>")

    private fun attr(attrs: String, name: String): String? =
        Regex("\\b" + name + "\\s*=\\s*\"([^\"]*)\"").find(attrs)?.groupValues?.get(1)

    private fun unescape(s: String): String = s
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&amp;", "&")

    private fun time(raw: String?): Long? {
        val s = raw?.trim() ?: return null
        if (s.endsWith("s") && !s.contains(':')) {
            return s.dropLast(1).toDoubleOrNull()?.let { Math.round(it * 1000.0) }
        }
        val parts = s.split(':')
        if (parts.size !in 2..3) return null
        val secs = parts.last().toDoubleOrNull() ?: return null
        val mins = parts[parts.size - 2].toLongOrNull() ?: return null
        val hours = if (parts.size == 3) (parts[0].toLongOrNull() ?: return null) else 0L
        return Math.round((hours * 3600.0 + mins * 60.0 + secs) * 1000.0)
    }

    private val agentTag = Regex("<ttm:agent\\b[^>]*>", RegexOption.IGNORE_CASE)

    fun parse(xml: String): List<LyricLine> {
        val out = ArrayList<LyricLine>()
        val agents = agentTag.findAll(xml).mapNotNull { attr(it.value, "xml:id") }.toList()
        for (m in pTag.findAll(xml)) {
            val attrs = m.groupValues[1]
            val inner = m.groupValues[2]
            val start = time(attr(attrs, "begin")) ?: continue
            val end = time(attr(attrs, "end")) ?: -1L
            val words = spanTag.findAll(inner).mapNotNull { sm ->
                val ws = time(attr(sm.groupValues[1], "begin"))
                val we = time(attr(sm.groupValues[1], "end"))
                val txt = unescape(anyTag.replace(sm.groupValues[2], "")).trim()
                if (ws == null || txt.isEmpty()) null else WordTiming(txt, ws, we ?: (ws + 300L))
            }.toList()
            val text = if (words.isNotEmpty()) {
                words.joinToString(" ") { it.text }
            } else {
                unescape(anyTag.replace(inner, "")).trim()
            }
            if (text.isBlank()) continue
            val agent = attr(attrs, "ttm:agent")
            val singer = when {
                agent == null -> 1
                agents.indexOf(agent) >= 0 -> minOf(agents.indexOf(agent) + 1, 2)
                else -> if (Regex("(\\d+)$").find(agent)?.value?.toIntOrNull() == 2) 2 else 1
            }
            out.add(LyricLine(0, text, start, end, words, singer))
        }
        return out.mapIndexed { i, l -> l.copy(id = i.toLong()) }
    }
}

/** Detects LRC / ELARC / TTML from the text itself, so a saved file can be checked as-is. */
object PreviewParser {
    fun parse(text: String): ParsedFile {
        val t = text.trimStart('\uFEFF')
        return if (t.contains("<tt", ignoreCase = true) && t.contains("<p", ignoreCase = true)) {
            ParsedFile("TTML", TtmlParser.parse(t))
        } else {
            val lines = LyricsParser.parse(t)
            val enhanced = lines.any { it.words.isNotEmpty() }
            ParsedFile(if (enhanced) "ELARC" else "LRC", lines)
        }
    }
}

data class PreviewReport(
    val synced: Int,
    val unsynced: Int,
    val withWords: Int,
    val outOfOrder: Int,
    val overlaps: Int,
    val longGaps: Int,
    val beyondAudio: Int
)

/** Sanity checks on a parsed file, in file order. */
fun buildReport(lines: List<LyricLine>, durationMs: Long): PreviewReport {
    val synced = lines.filter { it.startMs >= 0 }
    val sorted = synced.sortedBy { it.startMs }
    var outOfOrder = 0
    for (i in 1 until synced.size) {
        if (synced[i].startMs < synced[i - 1].startMs) outOfOrder++
    }
    var overlaps = 0
    var gaps = 0
    for (i in 0 until sorted.size - 1) {
        val l = sorted[i]
        val n = sorted[i + 1]
        if (l.endMs >= 0 && l.endMs > n.startMs + 50L) overlaps++
        val prevEnd = if (l.endMs > l.startMs) l.endMs else l.startMs
        if (n.startMs - prevEnd > 15_000L) gaps++
    }
    val beyond = if (durationMs > 0) synced.count { it.startMs > durationMs } else 0
    return PreviewReport(
        synced = synced.size,
        unsynced = lines.size - synced.size,
        withWords = synced.count { it.words.isNotEmpty() },
        outOfOrder = outOfOrder,
        overlaps = overlaps,
        longGaps = gaps,
        beyondAudio = beyond
    )
}
