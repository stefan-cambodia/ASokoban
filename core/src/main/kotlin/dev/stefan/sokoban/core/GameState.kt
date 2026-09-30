package dev.stefan.sokoban.core

/**
 * An immutable snapshot of a game in progress.
 *
 * Boxes are a list rather than a set: a box keeps its index for the whole game,
 * which gives the UI a stable identity to animate each box individually.
 *
 * History is a persistent linked stack shared between successive states, so a
 * move and an undo are both O(1) and undo is unlimited at negligible cost.
 */
class GameState private constructor(
    val level: Level,
    val player: Position,
    val boxes: List<Position>,
    /** The way the player looks. Purely cosmetic, but restored by undo. */
    val facing: Direction,
    val moves: Int,
    val pushes: Int,
    private val history: HistoryEntry?,
) {

    /** One undoable step: the state before it, and the move that left it. */
    private class HistoryEntry(
        val player: Position,
        val boxes: List<Position>,
        val facing: Direction,
        val moves: Int,
        val pushes: Int,
        val direction: Direction,
        val pushed: Boolean,
        val previous: HistoryEntry?,
    )

    val boxesOnGoals: Int = boxes.count { level.isGoal(it) }

    val isSolved: Boolean = boxesOnGoals == boxes.size

    val canUndo: Boolean get() = history != null

    /** Index of the box on [position], or -1. */
    fun boxAt(position: Position): Int = boxes.indexOf(position)

    fun hasBoxAt(position: Position): Boolean = boxAt(position) >= 0

    /** A cell the player could step into right now. */
    fun isFree(position: Position): Boolean = level.isWalkable(position) && !hasBoxAt(position)

    /**
     * The moves played so far in LURD notation (upper case for pushes), oldest
     * first. Replaying it with [GameEngine.replay] rebuilds this exact state.
     */
    fun moveLog(): String {
        val symbols = StringBuilder()
        var entry = history
        while (entry != null) {
            symbols.append(if (entry.pushed) entry.direction.symbol.uppercaseChar() else entry.direction.symbol)
            entry = entry.previous
        }
        return symbols.reverse().toString()
    }

    internal fun advancedTo(player: Position, boxes: List<Position>, facing: Direction, pushed: Boolean) = GameState(
        level = level,
        player = player,
        boxes = boxes,
        facing = facing,
        moves = moves + 1,
        pushes = if (pushed) pushes + 1 else pushes,
        history = HistoryEntry(this.player, this.boxes, this.facing, moves, pushes, facing, pushed, history),
    )

    /** Same position, player turned towards [direction]: no move, no history. */
    internal fun facing(direction: Direction): GameState =
        if (direction == facing) this else GameState(level, player, boxes, direction, moves, pushes, history)

    internal fun previous(): GameState? {
        val entry = history ?: return null
        return GameState(level, entry.player, entry.boxes, entry.facing, entry.moves, entry.pushes, entry.previous)
    }

    companion object {
        fun start(level: Level): GameState = GameState(
            level = level,
            player = level.playerStart,
            boxes = level.boxStarts,
            facing = Direction.DOWN,
            moves = 0,
            pushes = 0,
            history = null,
        )
    }
}
