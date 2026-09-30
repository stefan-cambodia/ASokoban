package dev.stefan.sokoban.core.solver

import dev.stefan.sokoban.core.DeadlockDetector
import dev.stefan.sokoban.core.Direction
import dev.stefan.sokoban.core.Level
import dev.stefan.sokoban.core.Position

/**
 * A* Sokoban solver, used to prove levels solvable and to measure them.
 *
 * The search runs over *pushes*: between two pushes the player simply walks, so
 * a state is the box layout plus where the player stands. With [Metric.MOVES]
 * the player's exact cell is part of the state and the cost of a push is the
 * walk to it plus one, which makes the result move-optimal. With
 * [Metric.PUSHES] the player is reduced to the area it can reach, which shrinks
 * the search a lot and makes the result push-optimal.
 *
 * The heuristic — the sum of each box's pull-distance to its nearest goal — is
 * admissible and consistent for both metrics. Dead squares and 2x2 freezes are
 * pruned; both are exact, so pruning never loses a solution.
 */
class Solver(private val level: Level) {

    enum class Metric { MOVES, PUSHES }

    sealed interface Result {
        val explored: Int

        data class Solved(val lurd: String, val moves: Int, val pushes: Int, override val explored: Int) : Result
        data class Unsolvable(override val explored: Int) : Result
        data class LimitReached(override val explored: Int) : Result
    }

    private val width = level.width
    private val cellCount = level.width * level.height
    private val walkable = BooleanArray(cellCount) { level.isWalkable(position(it)) }
    private val goal = BooleanArray(cellCount) { level.isGoal(position(it)) }
    private val dead: BooleanArray
    private val goalDistance: IntArray

    /** Cell offsets in [Direction.entries] order. */
    private val offsets = Direction.entries.map { it.dy * width + it.dx }.toIntArray()

    init {
        val detector = DeadlockDetector(level)
        dead = BooleanArray(cellCount) { walkable[it] && detector.isDeadSquare(position(it)) }
        goalDistance = IntArray(cellCount) { Int.MAX_VALUE }
        // Pull distances from every goal at once: a multi-source BFS in reverse.
        val queue = ArrayDeque<Int>()
        for (cell in 0 until cellCount) if (goal[cell]) {
            goalDistance[cell] = 0
            queue.addLast(cell)
        }
        while (queue.isNotEmpty()) {
            val cell = queue.removeFirst()
            for (offset in offsets) {
                val boxTo = cell + offset
                val playerTo = boxTo + offset
                if (!inBoard(playerTo) || !walkable[boxTo] || !walkable[playerTo]) continue
                if (goalDistance[boxTo] != Int.MAX_VALUE) continue
                goalDistance[boxTo] = goalDistance[cell] + 1
                queue.addLast(boxTo)
            }
        }
    }

    private class Key(val boxes: IntArray, val player: Int) {
        private val hash = boxes.contentHashCode() * 31 + player
        override fun hashCode() = hash
        override fun equals(other: Any?) = other is Key && other.player == player && other.boxes.contentEquals(boxes)
    }

    private class Node(
        val key: Key,
        val boxes: IntArray,
        val player: Int,
        val cost: Int,
        val parent: Node?,
        /** Cell the pushed box left, and push direction index; -1 for the root. */
        val pushFrom: Int,
        val direction: Int,
    )

    fun solve(metric: Metric = Metric.PUSHES, maxExplored: Int = 2_000_000): Result {
        val startBoxes = level.boxStarts.map { index(it) }.sorted().toIntArray()
        val startPlayer = index(level.playerStart)
        if (startBoxes.any { dead[it] && !goal[it] }) return Result.Unsolvable(0)

        val best = HashMap<Key, Int>()
        val buckets = ArrayList<ArrayList<Node>>()
        fun enqueue(node: Node) {
            val f = node.cost + heuristic(node.boxes)
            while (buckets.size <= f) buckets += ArrayList<Node>()
            buckets[f].add(node)
        }

        val root = Node(key(startBoxes, startPlayer, metric), startBoxes, startPlayer, 0, null, -1, -1)
        best[root.key] = 0
        enqueue(root)

        val reach = IntArray(cellCount)
        val occupied = BooleanArray(cellCount)
        var explored = 0
        var bucket = 0
        while (true) {
            while (bucket < buckets.size && buckets[bucket].isEmpty()) bucket++
            if (bucket >= buckets.size) return Result.Unsolvable(explored)
            val node = buckets[bucket].removeAt(buckets[bucket].lastIndex)
            if ((best[node.key] ?: Int.MAX_VALUE) < node.cost) continue

            if (node.boxes.all { goal[it] }) return solution(node, explored)
            if (++explored > maxExplored) return Result.LimitReached(explored)

            node.boxes.forEach { occupied[it] = true }
            walkDistances(node.player, occupied, reach)
            for (boxSlot in node.boxes.indices) {
                val box = node.boxes[boxSlot]
                for (direction in offsets.indices) {
                    val offset = offsets[direction]
                    val pusher = box - offset
                    val target = box + offset
                    if (!inBoard(pusher) || reach[pusher] < 0) continue
                    if (!walkable[target] || occupied[target] || dead[target]) continue

                    occupied[box] = false
                    occupied[target] = true
                    val frozen = formsFrozenSquare(target, occupied)
                    occupied[target] = false
                    occupied[box] = true
                    if (frozen) continue

                    val boxes = node.boxes.copyOf()
                    boxes[boxSlot] = target
                    boxes.sort()
                    val step = if (metric == Metric.MOVES) reach[pusher] + 1 else 1
                    val childKey = key(boxes, box, metric)
                    val child = Node(childKey, boxes, box, node.cost + step, node, box, direction)
                    val known = best[childKey]
                    if (known != null && known <= child.cost) continue
                    best[childKey] = child.cost
                    enqueue(child)
                }
            }
            node.boxes.forEach { occupied[it] = false }
        }
    }

