package dev.stefan.sokoban.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Polyphonic player for in-memory sounds.
 *
 * One streaming [AudioTrack] fed by a thread that mixes the active voices
 * itself. SoundPool would need encoded files; these sounds are computed arrays,
 * so mixing them directly is both simpler and lower-latency.
 *
 * When nothing has played for a short while the track is paused and the thread
 * sleeps: an idle game costs no CPU and no battery for silence.
 */
class AudioMixer(private val sampleRate: Int) {

    private class Voice(val samples: FloatArray, val gain: Float, val rate: Float) {
        var position = 0.0
    }

    private val lock = ReentrantLock()
    private val wake = lock.newCondition()
    private val voices = ArrayList<Voice>()

    private var track: AudioTrack? = null
    private var thread: Thread? = null

    @Volatile
    private var running = false

    @Synchronized
    fun start() {
        if (running) return
        val minBytes = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBytes <= 0) {
            Log.w(TAG, "No usable buffer size ($minBytes): sound disabled")
            return
        }
        val created = runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(maxOf(minBytes, FRAMES_PER_WRITE * 2 * 3))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .build()
        }.getOrNull()
        if (created == null || created.state != AudioTrack.STATE_INITIALIZED) {
            Log.w(TAG, "AudioTrack failed to initialise: sound disabled")
            created?.release()
            return
        }
        track = created
        running = true
        thread = Thread(::mixLoop, "sokoban-audio").apply {
            isDaemon = true
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    @Synchronized
    fun stop() {
        if (!running) return
        running = false
        lock.withLock {
            voices.clear()
            wake.signalAll()
        }
        thread?.join(STOP_TIMEOUT_MS)
        thread = null
        track?.let { runCatching { it.pause(); it.flush(); it.release() } }
        track = null
    }

    /** Starts [samples] at [gain], played [rate] times faster (pitch follows). */
    fun play(samples: FloatArray, gain: Float = 1f, rate: Float = 1f) {
        if (!running || samples.isEmpty()) return
        lock.withLock {
            // Past the limit, the oldest voice goes: better lose a fading tail
            // than delay a new sound.
            if (voices.size >= MAX_VOICES) voices.removeAt(0)
            voices += Voice(samples, gain, rate)
            wake.signalAll()
        }
    }

    private fun mixLoop() {
        val output = track ?: return
        val mix = FloatArray(FRAMES_PER_WRITE)
        val pcm = ShortArray(FRAMES_PER_WRITE)
        var silentFrames = 0
        var playing = false
        while (running) {
            lock.withLock {
                // Keep streaming a little silence after the last sound, so a
                // burst of moves does not pause and restart the track each time.
                while (running && voices.isEmpty() && silentFrames >= IDLE_TAIL_FRAMES) {
                    if (playing) {
                        runCatching { output.pause() }
                        playing = false
                    }
                    wake.await()
                }
                mix.fill(0f)
                if (voices.isEmpty()) silentFrames += FRAMES_PER_WRITE else silentFrames = 0
                mixVoices(mix)
            }
            if (!running) break
            if (!playing) {
                runCatching { output.play() }
                playing = true
            }
            for (i in mix.indices) pcm[i] = (mix[i].coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
            // Blocking write, outside the lock: it paces the loop while the game
            // thread stays free to start new sounds.
            val written = runCatching { output.write(pcm, 0, pcm.size) }.getOrDefault(-1)
            if (written < 0) Log.w(TAG, "AudioTrack.write returned $written")
        }
    }

    private fun mixVoices(mix: FloatArray) {
        val iterator = voices.iterator()
        while (iterator.hasNext()) {
            val voice = iterator.next()
            val samples = voice.samples
            for (i in mix.indices) {
                val index = voice.position.toInt()
                if (index >= samples.size - 1) break
                val fraction = (voice.position - index).toFloat()
                mix[i] += (samples[index] + (samples[index + 1] - samples[index]) * fraction) * voice.gain * MASTER_GAIN
                voice.position += voice.rate
            }
            if (voice.position >= samples.size - 1) iterator.remove()
        }
    }

    private companion object {
        const val TAG = "AudioMixer"

        /** ~5.8 ms per write at 44.1 kHz: sound follows the finger closely. */
        const val FRAMES_PER_WRITE = 256
        const val IDLE_TAIL_FRAMES = 44_100
        const val MAX_VOICES = 10
        const val MASTER_GAIN = 0.85f
        const val STOP_TIMEOUT_MS = 500L
    }
}
