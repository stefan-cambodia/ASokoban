package dev.stefan.sokoban.audio

import android.content.res.AssetManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.ExperimentalCoroutinesApi

/**
 * The game's sound output.
 *
 * Each effect comes from `assets/sounds/<name>.wav` when such a file exists,
 * and from [SoundSynth] otherwise: recorded audio can be dropped in later
 * without touching code. Loading happens in the background at startup; sounds
 * requested before it finishes are skipped rather than delayed.
 */
class SoundPlayer(assets: AssetManager, scope: CoroutineScope) {

    private val mixer = AudioMixer(SoundSynth.SAMPLE_RATE)

    private val library: Deferred<Map<SoundEffect, FloatArray>> = scope.async(Dispatchers.Default) {
        val provided = runCatching { assets.list(ASSET_DIR)?.toSet() }.getOrNull().orEmpty()
        SoundEffect.entries.associateWith { effect ->
            val file = effect.assetName + ".wav"
            val recorded = if (file in provided) {
                runCatching { assets.open("$ASSET_DIR/$file").use { it.readBytes() } }
                    .getOrNull()
                    ?.let { WavDecoder.decode(it, SoundSynth.SAMPLE_RATE) }
                    .also { if (it == null) Log.w(TAG, "Unreadable $file: using the synthesized sound") }
            } else {
                null
            }
            recorded ?: SoundSynth.render(effect)
        }
    }

    @Volatile
    var enabled: Boolean = true

    fun start() = mixer.start()

    fun stop() = mixer.stop()

    @OptIn(ExperimentalCoroutinesApi::class)
    fun play(effect: SoundEffect, gain: Float = 1f, rate: Float = 1f) {
        if (!enabled || !library.isCompleted) return
        val samples = library.getCompleted()[effect] ?: return
        mixer.play(samples, gain, rate)
    }

    private companion object {
        const val TAG = "SoundPlayer"
        const val ASSET_DIR = "sounds"
    }
}
