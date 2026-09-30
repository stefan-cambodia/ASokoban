package dev.stefan.sokoban.core

/** What a move attempt did. Every outcome carries the resulting [state]. */
sealed interface MoveResult {
    val state: GameState

    /** The player stepped onto a free cell. */
    data class Walked(override val state: GameState, val from: Position, val to: Position) : MoveResult

    /** The player pushed box [boxIndex] one cell. */
    data class Pushed(
        override val state: GameState,
        val boxIndex: Int,
        val from: Position,
        val to: Position,
        /** The box arrived on a goal. */
        val enteredGoal: Boolean,
        /** The box left a goal it was sitting on. */
        val leftGoal: Boolean,
    ) : MoveResult

    /** Nothing moved. The player still turns to face [direction]. */
    data class Blocked(override val state: GameState, val direction: Direction, val reason: BlockReason) : MoveResult
}

enum class BlockReason {
    /** A wall is in the way. */
    WALL,

    /** A box is in the way and cannot move: wall or second box behind it. */
    BOX,

    /** The level is already solved: moves are no longer accepted. */
    SOLVED,
}

/**
 * The rules of classic Sokoban, as pure functions over [GameState]:
 *
 * - the player moves one cell up, down, left or right;
 * - walls stop the player;
 * - walking into a box pushes it, if the cell behind it is free floor;
 * - boxes can never be pulled, and only one box can be pushed at a time;
 * - the level is solved when every box sits on a goal.
 */
object GameEngine {

    fun move(state: GameState, direction: Direction): MoveResult {
        if (state.isSolved) return MoveResult.Blocked(state, direction, BlockReason.SOLVED)

        val target = state.player + direction
        if (!state.level.isWalkable(target)) {
            return MoveResult.Blocked(state.facing(direction), direction, BlockReason.WALL)
        }

        val boxIndex = state.boxAt(target)
        if (boxIndex < 0) {
            val next = state.advancedTo(target, state.boxes, direction, pushed = false)
            return MoveResult.Walked(next, state.player, target)
        }

        val boxTarget = target + direction
        if (!state.isFree(boxTarget)) {
            return MoveResult.Blocked(state.facing(direction), direction, BlockReason.BOX)
        }
        val boxes = state.boxes.toMutableList().also { it[boxIndex] = boxTarget }
        val next = state.advancedTo(target, boxes, direction, pushed = true)
        return MoveResult.Pushed(
            state = next,
            boxIndex = boxIndex,
            from = target,
            to = boxTarget,
            enteredGoal = state.level.isGoal(boxTarget),
            leftGoal = state.level.isGoal(target),
        )
    }

    /** The state before the last move, or null when there is nothing to undo. */
    fun undo(state: GameState): GameState? = state.previous()

    /** The exact initial state of the level, with an empty history. */
    fun restart(state: GameState): GameState = GameState.start(state.level)

    /**
     * Shortest walk from the player to [target] that pushes nothing, or null when
     * [target] is unreachable. An empty list means the player is already there.
     */
    fun pathTo(state: GameState, target: Position): List<Direction>? {
        if (target == state.player) return emptyList()
        if (!state.isFree(target)) return null

        val level = state.level
        val cameFrom = arrayOfNulls<Direction>(level.width * level.height)
        val queue = ArrayDeque<Position>()
        val visited = BooleanArray(level.width * level.height)
        visited[state.player.y * level.width + state.player.x] = true
        queue.addLast(state.player)
        while (queue.isNotEmpty()) {
            val cell = queue.removeFirst()
            if (cell == target) break
            for (direction in Direction.entries) {
                val next = cell + direction
                if (!state.isFree(next)) continue
                val index = next.y * level.width + next.x
                if (visited[index]) continue
                visited[index] = true
                cameFrom[index] = direction
                queue.addLast(next)
            }
        }
        if (!visited[target.y * level.width + target.x]) return null

        val path = ArrayList<Direction>()
        var cell = target
        while (cell != state.player) {
            val direction = cameFrom[cell.y * level.width + cell.x] ?: return null
            path += direction
            cell -= direction
        }
        path.reverse()
        return path
    }

    /**
     * Plays a LURD move string from the start of [level]. Letter case is ignored:
     * whether a step pushes is decided by the rules, not by the notation.
     *
     * @throws IllegalArgumentException on an unknown letter or a blocked move.
     */
    fun replay(level: Level, moves: String): GameState {
        var state = GameState.start(level)
        moves.forEachIndexed { index, symbol ->
            val direction = requireNotNull(Direction.fromSymbol(symbol)) { "Unknown move '$symbol' at $index" }
            val result = move(state, direction)
            require(result !is MoveResult.Blocked) { "Move '$symbol' at $index is blocked (${(result as MoveResult.Blocked).reason})" }
            state = result.state
        }
        return state
    }
}
