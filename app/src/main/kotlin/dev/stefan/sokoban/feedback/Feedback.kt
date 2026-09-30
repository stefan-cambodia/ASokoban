package dev.stefan.sokoban.feedback

import dev.stefan.sokoban.audio.SoundEffect
import dev.stefan.sokoban.audio.SoundPlayer

/** The moments of play that make a sound and a vibration. */
interface GameFeedback {
    fun step()
    fun push()
    fun goal()
    fun bump()
    fun undo()
    fun restart()
    fun victory()
}

/**
 * What the player hears and feels, by game moment. Sound and vibration each
 * honour their own setting inside [SoundPlayer] and [Haptics].
 */
class Feedback(private val sounds: SoundPlayer, private val haptics: Haptics) : GameFeedback {

    private var stepVariant = 0

    override fun step() {
        // Alternating pitch keeps a long walk from sounding like a machine.
        stepVariant = (stepVariant + 1) % STEP_RATES.size
        sounds.play(SoundEffect.STEP, gain = 0.8f, rate = STEP_RATES[stepVariant])
        haptics.perform(Haptic.STEP)
    }

    override fun push() {
        sounds.play(SoundEffect.PUSH)
        haptics.perform(Haptic.PUSH)
    }

    override fun goal() {
        sounds.play(SoundEffect.PUSH, gain = 0.7f)
        sounds.play(SoundEffect.GOAL)
        haptics.perform(Haptic.GOAL)
    }

    override fun bump() {
        sounds.play(SoundEffect.BUMP)
        haptics.perform(Haptic.BUMP)
    }

    override fun undo() {
        sounds.play(SoundEffect.UNDO)
        haptics.perform(Haptic.STEP)
    }

    override fun restart() {
        sounds.play(SoundEffect.RESTART)
        haptics.perform(Haptic.PUSH)
    }

    override fun victory() {
        sounds.play(SoundEffect.VICTORY)
        haptics.perform(Haptic.VICTORY)
    }

    /** One star popping in on the result card; each is a little higher. */
    fun star(index: Int) {
        sounds.play(SoundEffect.STAR, rate = STAR_RATES[index.coerceIn(STAR_RATES.indices)])
        haptics.perform(Haptic.TAP)
    }

    fun unlock() {
        sounds.play(SoundEffect.UNLOCK)
        haptics.perform(Haptic.GOAL)
    }

    fun tap() {
        sounds.play(SoundEffect.BUTTON, gain = 0.9f)
        haptics.perform(Haptic.TAP)
    }

    private companion object {
        val STEP_RATES = floatArrayOf(1f, 1.06f, 0.96f, 1.03f)

        /** Root, major third, fifth. */
        val STAR_RATES = floatArrayOf(1f, 1.26f, 1.5f)
    }
}
