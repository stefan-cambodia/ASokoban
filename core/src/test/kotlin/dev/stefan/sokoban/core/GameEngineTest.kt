package dev.stefan.sokoban.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class GameEngineTest {

    private fun start(map: String) = GameState.start(LevelParser.parse(map.trimIndent()))

    private fun GameState.play(vararg directions: Direction): GameState =
        directions.fold(this) { state, direction -> GameEngine.move(state, direction).state }

    @Nested
    inner class Movement {

        private val open = """
            #######
            #     #
            # .@  #
            #   $ #
            #     #
            #######
        """

        @Test
        fun `player moves one cell in each direction`() {
            val state = start(open)
            assertEquals(Position(3, 1), GameEngine.move(state, Direction.UP).state.player)
            assertEquals(Position(3, 3), GameEngine.move(state, Direction.DOWN).state.player)
            assertEquals(Position(2, 2), GameEngine.move(state, Direction.LEFT).state.player)
            assertEquals(Position(4, 2), GameEngine.move(state, Direction.RIGHT).state.player)
        }

        @Test
        fun `a free step is reported as a walk`() {
            val result = GameEngine.move(start(open), Direction.RIGHT)
            val walked = assertInstanceOf(MoveResult.Walked::class.java, result)
            assertEquals(Position(3, 2), walked.from)
            assertEquals(Position(4, 2), walked.to)
        }

        @Test
        fun `player faces the direction of the last move`() {
            val state = start(open).play(Direction.LEFT)
            assertEquals(Direction.LEFT, state.facing)
        }

        @Test
        fun `walls stop the player without counting a move`() {
            val state = start(open).play(Direction.UP)
            val result = GameEngine.move(state, Direction.UP)
            val blocked = assertInstanceOf(MoveResult.Blocked::class.java, result)
            assertEquals(BlockReason.WALL, blocked.reason)
            assertEquals(Position(3, 1), result.state.player)
            assertEquals(1, result.state.moves)
        }

        @Test
        fun `bumping a wall turns the player but adds no history`() {
            val state = start(open).play(Direction.UP)
            val turned = GameEngine.move(state, Direction.UP).state
            assertEquals(Direction.UP, turned.facing)
            val undone = GameEngine.undo(turned)!!
            assertEquals(Position(3, 2), undone.player)
            assertFalse(undone.canUndo)
        }

        @Test
        fun `every successful step counts one move`() {
            val state = start(open).play(Direction.LEFT, Direction.RIGHT, Direction.RIGHT, Direction.UP)
            assertEquals(4, state.moves)
            assertEquals(0, state.pushes)
        }
    }

    @Nested
    inner class Pushing {

        @Test
        fun `walking into a box pushes it`() {
            val state = start(
                """
                ######
                #@$ .#
                ######
                """,
            )
            val result = GameEngine.move(state, Direction.RIGHT)
            val pushed = assertInstanceOf(MoveResult.Pushed::class.java, result)
            assertEquals(0, pushed.boxIndex)
            assertEquals(Position(2, 1), pushed.from)
            assertEquals(Position(3, 1), pushed.to)
            assertEquals(Position(2, 1), result.state.player)
            assertEquals(listOf(Position(3, 1)), result.state.boxes)
            assertEquals(1, result.state.moves)
            assertEquals(1, result.state.pushes)
        }

        @Test
        fun `a box against a wall does not move`() {
            val state = start(
                """
                #####
                #@$##
                #  .#
                #####
                """,
            )
            val result = GameEngine.move(state, Direction.RIGHT)
            val blocked = assertInstanceOf(MoveResult.Blocked::class.java, result)
            assertEquals(BlockReason.BOX, blocked.reason)
            assertEquals(Position(1, 1), result.state.player)
            assertEquals(listOf(Position(2, 1)), result.state.boxes)
            assertEquals(0, result.state.moves)
        }

        @Test
        fun `two boxes in a row cannot be pushed`() {
            val state = start(
                """
                #######
                #@$$ .#
                #    .#
                #######
                """,
            )
            val result = GameEngine.move(state, Direction.RIGHT)
            assertInstanceOf(MoveResult.Blocked::class.java, result)
            assertEquals(listOf(Position(2, 1), Position(3, 1)), result.state.boxes)
        }

        @Test
        fun `boxes cannot be pulled`() {
            val state = start(
                """
                ######
                # @$.#
                ######
                """,
            )
            val moved = GameEngine.move(state, Direction.LEFT).state
            assertEquals(Position(1, 1), moved.player)
            assertEquals(listOf(Position(3, 1)), moved.boxes)
        }

        @Test
        fun `only the pushed box moves and keeps its identity`() {
            val state = start(
                """
                #######
                #@$  .#
                # $  .#
                #######
                """,
            )
            val result = GameEngine.move(state, Direction.RIGHT) as MoveResult.Pushed
            assertEquals(0, result.boxIndex)
            assertEquals(listOf(Position(3, 1), Position(2, 2)), result.state.boxes)
        }

        @Test
        fun `pushing onto and off a goal is reported`() {
            val state = start(
                """
                #######
                #@$. .#
                #  $  #
                #######
                """,
            )
            val onto = GameEngine.move(state, Direction.RIGHT) as MoveResult.Pushed
            assertTrue(onto.enteredGoal)
            assertFalse(onto.leftGoal)
            val off = GameEngine.move(onto.state, Direction.RIGHT) as MoveResult.Pushed
            assertFalse(off.enteredGoal)
            assertTrue(off.leftGoal)
        }
    }

    @Nested
    inner class Victory {

        @Test
        fun `level is solved when every box is on a goal`() {
            val state = start(
                """
                ######
                #@$ .#
                #  $.#
                ######
                """,
            )
            val half = state.play(Direction.RIGHT, Direction.RIGHT)
            assertEquals(1, half.boxesOnGoals)
            assertFalse(half.isSolved)
            val solved = half.play(Direction.LEFT, Direction.DOWN, Direction.RIGHT)
            assertEquals(2, solved.boxesOnGoals)
            assertTrue(solved.isSolved)
        }

        @Test
        fun `no move is accepted once solved`() {
            val solved = start(
                """
                #####
                #@$.#
                #####
                """,
            ).play(Direction.RIGHT)
            assertTrue(solved.isSolved)
            val result = GameEngine.move(solved, Direction.LEFT)
            assertEquals(BlockReason.SOLVED, (result as MoveResult.Blocked).reason)
            assertSame(solved, result.state)
        }

        @Test
        fun `boxes starting on goals count as placed`() {
            val state = start(
                """
                ######
                #@*$.#
                ######
                """,
            )
            assertEquals(1, state.boxesOnGoals)
            assertFalse(state.isSolved)
        }
    }

    @Nested
    inner class Undo {

        private val map = """
            #######
            #@ $ .#
            #######
        """

        @Test
        fun `undo restores player, boxes and counters`() {
            val initial = start(map)
            val moved = initial.play(Direction.RIGHT, Direction.RIGHT)
            assertEquals(2, moved.moves)
            assertEquals(1, moved.pushes)

            val once = GameEngine.undo(moved)!!
            assertEquals(Position(2, 1), once.player)
            assertEquals(listOf(Position(3, 1)), once.boxes)
            assertEquals(1, once.moves)
            assertEquals(0, once.pushes)

            val twice = GameEngine.undo(once)!!
            assertEquals(initial.player, twice.player)
            assertEquals(initial.boxes, twice.boxes)
            assertEquals(0, twice.moves)
        }

        @Test
        fun `undo restores the facing direction`() {
            val moved = start(map).play(Direction.RIGHT, Direction.LEFT)
            assertEquals(Direction.RIGHT, GameEngine.undo(moved)!!.facing)
        }

        @Test
        fun `nothing to undo at the start`() {
            val state = start(map)
            assertFalse(state.canUndo)
            assertNull(GameEngine.undo(state))
        }

        @Test
        fun `undo is unlimited`() {
            var state = start(
                """
                ######
                #@ $.#
                ######
                """,
            )
            repeat(5_000) { state = state.play(if (it % 2 == 0) Direction.RIGHT else Direction.LEFT) }
            assertEquals(5_000, state.moves)
            var undos = 0
            while (true) state = GameEngine.undo(state)?.also { undos++ } ?: break
            assertEquals(5_000, undos)
            assertEquals(0, state.moves)
        }

        @Test
        fun `undo after a win reopens the level`() {
            val solved = start(
                """
                #####
                #@$.#
                #####
                """,
            ).play(Direction.RIGHT)
            val undone = GameEngine.undo(solved)!!
            assertFalse(undone.isSolved)
            assertInstanceOf(MoveResult.Pushed::class.java, GameEngine.move(undone, Direction.RIGHT))
        }
    }

    @Nested
    inner class Restart {

        @Test
        fun `restart restores the exact initial state and clears history`() {
            val initial = start(
                """
                #######
                #@ $ .#
                # $  .#
                #######
                """,
            )
            val played = initial.play(Direction.RIGHT, Direction.RIGHT, Direction.DOWN)
            val restarted = GameEngine.restart(played)
            assertEquals(initial.player, restarted.player)
            assertEquals(initial.boxes, restarted.boxes)
            assertEquals(initial.facing, restarted.facing)
            assertEquals(0, restarted.moves)
            assertEquals(0, restarted.pushes)
            assertFalse(restarted.canUndo)
            assertEquals("", restarted.moveLog())
        }
    }

    @Nested
    inner class Notation {

        private val level = LevelParser.parse(
            """
            #######
            #@ $ .#
            #######
            """.trimIndent(),
        )

        @Test
        fun `move log uses LURD with upper case pushes`() {
            val state = GameState.start(level).play(Direction.RIGHT, Direction.RIGHT, Direction.RIGHT)
            assertEquals("rRR", state.moveLog())
        }

        @Test
        fun `replay rebuilds the same state`() {
            val state = GameEngine.replay(level, "rRR")
            assertTrue(state.isSolved)
            assertEquals(3, state.moves)
            assertEquals("rRR", state.moveLog())
        }

        @Test
        fun `replay rejects blocked moves and unknown letters`() {
            assertThrows(IllegalArgumentException::class.java) { GameEngine.replay(level, "u") }
            assertThrows(IllegalArgumentException::class.java) { GameEngine.replay(level, "rx") }
        }
    }

    @Nested
    inner class PathFinding {

        private val map = """
            #######
            #@  # #
            # # $.#
            #   # #
            #######
        """

        @Test
        fun `path walks around walls`() {
            val state = start(map)
            val path = GameEngine.pathTo(state, Position(3, 3))!!
            assertEquals(4, path.size)
            val end = path.fold(state) { s, d -> GameEngine.move(s, d).state }
            assertEquals(Position(3, 3), end.player)
            assertEquals(state.boxes, end.boxes)
        }

        @Test
        fun `path never pushes boxes`() {
            val state = start(map)
            assertNull(GameEngine.pathTo(state, Position(5, 2)))
            assertNull(GameEngine.pathTo(state, Position(4, 2)))
        }

        @Test
        fun `path to walls or the current cell`() {
            val state = start(map)
            assertNull(GameEngine.pathTo(state, Position(0, 0)))
            assertEquals(emptyList<Direction>(), GameEngine.pathTo(state, state.player))
        }
    }
}
