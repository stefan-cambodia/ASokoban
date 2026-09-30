package dev.stefan.sokoban.game

import dev.stefan.sokoban.core.Direction
import dev.stefan.sokoban.ui.game.padDirection
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class InputClassificationTest {

    private val threshold = 40f

    @Test
    fun `short drags are not swipes`() {
        assertNull(Swipe.classify(10f, 5f, threshold))
        assertNull(Swipe.classify(-39f, 0f, threshold))
    }

    @Test
    fun `clear swipes map to their direction`() {
        assertEquals(Direction.RIGHT, Swipe.classify(60f, 8f, threshold))
        assertEquals(Direction.LEFT, Swipe.classify(-60f, -12f, threshold))
        assertEquals(Direction.DOWN, Swipe.classify(5f, 70f, threshold))
        assertEquals(Direction.UP, Swipe.classify(-10f, -45f, threshold))
    }

    @Test
    fun `diagonal drags wait instead of guessing`() {
        assertNull(Swipe.classify(50f, 50f, threshold))
        assertNull(Swipe.classify(-60f, 55f, threshold))
        // Once the finger commits to an axis, the swipe is recognised.
        assertEquals(Direction.RIGHT, Swipe.classify(90f, 55f, threshold))
    }

    @Test
    fun `pad maps every point outside the dead zone`() {
        assertNull(padDirection(2f, 3f, deadZone = 10f))
        assertEquals(Direction.UP, padDirection(0f, -50f, 10f))
        assertEquals(Direction.DOWN, padDirection(-5f, 50f, 10f))
        assertEquals(Direction.LEFT, padDirection(-50f, 20f, 10f))
        assertEquals(Direction.RIGHT, padDirection(50f, -49f, 10f))
    }
}
