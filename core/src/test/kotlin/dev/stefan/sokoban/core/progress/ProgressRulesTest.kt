package dev.stefan.sokoban.core.progress

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProgressRulesTest {

    private val ids = listOf("a", "b", "c", "d", "e")

    @Test
    fun `only the first level is open at the start`() {
        val progress = Progress()
        assertTrue(ProgressRules.isUnlocked(progress, ids, 0))
        (1 until ids.size).forEach { assertFalse(ProgressRules.isUnlocked(progress, ids, it)) }
        assertFalse(ProgressRules.isUnlocked(progress, ids, 99))
    }

    @Test
    fun `solving a level unlocks the next two`() {
        val completion = ProgressRules.complete(Progress(), ids, 0, moves = 10, par = 10)
        assertEquals(listOf("b", "c"), completion.newlyUnlocked)
        assertTrue(ProgressRules.isUnlocked(completion.progress, ids, 1))
        assertTrue(ProgressRules.isUnlocked(completion.progress, ids, 2))
        assertFalse(ProgressRules.isUnlocked(completion.progress, ids, 3))
        assertTrue("a" in completion.progress.completed)
    }

    @Test
    fun `unlocking stops at the end of the campaign and is not repeated`() {
        val first = ProgressRules.complete(Progress(), ids, 3, moves = 5, par = 5)
        assertEquals(listOf("e"), first.newlyUnlocked)
        val again = ProgressRules.complete(first.progress, ids, 3, moves = 5, par = 5)
        assertEquals(emptyList<String>(), again.newlyUnlocked)
    }

    @Test
    fun `levels added after the last one open for players who solved it`() {
        val before = listOf("a", "b", "c")
        val progress = before.indices.fold(Progress()) { progress, index ->
            ProgressRules.complete(progress, before, index, moves = 5, par = 5).progress
        }
        val extended = before + listOf("d", "e", "f")
        assertTrue(ProgressRules.isUnlocked(progress, extended, 3))
        assertTrue(ProgressRules.isUnlocked(progress, extended, 4))
        assertFalse(ProgressRules.isUnlocked(progress, extended, 5))
        assertEquals(3, ProgressRules.nextLevelToPlay(progress, extended))
    }

    @Test
    fun `first completion is a new best`() {
        val completion = ProgressRules.complete(Progress(), ids, 0, moves = 12, par = 10)
        assertTrue(completion.isNewBest)
        assertNull(completion.previousBest)
        assertEquals(12, completion.progress.bestMoves["a"])
    }

    @Test
    fun `best only improves`() {
        val first = ProgressRules.complete(Progress(), ids, 0, moves = 12, par = 10).progress
        val worse = ProgressRules.complete(first, ids, 0, moves = 15, par = 10)
        assertFalse(worse.isNewBest)
        assertEquals(12, worse.previousBest)
        assertEquals(12, worse.progress.bestMoves["a"])

        val better = ProgressRules.complete(first, ids, 0, moves = 11, par = 10)
        assertTrue(better.isNewBest)
        assertEquals(11, better.progress.bestMoves["a"])
    }

    @Test
    fun `equal score is not a new best`() {
        val first = ProgressRules.complete(Progress(), ids, 0, moves = 12, par = 10).progress
        assertFalse(ProgressRules.complete(first, ids, 0, moves = 12, par = 10).isNewBest)
    }

    @Test
    fun `stars depend on par`() {
        assertEquals(3, ProgressRules.stars(moves = 8, par = 10))
        assertEquals(3, ProgressRules.stars(moves = 10, par = 10))
        assertEquals(2, ProgressRules.stars(moves = ProgressRules.twoStarLimit(10), par = 10))
        assertEquals(1, ProgressRules.stars(moves = ProgressRules.twoStarLimit(10) + 1, par = 10))
    }

    @Test
    fun `play resumes at the first open unsolved level`() {
        assertEquals(0, ProgressRules.nextLevelToPlay(Progress(), ids))
        val progress = Progress(completed = setOf("a", "b"), unlocked = setOf("a", "b", "c", "d"))
        assertEquals(2, ProgressRules.nextLevelToPlay(progress, ids))
        val skipped = Progress(completed = setOf("a", "c"), unlocked = setOf("a", "b", "c", "d", "e"))
        assertEquals(1, ProgressRules.nextLevelToPlay(skipped, ids))
    }
}
