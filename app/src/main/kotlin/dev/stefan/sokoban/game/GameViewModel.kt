package dev.stefan.sokoban.game

import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.stefan.sokoban.SokobanApplication
import dev.stefan.sokoban.core.BlockReason
import dev.stefan.sokoban.core.DeadlockDetector
import dev.stefan.sokoban.core.Direction
import dev.stefan.sokoban.core.GameEngine
import dev.stefan.sokoban.core.GameState
import dev.stefan.sokoban.core.MoveResult
import dev.stefan.sokoban.core.Position
import dev.stefan.sokoban.core.levels.LevelEntry
import dev.stefan.sokoban.core.levels.LevelPack
import dev.stefan.sokoban.core.progress.ProgressRules
import dev.stefan.sokoban.data.ProgressRepository
import dev.stefan.sokoban.feedback.GameFeedback
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

sealed interface GamePhase {
    data object Playing : GamePhase

    /** The last crate is home: input is closed while the board celebrates. */
    data object Celebrating : GamePhase

    data class Complete(val result: LevelResult) : GamePhase
}

data class LevelResult(
    val moves: Int,
    val par: Int,
    val stars: Int,
    val isNewBest: Boolean,
    val previousBest: Int?,
    /** 1-based numbers of the levels this solve opened. */
    val unlockedNumbers: List<Int>,
    /** Index of the following level, or null after the last one. */
    val nextIndex: Int?,
)

data class GameUiState(
    val entry: LevelEntry,
    val game: GameState,
    /** Indices of crates that can provably no longer reach a target. */
    val stuckCrates: Set<Int>,
    val best: Int?,
    val phase: GamePhase,
)

/** One-shot moments the board turns into motion. */
sealed interface BoardEvent {
    /** The hero walked one cell, onto [to]. */
    data class Stepped(val direction: Direction, val to: Position) : BoardEvent
    data class Pushed(val crate: Int, val direction: Direction, val to: Position, val enteredGoal: Boolean) : BoardEvent

    /**
     * The hero, standing on [at], ran into a wall or into a crate that cannot
     * move ([intoCrate]). [knocked] is false for the repeats of a held
     * direction, which stay quiet.
     */
    data class Bumped(val direction: Direction, val at: Position, val intoCrate: Boolean, val knocked: Boolean) : BoardEvent
    data object Undone : BoardEvent
    data object Restarted : BoardEvent
    data object Solved : BoardEvent
}

/**
 * Runs one level at a time.
 *
 * Input goes through a queue consumed by a single coroutine. The first command
 * runs immediately; the ones typed during an animation wait just long enough
 * for each step to be seen, so a burst of swipes glides instead of teleporting,
 * and the pace quickens while a backlog lasts.
 *
 * Every deliberate press is played: a dropped move in a planned sequence is the
 * worst thing a Sokoban can do to its player. Only the repeats of a *held*
 * control are dropped when the hero is already busy, so letting go stops at
 * once instead of running on.
 */
