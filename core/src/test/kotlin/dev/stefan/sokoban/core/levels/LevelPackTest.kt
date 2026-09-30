package dev.stefan.sokoban.core.levels

import dev.stefan.sokoban.core.DeadlockDetector
import dev.stefan.sokoban.core.GameEngine
import dev.stefan.sokoban.core.GameState
import dev.stefan.sokoban.core.LevelParser
import dev.stefan.sokoban.core.solver.Solver
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

/**
 * Every shipped level is checked, not sampled: a single broken puzzle in a
 * campaign is a player stuck forever.
 */
class LevelPackTest {

    private val solutions: Map<String, String> =
        requireNotNull(javaClass.getResource("/solutions.txt")) { "solutions.txt missing" }
            .readText()
            .lines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .associate { line -> line.substringBefore(' ') to line.substringAfter(' ') }

    @Test
    fun `campaign has five worlds of ten levels`() {
        assertEquals(5, LevelPack.worlds.size)
        LevelPack.worlds.forEach { assertEquals(10, it.levels.size, it.title) }
        assertEquals(50, LevelPack.size)
        assertEquals((0 until 50).toList(), LevelPack.levels.map { it.index })
    }

    @Test
    fun `ids are unique and stable in format`() {
        val ids = LevelPack.levels.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        LevelPack.levels.forEach { entry ->
            assertEquals("w${entry.world.number}-%02d".format(entry.world.levels.indexOf(entry.definition) + 1), entry.id)
            assertTrue(entry.name.isNotBlank())
        }
    }

    @TestFactory
    fun `every level is valid and fits a phone screen`() = LevelPack.levels.map { entry ->
        DynamicTest.dynamicTest(entry.id) {
            assertEquals(emptyList<String>(), LevelParser.validate(entry.definition.map))
            val level = entry.level
            assertTrue(level.width <= MAX_SIDE && level.height <= MAX_SIDE, "${level.width}x${level.height}")
            val start = GameState.start(level)
            assertFalse(start.isSolved, "starts solved")
            assertFalse(DeadlockDetector(level).isDeadlocked(start), "starts deadlocked")
        }
    }

    @TestFactory
    fun `stored solution solves the level in exactly par moves`() = LevelPack.levels.map { entry ->
        DynamicTest.dynamicTest(entry.id) {
            val solution = requireNotNull(solutions[entry.id]) { "no stored solution" }
            val end = GameEngine.replay(entry.level, solution)
            assertTrue(end.isSolved, "solution does not solve the level")
            assertEquals(entry.par, end.moves, "par must equal the verified solution length")
            // The notation marks pushes; it must agree with what the engine did.
            assertEquals(solution.count { it.isUpperCase() }, end.pushes)
        }
    }

    @TestFactory
    fun `solver independently proves every level solvable`() = LevelPack.levels.map { entry ->
        DynamicTest.dynamicTest(entry.id) {
            val result = Solver(entry.level).solve(Solver.Metric.PUSHES, maxExplored = 3_000_000)
            val solved = assertInstanceOf(Solver.Result.Solved::class.java, result)
            assertTrue(GameEngine.replay(entry.level, solved.lurd).isSolved)
        }
    }

    @Test
    fun `difficulty grows from world to world`() {
        val averagePar = LevelPack.worlds.map { world -> world.levels.map { it.par }.average() }
        val averageBoxes = LevelPack.worlds.map { world ->
            world.levels.map { LevelParser.parse(it.map).boxStarts.size }.average()
        }
        assertEquals(averagePar.sorted(), averagePar, "average par per world: $averagePar")
        assertEquals(averageBoxes.sorted(), averageBoxes, "average crates per world: $averageBoxes")
    }

    @Test
    fun `tutorial levels teach with hints`() {
        val tutorial = LevelPack.worlds.first().levels
        assertTrue(tutorial.take(9).all { !it.hint.isNullOrBlank() })
        assertTrue(tutorial.maxOf { LevelParser.parse(it.map).boxStarts.size } <= 3)
    }

    private companion object {
        /** Beyond this, tiles get too small to read on a phone in portrait. */
        const val MAX_SIDE = 12
    }
}
