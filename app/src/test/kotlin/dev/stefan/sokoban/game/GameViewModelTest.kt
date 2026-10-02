package dev.stefan.sokoban.game

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.lifecycle.SavedStateHandle
import dev.stefan.sokoban.core.Direction
import dev.stefan.sokoban.core.Position
import dev.stefan.sokoban.core.levels.LevelPack
import dev.stefan.sokoban.data.ProgressRepository
import dev.stefan.sokoban.feedback.GameFeedback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** In-memory DataStore: the real repository code, without files or threads. */
private class MemoryStore : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    override val data: Flow<Preferences> = state
    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        transform(state.value).also { state.value = it }
}

private class RecordingFeedback : GameFeedback {
    val events = mutableListOf<String>()
    override fun step() { events += "step" }
    override fun push() { events += "push" }
    override fun goal() { events += "goal" }
    override fun bump() { events += "bump" }
    override fun undo() { events += "undo" }
    override fun restart() { events += "restart" }
    override fun victory() { events += "victory" }
}

@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModelTest {

    // Unconfined mirrors Dispatchers.Main.immediate: the first command runs at once.
    private val dispatcher = UnconfinedTestDispatcher()
    private val repository = ProgressRepository(MemoryStore(), LevelPack.levels.map { it.id })
    private val feedback = RecordingFeedback()

    @BeforeEach
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(saved: SavedStateHandle = SavedStateHandle()) = GameViewModel(repository, feedback, saved)

    @Test
    fun `opening a level starts it fresh`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.open(0)
        val state = requireNotNull(vm.state.value)
        assertEquals("w1-01", state.entry.id)
        assertEquals(0, state.game.moves)
        assertEquals(GamePhase.Playing, state.phase)
    }

    @Test
    fun `solving saves progress and reports the result`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.open(0)
        // First Push: step right, then push twice onto the target.
        repeat(3) {
            vm.move(Direction.RIGHT)
            advanceTimeBy(GameViewModel.PUSH_MS + 1)
        }
        assertEquals(GamePhase.Celebrating, vm.state.value?.phase)
        assertEquals(listOf("step", "push", "goal"), feedback.events)

        advanceTimeBy(GameViewModel.CELEBRATION_MS + 1)
        advanceUntilIdle()
        val complete = assertInstanceOf(GamePhase.Complete::class.java, vm.state.value?.phase)
        with(complete.result) {
            assertEquals(3, moves)
            assertEquals(3, stars)
            assertTrue(isNewBest)
            assertEquals(listOf(2, 3), unlockedNumbers)
            assertEquals(1, nextIndex)
        }
        assertTrue("victory" in feedback.events)
        val saved = repository.progress.first()
        assertTrue("w1-01" in saved.completed)
        assertEquals(3, saved.bestMoves["w1-01"])
    }

    @Test
    fun `reopening a solved level starts it over, an unfinished one resumes`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.open(0)
        vm.move(Direction.RIGHT)
        advanceUntilIdle()
        vm.open(0)
        assertEquals(1, vm.state.value?.game?.moves, "a game in progress is kept")

        repeat(2) {
            vm.move(Direction.RIGHT)
            advanceTimeBy(GameViewModel.PUSH_MS + 1)
        }
        advanceUntilIdle()
        assertTrue(vm.state.value?.phase is GamePhase.Complete)
        vm.open(0)
        val state = requireNotNull(vm.state.value)
        assertEquals(GamePhase.Playing, state.phase)
        assertEquals(0, state.game.moves)
        assertEquals(3, state.best)
    }

    @Test
    fun `a solve is saved even when the level is restarted during the celebration`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.open(0)
        repeat(3) {
            vm.move(Direction.RIGHT)
            advanceTimeBy(GameViewModel.PUSH_MS + 1)
        }
        assertEquals(GamePhase.Celebrating, vm.state.value?.phase)
        vm.restart()
        advanceUntilIdle()
        assertEquals(GamePhase.Playing, vm.state.value?.phase)
        val saved = repository.progress.first()
        assertTrue("w1-01" in saved.completed)
        assertEquals(3, saved.bestMoves["w1-01"])
        assertFalse("victory" in feedback.events, "the fanfare belongs to the result card")
    }

    @Test
    fun `moves are refused once the level is solved`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.open(0)
        repeat(3) {
            vm.move(Direction.RIGHT)
            advanceTimeBy(GameViewModel.PUSH_MS + 1)
        }
        vm.move(Direction.LEFT)
        vm.undo()
        advanceUntilIdle()
        assertEquals(3, vm.state.value?.game?.moves)
    }

    @Test
    fun `every press of a fast burst is played`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.open(2) // "Pair": the player's row is empty.
        repeat(10) { vm.move(if (it % 2 == 0) Direction.LEFT else Direction.RIGHT) }
        assertEquals(1, vm.state.value?.game?.moves, "the first move is immediate")
        advanceUntilIdle()
        assertEquals(10, vm.state.value?.game?.moves, "no press is lost")
        assertEquals("lrlrlrlrlr", vm.state.value?.game?.moveLog())
    }

    @Test
    fun `a backlog is played faster than single moves`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.open(2)
        repeat(10) { vm.move(if (it % 2 == 0) Direction.LEFT else Direction.RIGHT) }
        advanceTimeBy(GameViewModel.STEP_MS * 7)
        assertEquals(10, vm.state.value?.game?.moves)
    }

    @Test
    fun `a held control never runs ahead of the hero`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.open(2)
        vm.move(Direction.LEFT)
        repeat(8) { vm.move(if (it % 2 == 0) Direction.RIGHT else Direction.LEFT, held = true) }
        advanceUntilIdle()
        assertEquals(2, vm.state.value?.game?.moves, "the press, plus the one repeat that was buffered")
    }

    @Test
    fun `queued moves are paced so each one can be seen`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.open(2)
        vm.move(Direction.LEFT)
        vm.move(Direction.RIGHT)
        assertEquals(1, vm.state.value?.game?.moves)
        advanceTimeBy(GameViewModel.STEP_MS / 2)
        assertEquals(1, vm.state.value?.game?.moves)
        advanceTimeBy(GameViewModel.STEP_MS)
        assertEquals(2, vm.state.value?.game?.moves)
    }

    @Test
    fun `undo and restart`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.open(2)
        vm.move(Direction.LEFT)
        advanceUntilIdle()
        vm.move(Direction.UP)
        advanceUntilIdle()
        assertEquals(2, vm.state.value?.game?.moves)

        vm.undo()
        advanceUntilIdle()
        assertEquals(1, vm.state.value?.game?.moves)
        assertTrue("undo" in feedback.events)

        vm.restart()
        val restarted = vm.state.value!!.game
        assertEquals(0, restarted.moves)
        assertFalse(restarted.canUndo)
        assertEquals(LevelPack.entry(2).level.playerStart, restarted.player)
    }

    @Test
    fun `walking into a wall knocks once when held`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.open(0)
        vm.move(Direction.UP)
        advanceUntilIdle()
        vm.move(Direction.UP)
        advanceUntilIdle()
        vm.move(Direction.UP)
        advanceUntilIdle()
        assertEquals(1, vm.state.value?.game?.moves)
        assertEquals(1, feedback.events.count { it == "bump" })
        assertEquals(Direction.UP, vm.state.value?.game?.facing)
    }

    @Test
    fun `a saved game is rebuilt after process death`() = runTest(dispatcher) {
        val vm = viewModel(SavedStateHandle(mapOf("level" to 2, "moves" to "lu")))
        val state = requireNotNull(vm.state.value)
        assertEquals(2, state.entry.index)
        assertEquals(2, state.game.moves)
        // Opening the same level keeps the game; another level starts fresh.
        vm.open(2)
        assertEquals(2, vm.state.value?.game?.moves)
        vm.open(3)
        assertEquals(0, vm.state.value?.game?.moves)
    }

    @Test
    fun `a corrupt saved move log falls back to a fresh start`() = runTest(dispatcher) {
        val vm = viewModel(SavedStateHandle(mapOf("level" to 0, "moves" to "uuuu??")))
        assertEquals(0, vm.state.value?.game?.moves)
    }

    @Test
    fun `stuck crates are reported`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.open(3) // "Along the Wall": pushing the crate right twice jams it on a wall with no target.
        assertTrue(vm.state.value!!.stuckCrates.isEmpty())
        listOf(Direction.LEFT, Direction.LEFT, Direction.UP, Direction.RIGHT, Direction.RIGHT).forEach {
            vm.move(it)
            advanceUntilIdle()
        }
        assertTrue(vm.state.value!!.stuckCrates.isNotEmpty(), vm.state.value!!.game.level.toText())
    }

    @Test
    fun `a bump says where it happened, and a held direction knocks only once`() = runTest(dispatcher) {
        val vm = viewModel()
        val events = mutableListOf<BoardEvent>()
        backgroundScope.launch { vm.events.collect { events += it } }
        vm.open(0) // "First Push": a free cell, then the wall, to the player's left.
        repeat(3) {
            vm.move(Direction.LEFT)
            advanceUntilIdle()
        }
        assertEquals(
            listOf(
                BoardEvent.Stepped(Direction.LEFT, Position(1, 2)),
                BoardEvent.Bumped(Direction.LEFT, Position(1, 2), intoCrate = false, knocked = true),
                BoardEvent.Bumped(Direction.LEFT, Position(1, 2), intoCrate = false, knocked = false),
            ),
            events,
        )
        assertEquals(listOf("step", "bump"), feedback.events)
    }

    @Test
    fun `a bump against a crate that cannot move says so`() = runTest(dispatcher) {
        val vm = viewModel()
        val events = mutableListOf<BoardEvent>()
        backgroundScope.launch { vm.events.collect { events += it } }
        vm.open(3) // "Along the Wall": push the crate left until the wall stops it.
        for (direction in listOf(Direction.UP, Direction.LEFT, Direction.LEFT, Direction.LEFT)) {
            vm.move(direction)
            advanceUntilIdle()
        }
        assertEquals(BoardEvent.Bumped(Direction.LEFT, Position(2, 2), intoCrate = true, knocked = true), events.last())
    }
}
