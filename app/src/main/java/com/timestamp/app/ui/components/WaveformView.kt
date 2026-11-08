package com.timestamp.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.timestamp.app.audio.Waveform
import com.timestamp.app.ui.theme.TsColors

/**
 * Scrolling waveform: the playhead stays in the centre, the audio moves underneath it.
 * Drag horizontally to scrub with millisecond precision. White ticks mark synced line starts.
 */
@Composable
fun WaveformView(
    waveform: Waveform?,
    positionMs: Long,
    durationMs: Long,
    markers: List<Long>,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val windowMs = 8000f
    val pos by rememberUpdatedState(positionMs)
    val dur by rememberUpdatedState(durationMs)
    val seek by rememberUpdatedState(onSeek)

    Canvas(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0x33000000))
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, dragAmount ->
                    change.consume()
                    val msPerPx = windowMs / size.width
                    val target = (pos - dragAmount * msPerPx).toLong()
                    val max = if (dur > 0) dur else Long.MAX_VALUE
                    seek(target.coerceIn(0L, max))
                }
            }
    ) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val barW = 3.dp.toPx()
        val step = 5.dp.toPx()
        val msPerPx = windowMs / w
        val pps = waveform?.peaksPerSecond ?: 50
        val peaks = waveform?.peaks

        var x = 0f
        while (x < w) {
            val t0 = positionMs + (x - cx) * msPerPx
            val t1 = t0 + step * msPerPx
            var peak = 0.03f
            if (peaks != null && t1 >= 0f) {
                val i0 = (t0 / 1000f * pps).toInt().coerceAtLeast(0)
                val i1 = (t1 / 1000f * pps).toInt().coerceAtMost(peaks.size - 1)
                var m = 0f
                var i = i0
                while (i <= i1) {
                    if (peaks[i] > m) m = peaks[i]
                    i++
                }
                if (i1 >= i0) peak = maxOf(peak, m)
            }
            val barH = maxOf(4.dp.toPx(), peak * h * 0.92f)
            drawRoundRect(
                color = if (x + barW / 2f < cx) TsColors.Neon else Color(0xFF3C3C40),
                topLeft = Offset(x, (h - barH) / 2f),
                size = Size(barW, barH),
                cornerRadius = CornerRadius(barW / 2f)
            )
            x += step
        }

        markers.forEach { m ->
            val mx = cx + (m - positionMs) / msPerPx
            if (mx in 0f..w) {
                drawLine(
                    Color.White.copy(alpha = 0.45f),
                    Offset(mx, h * 0.1f), Offset(mx, h * 0.9f),
                    strokeWidth = 1.5.dp.toPx()
                )
            }
        }

        drawLine(TsColors.Neon.copy(alpha = 0.25f), Offset(cx, 0f), Offset(cx, h), strokeWidth = 8.dp.toPx())
        drawLine(TsColors.Neon, Offset(cx, 0f), Offset(cx, h), strokeWidth = 2.dp.toPx())
    }
}
