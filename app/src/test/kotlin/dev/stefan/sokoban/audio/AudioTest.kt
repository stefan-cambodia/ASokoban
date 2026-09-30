package dev.stefan.sokoban.audio

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

class AudioTest {

    @Test
    fun `every effect renders an audible, safe, click-free sound`() {
        for (effect in SoundEffect.entries) {
            val samples = SoundSynth.render(effect)
            assertTrue(samples.size > SoundSynth.SAMPLE_RATE / 50, "$effect too short")
            assertTrue(samples.none { it.isNaN() || it.isInfinite() }, "$effect has invalid samples")
            val peak = SoundSynth.peak(samples)
            assertTrue(peak in 0.02f..0.9f, "$effect peak $peak")
            // Faded in and out: no click at either end.
            assertTrue(abs(samples.first()) < 0.05f, "$effect starts abruptly")
            assertTrue(abs(samples.last()) < 0.01f, "$effect ends abruptly")
        }
    }

    @Test
    fun `step is the quietest sound and victory the longest`() {
        val peaks = SoundEffect.entries.associateWith { SoundSynth.peak(SoundSynth.render(it)) }
        assertTrue(peaks.getValue(SoundEffect.STEP) <= peaks.values.min() + 0.02f)
        val lengths = SoundEffect.entries.associateWith { SoundSynth.render(it).size }
        assertEquals(SoundEffect.VICTORY, lengths.maxBy { it.value }.key)
    }

    private fun wav(samples: ShortArray, rate: Int, channels: Int): ByteArray {
        val data = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        samples.forEach { data.putShort(it) }
        val out = ByteArrayOutputStream()
        fun int(v: Int) = out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array())
        fun short(v: Int) = out.write(ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(v.toShort()).array())
        out.write("RIFF".toByteArray()); int(36 + samples.size * 2); out.write("WAVE".toByteArray())
        out.write("fmt ".toByteArray()); int(16); short(1); short(channels); int(rate)
        int(rate * channels * 2); short(channels * 2); short(16)
        out.write("data".toByteArray()); int(samples.size * 2); out.write(data.array())
        return out.toByteArray()
    }

    @Test
    fun `decodes mono PCM at the output rate`() {
        val decoded = requireNotNull(WavDecoder.decode(wav(shortArrayOf(0, 16384, -16384, 32767), 44_100, 1), 44_100))
        assertEquals(4, decoded.size)
        assertEquals(0.5f, decoded[1], 1e-4f)
        assertEquals(-0.5f, decoded[2], 1e-4f)
    }

    @Test
    fun `downmixes stereo and resamples`() {
        val stereo = shortArrayOf(16384, 0, 16384, 0, 16384, 0, 16384, 0)
        val decoded = requireNotNull(WavDecoder.decode(wav(stereo, 22_050, 2), 44_100))
        assertEquals(8, decoded.size)
        decoded.forEach { assertEquals(0.25f, it, 1e-3f) }
    }

    @Test
    fun `rejects files that are not PCM WAV`() {
        assertNull(WavDecoder.decode(ByteArray(0), 44_100))
        assertNull(WavDecoder.decode("definitely not audio".toByteArray(), 44_100))
        val compressed = wav(shortArrayOf(1, 2), 44_100, 1).also { it[20] = 3 }
        assertNull(WavDecoder.decode(compressed, 44_100))
    }
}