class GameViewModel(
    private val progressRepository: ProgressRepository,
    private val feedback: GameFeedback,
    private val savedState: SavedStateHandle,
) : ViewModel() {

    private sealed interface Command {
        data class Move(val direction: Direction) : Command
        data class Walk(val target: Position) : Command
        data object Undo : Command
    }

    private val _state = MutableStateFlow<GameUiState?>(null)
    val state: StateFlow<GameUiState?> = _state.asStateFlow()

    private val _events = MutableSharedFlow<BoardEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<BoardEvent> = _events.asSharedFlow()

    private val commands = Channel<Command>(Channel.UNLIMITED)
    private var queued = 0
    private var detector: DeadlockDetector? = null
    private var bests: Map<String, Int> = emptyMap()
    private var victory: Job? = null

    /** Bumped by restart and level loads, so a walk in progress knows to stop. */
    private var generation = 0
    private var lastBumpAt = 0L
    private var lastBumpDirection: Direction? = null

    init {
        viewModelScope.launch {
            progressRepository.progress.collect { progress ->
                bests = progress.bestMoves
                _state.update { it?.copy(best = bests[it.entry.id]) }
            }
        }
        viewModelScope.launch {
            for (command in commands) {
                queued--
                execute(command)
            }
        }
        // Back from process death: rebuild the game from its move log.
        val savedIndex = savedState.get<Int>(KEY_LEVEL)
        if (savedIndex != null && savedIndex in LevelPack.levels.indices) {
            load(savedIndex, savedState.get<String>(KEY_MOVES))
        }
    }

    /**
     * Shows level [index]. A game of that level still in progress is resumed;
     * a finished one starts over.
     */
    fun open(index: Int) {
        val current = _state.value
        if (current?.entry?.index == index && !current.game.isSolved) return
        load(index, moves = null)
    }

    /** [held] marks the automatic repeats of a control that is kept pressed. */
    fun move(direction: Direction, held: Boolean = false) = enqueue(Command.Move(direction), held)

    fun walkTo(target: Position) = enqueue(Command.Walk(target))

    fun undo(held: Boolean = false) = enqueue(Command.Undo, held)

    fun restart() {
        val current = _state.value ?: return
        if (current.game.moves == 0 && current.phase == GamePhase.Playing) return
        victory?.cancel()
        drain()
        generation++
        val fresh = GameEngine.restart(current.game)
        _state.value = current.copy(game = fresh, stuckCrates = emptySet(), phase = GamePhase.Playing)
        savedState[KEY_MOVES] = ""
        feedback.restart()
        _events.tryEmit(BoardEvent.Restarted)
    }

    private fun load(index: Int, moves: String?) {
        victory?.cancel()
        drain()
        generation++
        val entry = LevelPack.entry(index)
        val restored = moves?.takeIf { it.isNotEmpty() }?.let { runCatching { GameEngine.replay(entry.level, it) }.getOrNull() }
        // A game saved at the moment of victory restarts: there is nothing left to play.
        val game = restored?.takeUnless { it.isSolved } ?: GameState.start(entry.level)
        val detector = DeadlockDetector(entry.level).also { detector = it }
        _state.value = GameUiState(
            entry = entry,
            game = game,
            stuckCrates = detector.deadlockedBoxes(game),
            best = bests[entry.id],
            phase = GamePhase.Playing,
        )
        savedState[KEY_LEVEL] = index
        savedState[KEY_MOVES] = game.moveLog()
    }

    private fun enqueue(command: Command, held: Boolean = false) {
        if (_state.value?.phase != GamePhase.Playing) return
        if (queued >= if (held) MAX_QUEUED_HELD else MAX_QUEUED) return
        queued++
        commands.trySend(command)
    }

    private fun drain() {
        while (commands.tryReceive().isSuccess) queued--
    }

    private suspend fun execute(command: Command) {
        when (command) {
            is Command.Move -> step(command.direction)
            is Command.Walk -> walk(command.target)
            Command.Undo -> undoOnce()
        }
    }

    private suspend fun step(direction: Direction) {
        val current = _state.value ?: return
        if (current.phase != GamePhase.Playing) return
        when (val result = GameEngine.move(current.game, direction)) {
            is MoveResult.Walked -> {
                commit(result.state)
                feedback.step()
                _events.tryEmit(BoardEvent.Stepped(direction, result.to))
                pace(STEP_MS)
            }
            is MoveResult.Pushed -> {
                commit(result.state)
                if (result.enteredGoal) feedback.goal() else feedback.push()
                _events.tryEmit(BoardEvent.Pushed(result.boxIndex, direction, result.to, result.enteredGoal))
                if (result.state.isSolved) celebrate() else pace(PUSH_MS)
            }
            is MoveResult.Blocked -> {
                if (result.state !== current.game) commit(result.state)
                // Holding a direction against a wall knocks once, not every repeat.
                val now = SystemClock.uptimeMillis()
                val knocked = direction != lastBumpDirection || now - lastBumpAt > BUMP_REPEAT_QUIET_MS
                if (knocked) feedback.bump()
                lastBumpAt = now
                lastBumpDirection = direction
                _events.tryEmit(BoardEvent.Bumped(direction, result.state.player, result.reason == BlockReason.BOX, knocked))
                pace(BUMP_MS)
            }
        }
    }

    private suspend fun walk(target: Position) {
        val start = _state.value ?: return
        val path = GameEngine.pathTo(start.game, target) ?: return
        val walkGeneration = generation
        for (direction in path) {
            // Any new input, a restart or another level takes over from a walk.
            if (queued > 0 || generation != walkGeneration) return
            val current = _state.value ?: return
            if (current.phase != GamePhase.Playing) return
            val result = GameEngine.move(current.game, direction) as? MoveResult.Walked ?: return
            commit(result.state)
            feedback.step()
            _events.tryEmit(BoardEvent.Stepped(direction, result.to))
            delay(WALK_MS)
        }
    }

    private suspend fun undoOnce() {
        val current = _state.value ?: return
        if (current.phase != GamePhase.Playing) return
        val previous = GameEngine.undo(current.game) ?: return
        commit(previous)
        feedback.undo()
        _events.tryEmit(BoardEvent.Undone)
        pace(UNDO_MS)
    }

    private fun commit(game: GameState) {
        _state.update { it?.copy(game = game, stuckCrates = detector?.deadlockedBoxes(game).orEmpty()) }
        savedState[KEY_MOVES] = game.moveLog()
    }

    /** Leaves each step visible; a backlog of input speeds the pace up. */
    private suspend fun pace(stepMs: Long) = delay(
        when {
            queued >= HURRY_AT -> stepMs * 3 / 5
            queued > 0 -> stepMs * 3 / 4
            else -> stepMs
        },
    )

    private fun celebrate() {
        val current = _state.value ?: return
        drain()
        _state.value = current.copy(phase = GamePhase.Celebrating)
        _events.tryEmit(BoardEvent.Solved)
        val entry = current.entry
        val moves = current.game.moves
        // The solve is recorded at once and on its own: restarting or leaving
        // during the celebration must never cost the player a finished level.
        val saving = viewModelScope.async {
            try {
                progressRepository.complete(entry.index, moves, entry.par)
            } catch (error: IOException) {
                Log.w(TAG, "Could not save progress", error)
                null
            }
        }
        victory = viewModelScope.launch {
            // Let the last crate land and light up before anything else happens.
            delay(CELEBRATION_MS)
            feedback.victory()
            val completion = saving.await()
            val ids = LevelPack.levels.map { it.id }
            val previousBest = completion?.previousBest ?: bests[entry.id]
            val result = LevelResult(
                moves = moves,
                par = entry.par,
                stars = completion?.stars ?: ProgressRules.stars(moves, entry.par),
                isNewBest = completion?.isNewBest ?: (previousBest == null || moves < previousBest),
                previousBest = previousBest,
                unlockedNumbers = completion?.newlyUnlocked.orEmpty().map { ids.indexOf(it) + 1 },
                nextIndex = (entry.index + 1).takeIf { it < LevelPack.size },
            )
            _state.update { state -> state?.takeIf { it.entry.index == entry.index }?.copy(phase = GamePhase.Complete(result)) }
        }
    }

    companion object {
        private const val TAG = "GameViewModel"
        private const val KEY_LEVEL = "level"
        private const val KEY_MOVES = "moves"

        /** Deliberate presses allowed to wait behind the one running. */
        private const val MAX_QUEUED = 16

        /** A held control buffers a single repeat: releasing it stops the hero. */
        private const val MAX_QUEUED_HELD = 1

        /** Backlog from which the pace goes from brisk to hurried. */
        private const val HURRY_AT = 3

        const val STEP_MS = 105L
        const val PUSH_MS = 115L
        const val WALK_MS = 80L
        const val UNDO_MS = 85L
        const val BUMP_MS = 110L
        const val CELEBRATION_MS = 700L
        private const val BUMP_REPEAT_QUIET_MS = 400L

        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as SokobanApplication).container
                GameViewModel(container.progressRepository, container.feedback, createSavedStateHandle())
            }
        }
    }
}
