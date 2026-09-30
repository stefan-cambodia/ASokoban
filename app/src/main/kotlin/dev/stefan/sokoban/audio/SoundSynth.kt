package dev.stefan.sokoban.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.exp
import kotlin.math.sin

/** Every sound the game can play. [assetName] is the file that may override it. */
enum class SoundEffect(val assetName: String) {
    BUTTON("button"),
    STEP("step"),
    PUSH("push"),
    GOAL("goal"),
    BUMP("bump"),
    UNDO("undo"),
    RESTART("restart"),
    STAR("star"),
    UNLOCK("unlock"),
    VICTORY("victory"),
}

/**
 * Procedural sound design: every effect is computed, sample by sample.
 *
 * The palette is deliberately soft — vibraphone-like bells, muted wooden
 * thuds, barely-there steps — because these sounds repeat hundreds of times per
 * level and must never tire the ear.
 *
 * Pure Kotlin, no Android dependency: it is unit-tested on the JVM.
 */
object SoundSynth {

    const val SAMPLE_RATE = 44_100

    fun render(effect: SoundEffect): FloatArray = when (effect) {
        SoundEffect.BUTTON -> Voice(70).apply {
            tone(0, 1150f, 1020f, 0.16f, Wave.SINE, decay = 55f)
            tone(0, 2300f, 2040f, 0.04f, Wave.SINE, decay = 90f)
        }.finish()

        SoundEffect.STEP -> Voice(45).apply {
            noise(0, 45, 0.09f, decay = 80f, cutoffHz = 1600f, seed = 11)
            tone(0, 230f, 170f, 0.05f, Wave.SINE, decay = 70f)
        }.finish()

        // A wooden thunk carries the weight; a short muffled scrape follows it.
        SoundEffect.PUSH -> Voice(170).apply {
            tone(0, 125f, 78f, 0.30f, Wave.SINE, decay = 21f)
            tone(0, 250f, 160f, 0.07f, Wave.TRIANGLE, decay = 30f)
            noise(0, 140, 0.11f, decay = 22f, cutoffHz = 900f, seed = 23)
        }.finish()

        // Two rising bell notes: the reward must be instantly recognisable.
        SoundEffect.GOAL -> Voice(750).apply {
            bell(0, 1318.5f, 0.20f, decay = 7f)
            bell(75, 1975.5f, 0.18f, decay = 6f)
        }.finish()

        SoundEffect.BUMP -> Voice(110).apply {
            tone(0, 95f, 62f, 0.24f, Wave.SINE, decay = 34f)
            noise(0, 60, 0.06f, decay = 55f, cutoffHz = 450f, seed = 31)
        }.finish()

        // A soft falling sweep: time running backwards.
        SoundEffect.UNDO -> Voice(120).apply {
            tone(0, 640f, 400f, 0.11f, Wave.SINE, decay = 18f, attackMs = 6)
            tone(0, 1280f, 800f, 0.025f, Wave.SINE, decay = 26f, attackMs = 6)
        }.finish()

        SoundEffect.RESTART -> Voice(420).apply {
            tone(0, 280f, 720f, 0.09f, Wave.TRIANGLE, decay = 7f, attackMs = 60)
            bell(170, 880f, 0.08f, decay = 9f)
        }.finish()

        SoundEffect.STAR -> Voice(480).apply {
            bell(0, 1760f, 0.18f, decay = 8f)
        }.finish()

        SoundEffect.UNLOCK -> Voice(820).apply {
            bell(0, 1568f, 0.17f, decay = 5.5f)
            bell(95, 2349.3f, 0.15f, decay = 5f)
            noise(0, 300, 0.025f, decay = 9f, cutoffHz = 6000f, seed = 41)
        }.finish()

        // A major arpeggio resolving onto a soft chord: the one melody in the game.
        SoundEffect.VICTORY -> Voice(1600).apply {
            bell(0, 1046.5f, 0.17f, decay = 4.5f)
            bell(110, 1318.5f, 0.17f, decay = 4.5f)
            bell(220, 1568f, 0.17f, decay = 4.2f)
            bell(340, 2093f, 0.19f, decay = 3.2f)
            tone(340, 523.25f, 523.25f, 0.05f, Wave.SINE, decay = 2.4f, attackMs = 40)
            tone(340, 659.25f, 659.25f, 0.04f, Wave.SINE, decay = 2.4f, attackMs = 40)
            tone(340, 784f, 784f, 0.04f, Wave.SINE, decay = 2.4f, attackMs = 40)
        }.finish()
    }

