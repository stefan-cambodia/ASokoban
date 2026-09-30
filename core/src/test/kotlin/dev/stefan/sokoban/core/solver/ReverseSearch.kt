package dev.stefan.sokoban.core.solver

import dev.stefan.sokoban.core.Direction
import dev.stefan.sokoban.core.Level
import dev.stefan.sokoban.core.Position

/**
 * Exhaustive backward search from the solved position of a room.
 *
 * Starting with every box on a goal, it *pulls* boxes breadth-first. Every state
 * it reaches is solvable by construction (play the pulls backwards as pushes),
 * and its BFS depth is exactly the minimum number of pushes needed to solve it.
 *
 * This is a design tool: the author draws walls and goals, and the search
 * proposes the start positions that demand the longest solutions.
 */
class ReverseSearch(private val level: Level) {

    class Candidate(val boxes: List<Position>, val playerArea: List<Position>, val depth: Int)

    class Outcome(val maxDepth: Int, val states: Int, val candidates: Map<Int, List<Candidate>>)

    private val width = level.width
    private val cellCount = level.width * level.height
    private val walkable = BooleanArray(cellCount) { level.isWalkable(Position(it % width, it / width)) }
    private val offsets = Direction.entries.map { it.dy * width + it.dx }.toIntArray()

    private class Key(val boxes: IntArray, val player: Int) {
        private val hash = boxes.contentHashCode() * 31 + player
        override fun hashCode() = hash
        override fun equals(other: Any?) = other is Key && other.player == player && other.boxes.contentEquals(boxes)
    }

    /**
     * Runs the search, keeping up to [samplesPerDepth] states for each of the
     * deepest [keepDepths] layers.
     */
    fun run(maxStates: Int = 6_000_000, samplesPerDepth: Int = 400, keepDepths: Int = 12): Outcome {
        val goals = level.goals.map { it.y * width + it.x }.sorted().toIntArray()
        val visited = HashSet<Key>()
        var frontier = ArrayList<Key>()

        val occupied = BooleanArray(cellCount)
        goals.forEach { occupied[it] = true }
        val seen = BooleanArray(cellCount)
        for (cell in 0 until cellCount) {
            if (!walkable[cell] || occupied[cell] || seen[cell]) continue
            val area = area(cell, occupied)
            area.forEach { seen[it] = true }
            val key = Key(goals, area.min())
            if (visited.add(key)) frontier.add(key)
        }

        val layers = ArrayDeque<Pair<Int, List<Key>>>()
        var depth = 0
        while (frontier.isNotEmpty()) {
            layers.addLast(depth to frontier.shuffledSample(samplesPerDepth))
            if (layers.size > keepDepths) layers.removeFirst()
            if (visited.size > maxStates) error("State limit reached at depth $depth (${visited.size} states)")

            val next = ArrayList<Key>()
            for (state in frontier) {
                state.boxes.forEach { occupied[it] = true }
                val area = area(state.player, occupied)
                val reachable = BooleanArray(cellCount).also { r -> area.forEach { r[it] = true } }
                for (slot in state.boxes.indices) {
                    val box = state.boxes[slot]
                    for (offset in offsets) {
                        val stand = box + offset
                        val back = stand + offset
                        if (back !in 0 until cellCount || !reachable[stand]) continue
                        if (!walkable[back] || occupied[back]) continue
                        val boxes = state.boxes.copyOf()
                        boxes[slot] = stand
                        boxes.sort()
                        occupied[box] = false
                        occupied[stand] = true
                        val key = Key(boxes, area(back, occupied).min())
                        occupied[stand] = false
                        occupied[box] = true
                        if (visited.add(key)) next.add(key)
                    }
                }
                state.boxes.forEach { occupied[it] = false }
            }
            if (next.isEmpty()) break
            frontier = next
            depth++
        }

        val candidates = layers.associate { (layerDepth, keys) ->
            layerDepth to keys.map { key ->
                val occupiedHere = BooleanArray(cellCount).also { o -> key.boxes.forEach { o[it] = true } }
                Candidate(
                    boxes = key.boxes.map { position(it) },
                    playerArea = area(key.player, occupiedHere).map { position(it) },
                    depth = layerDepth,
                )
            }
        }
        return Outcome(depth, visited.size, candidates)
    }

    private fun area(start: Int, occupied: BooleanArray): IntArray {
        val result = ArrayList<Int>()
        val seen = BooleanArray(cellCount)
        val queue = ArrayDeque<Int>()
        queue.addLast(start)
        seen[start] = true
        while (queue.isNotEmpty()) {
            val cell = queue.removeFirst()
            result += cell
            for (offset in offsets) {
                val next = cell + offset
                if (next !in 0 until cellCount || seen[next] || !walkable[next] || occupied[next]) continue
                seen[next] = true
                queue.addLast(next)
            }
        }
        return result.toIntArray()
    }

    private fun <T> List<T>.shuffledSample(count: Int): List<T> =
        if (size <= count) toList() else shuffled(kotlin.random.Random(size.toLong())).take(count)

    private fun position(cell: Int) = Position(cell % width, cell / width)
}
