package dev.stefan.sokoban.game

import dev.stefan.sokoban.core.Direction
import kotlin.math.abs

/**
 * Turns a finger's travel into a move.
 *
 * A swipe counts once it has travelled [threshold] along a clearly dominant
 * axis. A diagonal drag is ambiguous: it returns null and waits for the finger
 * to commit, rather than guessing and moving the wrong way.
 */
object Swipe {

    /** How much longer the main axis must be than the other one. */
    const val DOMINANCE = 1.4f

    fun classify(dx: Float, dy: Float, threshold: Float): Direction? {
        val ax = abs(dx)
        val ay = abs(dy)
        if (maxOf(ax, ay) < threshold) return null
        return when {
            ax >= ay * DOMINANCE -> if (dx > 0) Direction.RIGHT else Direction.LEFT
            ay >= ax * DOMINANCE -> if (dy > 0) Direction.DOWN else Direction.UP
            else -> null
        }
    }
}
