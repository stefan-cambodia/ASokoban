package dev.stefan.sokoban.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Minimal decoder for PCM WAV files, so recorded sounds can replace the
 * synthesized ones without code changes. Accepts 8/16-bit PCM, mono or
 * stereo, at any sample rate; the result is mono floats at [targetRate].
 */
object WavDecoder {

    /** Returns null for anything that is not a PCM WAV file this decoder supports. */
    fun decode(bytes: ByteArray, targetRate: Int): FloatArray? {
        if (bytes.size < 12) return null
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        if (buffer.tag(0) != "RIFF" || buffer.tag(8) != "WAVE") return null

        var channels = 0
        var sampleRate = 0
        var bits = 0
        var dataOffset = -1
        var dataSize = 0
        var offset = 12
        while (offset + 8 <= bytes.size) {
            val id = buffer.tag(offset)
            val size = buffer.getInt(offset + 4)
            if (size < 0) return null
            val body = offset + 8
            when (id) {
                "fmt " -> {
                    if (size < 16 || body + 16 > bytes.size) return null
                    if (buffer.getShort(body).toInt() != PCM_FORMAT) return null
                    channels = buffer.getShort(body + 2).toInt()
                    sampleRate = buffer.getInt(body + 4)
                    bits = buffer.getShort(body + 14).toInt()
                }
                "data" -> {
                    dataOffset = body
                    dataSize = minOf(size, bytes.size - body)
                }
            }
            // Chunks are padded to an even size.
            offset = body + size + (size and 1)
        }
        if (dataOffset < 0 || channels !in 1..2 || sampleRate <= 0 || bits !in setOf(8, 16)) return null

        val bytesPerFrame = channels * bits / 8
        val frames = dataSize / bytesPerFrame
        val mono = FloatArray(frames) { frame ->
            var sum = 0f
            for (channel in 0 until channels) {
                val at = dataOffset + frame * bytesPerFrame + channel * bits / 8
                sum += if (bits == 16) {
                    buffer.getShort(at) / 32768f
                } else {
                    ((bytes[at].toInt() and 0xFF) - 128) / 128f
                }
            }
            sum / channels
        }
        return resample(mono, sampleRate, targetRate)
    }

    /** Linear-interpolation resampling: plenty for short effects. */
    internal fun resample(samples: FloatArray, from: Int, to: Int): FloatArray {
        if (from == to || samples.isEmpty()) return samples
        val length = (samples.size.toLong() * to / from).toInt().coerceAtLeast(1)
        val step = from.toDouble() / to
        return FloatArray(length) { i ->
            val position = i * step
            val index = position.toInt().coerceAtMost(samples.lastIndex)
            val next = (index + 1).coerceAtMost(samples.lastIndex)
            val fraction = (position - index).toFloat()
            samples[index] + (samples[next] - samples[index]) * fraction
        }
    }

    private fun ByteBuffer.tag(at: Int): String =
        String(ByteArray(4) { get(at + it) }, Charsets.US_ASCII)

    private const val PCM_FORMAT = 1
}