    private fun heuristic(boxes: IntArray): Int = boxes.sumOf { goalDistance[it] }

    /**
     * A 2x2 square of walls and boxes around [cell] can never be undone. It is a
     * deadlock unless every box in it already sits on a goal.
     */
    private fun formsFrozenSquare(cell: Int, occupied: BooleanArray): Boolean {
        val x = cell % width
        val y = cell / width
        for (dx in -1..0) for (dy in -1..0) {
            var solid = true
            var boxOffGoal = false
            for (ox in 0..1) for (oy in 0..1) {
                val cx = x + dx + ox
                val cy = y + dy + oy
                val inside = cx in 0 until width && cy in 0 until level.height
                val c = cy * width + cx
                val wall = !inside || !walkable[c]
                val box = inside && occupied[c]
                if (!wall && !box) solid = false
                if (box && !goal[c]) boxOffGoal = true
            }
            if (solid && boxOffGoal) return true
        }
        return false
    }

    /** BFS walking distances from [start]; -1 for unreachable cells. */
    private fun walkDistances(start: Int, occupied: BooleanArray, distances: IntArray) {
        distances.fill(-1)
        val queue = IntArray(cellCount)
        var head = 0
        var tail = 0
        distances[start] = 0
        queue[tail++] = start
        while (head < tail) {
            val cell = queue[head++]
            for (offset in offsets) {
                val next = cell + offset
                if (!inBoard(next) || !walkable[next] || occupied[next] || distances[next] >= 0) continue
                distances[next] = distances[cell] + 1
                queue[tail++] = next
            }
        }
    }

    private fun key(boxes: IntArray, player: Int, metric: Metric): Key {
        if (metric == Metric.MOVES) return Key(boxes, player)
        // Normalise the player to the smallest cell of its reachable area.
        val occupied = BooleanArray(cellCount)
        boxes.forEach { occupied[it] = true }
        val distances = IntArray(cellCount)
        walkDistances(player, occupied, distances)
        return Key(boxes, distances.indexOfFirst { it >= 0 })
    }

    private fun solution(end: Node, explored: Int): Result.Solved {
        val chain = generateSequence(end) { it.parent }.toList().reversed()
        val lurd = StringBuilder()
        for (i in 1 until chain.size) {
            val before = chain[i - 1]
            val step = chain[i]
            val offset = offsets[step.direction]
            val occupied = BooleanArray(cellCount)
            before.boxes.forEach { occupied[it] = true }
            lurd.append(walk(before.player, step.pushFrom - offset, occupied))
            lurd.append(Direction.entries[step.direction].symbol.uppercaseChar())
        }
        val text = lurd.toString()
        return Result.Solved(text, text.length, text.count { it.isUpperCase() }, explored)
    }

    private fun walk(from: Int, to: Int, occupied: BooleanArray): String {
        val distances = IntArray(cellCount)
        walkDistances(to, occupied, distances)
        check(distances[from] >= 0) { "Solver produced an unreachable push" }
        val path = StringBuilder()
        var cell = from
        while (cell != to) {
            val direction = offsets.indices.first { d ->
                val next = cell + offsets[d]
                inBoard(next) && distances[next] == distances[cell] - 1
            }
            path.append(Direction.entries[direction].symbol)
            cell += offsets[direction]
        }
        return path.toString()
    }

    private fun inBoard(cell: Int) = cell in 0 until cellCount
    private fun index(position: Position) = position.y * width + position.x
    private fun position(index: Int) = Position(index % width, index / width)
}
