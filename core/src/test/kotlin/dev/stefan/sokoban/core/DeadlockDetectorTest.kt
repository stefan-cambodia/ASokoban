package dev.stefan.sokoban.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DeadlockDetectorTest {

    private fun state(map: String) = GameState.start(LevelParser.parse(map.trimIndent()))

    @Test
    fun `corners without goals are dead squares`() {
        val level = LevelParser.parse(
            """
            ######
            #   .#
            # @$ #
            #    #
            ######
            """.trimIndent(),
        )
        val detector = DeadlockDetector(level)
        assertTrue(detector.isDeadSquare(Position(1, 1)))
        assertTrue(detector.isDeadSquare(Position(1, 3)))
        assertTrue(detector.isDeadSquare(Position(4, 3)))
        assertFalse(detector.isDeadSquare(Position(4, 1)), "a goal is never dead")
        assertFalse(detector.isDeadSquare(Position(2, 2)))
    }

    @Test
    fun `a wall without a goal along it is dead`() {
        val level = LevelParser.parse(
            """
            #######
            #     #
            # @$ .#
            #     #
            #######
            """.trimIndent(),
        )
        val detector = DeadlockDetector(level)
        (1..5).forEach { x -> assertTrue(detector.isDeadSquare(Position(x, 1)), "top wall $x") }
        assertFalse(detector.isDeadSquare(Position(3, 2)))
    }

    @Test
    fun `box in a dead corner is deadlocked`() {
        val game = state(
            """
            ######
            #$  .#
            # @  #
            ######
            """,
        )
        assertEquals(setOf(0), DeadlockDetector(game.level).deadlockedBoxes(game))
    }

    @Test
    fun `two boxes side by side against a wall are frozen`() {
        val game = state(
            """
            #######
            # $$..#
            #  @  #
            #######
            """,
        )
        val detector = DeadlockDetector(game.level)
        // Goals share the wall, so these cells are alive on their own: only
        // the pair, pinning each other against the wall, is stuck.
        game.boxes.forEach { assertFalse(detector.isDeadSquare(it), "$it") }
        assertEquals(setOf(0, 1), detector.deadlockedBoxes(game))
    }

    @Test
    fun `2x2 block of boxes is a freeze deadlock away from walls`() {
        val game = state(
            """
            ########
            #  .   #
            # $$ . #
            # $$ . #
            #  @ . #
            ########
            """,
        )
        val detector = DeadlockDetector(game.level)
        // None of these cells is a dead square on its own: the freeze is what kills.
        game.boxes.forEach { assertFalse(detector.isDeadSquare(it), "$it") }
        assertEquals(setOf(0, 1, 2, 3), detector.deadlockedBoxes(game))
    }

    @Test
    fun `frozen boxes on goals are fine`() {
        val game = state(
            """
            ######
            #**  #
            #@   #
            ######
            """,
        )
        assertFalse(DeadlockDetector(game.level).isDeadlocked(game))
    }

    @Test
    fun `movable boxes are not reported`() {
        val game = state(
            """
            ########
            #      #
            # $ $  #
            #  @ ..#
            #      #
            ########
            """,
        )
        assertFalse(DeadlockDetector(game.level).isDeadlocked(game))
    }
}
