package dev.stefan.sokoban.core

/**
 * Finds boxes that can provably never reach a goal again.
 *
 * Only the two classic checks with no false positives are used, because telling
 * a player "stuck" when they are not is worse than saying nothing:
 *
 * 1. **Dead squares.** A box can reach a goal only through cells from which it
 *    could be *pulled* back to a goal. Cells outside that set (typically
 *    corners and walls without goals) are dead for boxes, whatever the rest of
 *    the board looks like.
 * 2. **Freeze deadlocks.** A box that can move neither horizontally nor
 *    vertically — blocked by walls, dead squares, or other boxes that are
 *    themselves frozen — stays where it is forever. If it is not on a goal, the
 *    level is lost.
 *
 * Build one detector per level: the dead squares are computed once.
 */
class DeadlockDetector(private val level: Level) {

    private val live = BooleanArray(level.width * level.height)

    init {
        // Reverse search: pull a box away from every goal. Pulling a box from
        // `cell` towards `direction` needs the player on the next cell in that
        // direction, and room for the player to step back one more cell.
        val queue = ArrayDeque<Position>()
        for (goal in level.goals) {
            live[index(goal)] = true
            queue.addLast(goal)
        }
        while (queue.isNotEmpty()) {
            val cell = queue.removeFirst()
            for (direction in Direction.entries) {
                val boxTo = cell + direction
                val playerTo = boxTo + direction
                if (!level.isWalkable(boxTo) || !level.isWalkable(playerTo)) continue
                if (live[index(boxTo)]) continue
                live[index(boxTo)] = true
                queue.addLast(boxTo)
            }
        }
    }

    /** True when a box on [position] can never be brought to any goal. */
    fun isDeadSquare(position: Position): Boolean = level.isWalkable(position) && !live[index(position)]

    /** Indices of the boxes that are deadlocked in [state]. Empty when the game is still winnable by these checks. */
    fun deadlockedBoxes(state: GameState): Set<Int> {
        val occupied = state.boxes.toHashSet()
        val result = HashSet<Int>()
        state.boxes.forEachIndexed { index, box ->
            if (level.isGoal(box)) return@forEachIndexed
            if (isDeadSquare(box) || isFrozen(box, occupied)) result += index
        }
        return result
    }

    fun isDeadlocked(state: GameState): Boolean = deadlockedBoxes(state).isNotEmpty()

    private fun isFrozen(box: Position, occupied: Set<Position>): Boolean {
        val walls = HashSet<Position>()
        return isBlocked(box, horizontal = true, occupied, walls) &&
            isBlocked(box, horizontal = false, occupied, walls)
    }

    /**
     * Whether the box on [box] cannot move along one axis. While a box is being
     * examined it counts as a wall for its neighbours ([asWalls]), which both
     * breaks cycles and matches reality: a frozen neighbour pins this box too.
     */
    private fun isBlocked(
        box: Position,
        horizontal: Boolean,
        occupied: Set<Position>,
        asWalls: MutableSet<Position>,
    ): Boolean {
        val (sideA, sideB) = if (horizontal) {
            box + Direction.LEFT to box + Direction.RIGHT
        } else {
            box + Direction.UP to box + Direction.DOWN
        }
        val solid = { cell: Position -> !level.isWalkable(cell) || cell in asWalls }
        if (solid(sideA) || solid(sideB)) return true
        if (isDeadSquare(sideA) && isDeadSquare(sideB)) return true

        asWalls += box
        try {
            for (side in listOf(sideA, sideB)) {
                if (side in occupied && isBlocked(side, !horizontal, occupied, asWalls)) return true
            }
            return false
        } finally {
            asWalls -= box
        }
    }

    private fun index(position: Position) = position.y * level.width + position.x
}
