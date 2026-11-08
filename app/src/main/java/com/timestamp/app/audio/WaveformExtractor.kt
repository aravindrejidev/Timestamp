package com.timestamp.app.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.pow

class Waveform(val peaks: FloatArray, val peaksPerSecond: Int)

/** Decodes the audio track (MP3 / WAV / FLAC / ...) with MediaCodec and builds a peak envelope. */
object WaveformExtractor {

    suspend fun extract(context: Context, uri: Uri, peaksPerSecond: Int = 50): Waveform? =
        withContext(Dispatchers.Default) {
            val extractor = MediaExtractor()
            var codec: MediaCodec? = null
            try {
                extractor.setDataSource(context, uri, null)
                var track = -1
                var format: MediaFormat? = null
                for (i in 0 until extractor.trackCount) {
                    val f = extractor.getTrackFormat(i)
                    if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                        track = i
                        format = f
                        break
                    }
                }
                if (track < 0 || format == null) return@withContext null
                extractor.selectTrack(track)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: return@withContext null
                var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

                val dec = MediaCodec.createDecoderByType(mime)
                codec = dec
                dec.configure(format, null, null, 0)
                dec.start()

                val info = MediaCodec.BufferInfo()
                val peaks = ArrayList<Float>()
                var curMax = 0f
                var frames = 0
                var inputDone = false
                var outputDone = false

                while (!outputDone) {
                    ensureActive()
                    if (!inputDone) {
                        val inIndex = dec.dequeueInputBuffer(5_000)
                        if (inIndex >= 0) {
                            val inBuf = dec.getInputBuffer(inIndex)
                            val size = if (inBuf != null) extractor.readSampleData(inBuf, 0) else -1
                            if (size < 0) {
                                dec.queueInputBuffer(inIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                dec.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                    val outIndex = dec.dequeueOutputBuffer(info, 5_000)
                    if (outIndex >= 0) {
                        val buf = dec.getOutputBuffer(outIndex)
                        if (buf != null && info.size > 0) {
                            buf.position(info.offset)
                            buf.limit(info.offset + info.size)
                            val shorts = buf.order(ByteOrder.nativeOrder()).asShortBuffer()
                            val perBucket = (sampleRate / peaksPerSecond).coerceAtLeast(1)
                            val ch = channels.coerceAtLeast(1)
                            val total = shorts.remaining()
                            var idx = 0
                            while (idx + ch <= total) {
                                var m = 0
                                for (c in 0 until ch) {
                                    val v = abs(shorts.get(idx + c).toInt())
                                    if (v > m) m = v
                                }
                                if (m > curMax) curMax = m.toFloat()
                                frames++
                                if (frames >= perBucket) {
                                    peaks.add(curMax / 32768f)
                                    curMax = 0f
                                    frames = 0
                                }
                                idx += ch
                            }
                        }
                        dec.releaseOutputBuffer(outIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    } else if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        val nf = dec.outputFormat
                        sampleRate = nf.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = nf.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                }

                if (peaks.isEmpty()) return@withContext null
                val arr = peaks.toFloatArray()
                val max = arr.max()
                if (max > 0f) {
                    for (i in arr.indices) {
                        arr[i] = (arr[i] / max).toDouble().pow(0.7).toFloat()
                    }
                }
                Waveform(arr, peaksPerSecond)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            } finally {
                try { codec?.stop() } catch (_: Exception) {}
                try { codec?.release() } catch (_: Exception) {}
                extractor.release()
            }
        }
}
