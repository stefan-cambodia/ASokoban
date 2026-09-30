package dev.stefan.sokoban.feedback

import android.content.Context
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.annotation.RequiresApi

/** The haptic vocabulary of the game, from barely-there to celebratory. */
enum class Haptic {
    /** A step: the lightest possible tick. */
    STEP,

    /** A push: a firmer tick. */
    PUSH,

    /** A crate lands on a target: a clear, satisfying click. */
    GOAL,

    /** The way is blocked: a soft, low knock. */
    BUMP,

    /** UI buttons. */
    TAP,

    /** Level complete: a short rising sequence. */
    VICTORY,
}

/**
 * Haptic feedback, tuned per device capability.
 *
 * Modern vibrators (Android 12+) get composition primitives — short, crisp and
 * designed for exactly this. Older devices fall back to predefined effects, and
 * the oldest to short one-shot pulses. A device without a vibrator is silently
 * ignored.
 */
class Haptics(context: Context) {

    private val vibrator: Vibrator? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
    }.getOrNull()?.takeIf { it.hasVibrator() }

    // LOW_TICK arrived in Android 12; before that, predefined effects are used.
    private val primitives: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        vibrator?.areAllPrimitivesSupported(
            VibrationEffect.Composition.PRIMITIVE_TICK,
            VibrationEffect.Composition.PRIMITIVE_CLICK,
            VibrationEffect.Composition.PRIMITIVE_LOW_TICK,
        ) == true

    private val effects = HashMap<Haptic, VibrationEffect>()

    @Volatile
    var enabled: Boolean = true

    fun perform(haptic: Haptic) {
        val device = vibrator ?: return
        if (!enabled) return
        runCatching {
            val effect = effects.getOrPut(haptic) { build(haptic, device) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val usage = if (haptic == Haptic.TAP) VibrationAttributes.USAGE_TOUCH else VibrationAttributes.USAGE_MEDIA
                device.vibrate(effect, VibrationAttributes.createForUsage(usage))
            } else {
                device.vibrate(effect)
            }
        }
    }

    private fun build(haptic: Haptic, device: Vibrator): VibrationEffect = when {
        primitives -> composition(haptic)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> predefined(haptic)
        else -> oneShot(haptic, device.hasAmplitudeControl())
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun composition(haptic: Haptic): VibrationEffect {
        val composition = VibrationEffect.startComposition()
        when (haptic) {
            Haptic.STEP -> composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 0.35f)
            Haptic.PUSH -> composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.7f)
            Haptic.GOAL -> composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1f)
            Haptic.BUMP -> composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 0.9f)
            Haptic.TAP -> composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.5f)
            Haptic.VICTORY -> composition
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.5f)
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.75f, 90)
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1f, 90)
        }
        return composition.compose()
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun predefined(haptic: Haptic): VibrationEffect = when (haptic) {
        Haptic.STEP, Haptic.TAP, Haptic.BUMP -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
        Haptic.PUSH -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
        Haptic.GOAL -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
        Haptic.VICTORY -> VibrationEffect.createWaveform(
            longArrayOf(0, 18, 80, 22, 80, 34),
            intArrayOf(0, 120, 0, 180, 0, 255),
            -1,
        )
    }

    private fun oneShot(haptic: Haptic, amplitudeControl: Boolean): VibrationEffect {
        fun pulse(ms: Long, amplitude: Int) =
            VibrationEffect.createOneShot(ms, if (amplitudeControl) amplitude else VibrationEffect.DEFAULT_AMPLITUDE)
        return when (haptic) {
            Haptic.STEP -> pulse(6, 40)
            Haptic.TAP -> pulse(8, 70)
            Haptic.BUMP -> pulse(10, 60)
            Haptic.PUSH -> pulse(12, 110)
            Haptic.GOAL -> pulse(24, 200)
            Haptic.VICTORY -> VibrationEffect.createWaveform(longArrayOf(0, 20, 80, 24, 80, 36), -1)
        }
    }
}