    /** Peak absolute amplitude, in [0, 1]. */
    fun peak(samples: FloatArray): Float = samples.maxOfOrNull { abs(it) } ?: 0f

    private enum class Wave {
        SINE,
        TRIANGLE;

        fun at(phase: Double): Double = when (this) {
            SINE -> sin(phase)
            TRIANGLE -> 2.0 / PI * asin(sin(phase))
        }
    }

    private class Voice(durationMs: Int) {
        val data = FloatArray(SAMPLE_RATE * durationMs / 1000)

        /**
         * Adds an oscillator starting at [atMs], gliding from [startHz] to
         * [endHz], with a linear attack and an exponential decay ([decay] per
         * second: the larger, the shorter).
         */
        fun tone(
            atMs: Int,
            startHz: Float,
            endHz: Float,
            amplitude: Float,
            wave: Wave,
            decay: Float,
            attackMs: Int = 3,
        ) {
            val start = SAMPLE_RATE * atMs / 1000
            val length = data.size - start
            if (length <= 0) return
            var phase = 0.0
            for (i in 0 until length) {
                val progress = i.toDouble() / length
                val frequency = startHz + (endHz - startHz) * progress
                phase += 2.0 * PI * frequency / SAMPLE_RATE
                data[start + i] += (wave.at(phase) * amplitude * envelope(i, attackMs, decay)).toFloat()
            }
        }

        /** A vibraphone-like bell: partials at 1x, 4x and 10x, upper ones dying fast. */
        fun bell(atMs: Int, hz: Float, amplitude: Float, decay: Float) {
            tone(atMs, hz, hz, amplitude, Wave.SINE, decay, attackMs = 2)
            tone(atMs, hz * 4f, hz * 4f, amplitude * 0.22f, Wave.SINE, decay * 3f, attackMs = 1)
            tone(atMs, hz * 10f, hz * 10f, amplitude * 0.05f, Wave.SINE, decay * 6f, attackMs = 1)
        }

        /** Low-passed white noise: the grain of wood, of a floor, of a scrape. */
        fun noise(atMs: Int, durationMs: Int, amplitude: Float, decay: Float, cutoffHz: Float, seed: Int) {
            val start = SAMPLE_RATE * atMs / 1000
            val length = minOf(SAMPLE_RATE * durationMs / 1000, data.size - start)
            val smoothing = 1.0 - exp(-2.0 * PI * cutoffHz / SAMPLE_RATE)
            var state = seed.toLong() * 6364136223846793005L + 1442695040888963407L
            var filtered = 0.0
            for (i in 0 until length) {
                state = state * 6364136223846793005L + 1442695040888963407L
                val white = ((state ushr 33).toDouble() / (1L shl 31).toDouble()) * 2.0 - 1.0
                filtered += smoothing * (white - filtered)
                data[start + i] += (filtered * amplitude * 2.5 * envelope(i, 2, decay)).toFloat()
            }
        }

        private fun envelope(sample: Int, attackMs: Int, decay: Float): Double {
            val attackSamples = SAMPLE_RATE * attackMs / 1000.0
            val attack = if (attackSamples <= 0) 1.0 else (sample / attackSamples).coerceAtMost(1.0)
            return attack * exp(-decay * sample.toDouble() / SAMPLE_RATE)
        }

        /** Fades the tail out (a cut waveform clicks) and keeps the peak safe. */
        fun finish(): FloatArray {
            val release = minOf(RELEASE_SAMPLES, data.size)
            for (i in 0 until release) data[data.size - 1 - i] *= i.toFloat() / release
            val peak = peak(data)
            if (peak > MAX_PEAK) for (i in data.indices) data[i] *= MAX_PEAK / peak
            return data
        }
    }

    private const val RELEASE_SAMPLES = 400
    private const val MAX_PEAK = 0.9f
}
