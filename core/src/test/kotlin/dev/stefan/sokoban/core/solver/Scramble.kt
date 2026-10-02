package dev.stefan.sokoban.core.solver

import dev.stefan.sokoban.core.Direction
import dev.stefan.sokoban.core.Level
import dev.stefan.sokoban.core.Position
import kotlin.math.abs
import kotlin.random.Random

/**
 * Random backward play from the solved position of a room, for rooms too large
 * for [ReverseSearch] to exhaust.
 *
 * Starting with every box on a goal, the player *pulls* boxes at random, often
 * the same box several times in a row so that boxes travel. Every layout it
 * reaches is solvable by construction. A walk keeps the layout that scores best
 * on spread boxes (distance from their own goal) times how often the pulled box
 * changed, which favours puzzles where boxes have to be untangled rather than
 * merely carried home.
 */
class Scramble(private val level: Level) {

    class Candidate(val boxes: List<Position>, val playerArea: List<Position>, val score: Int)

    private val width = level.width
    private val cellCount = level.width * level.height
    private val walkable = BooleanArray(cellCount) { level.isWalkable(Position(it % width, it / width)) }
    private val offsets = Direction.entries.map { it.dy * width + it.dx }.toIntArray()
    private val goals = level.goals.map { it.y * width + it.x }.toIntArray()
    private val isGoal = BooleanArray(cellCount).also { g -> goals.forEach { g[it] = true } }

    /** One walk of [steps] pulls; null when the player can never pull anything. */
    fun walk(random: Random, steps: Int, stickiness: Double = 0.75): Candidate? {
        val boxes = goals.copyOf()
        val occupied = BooleanArray(cellCount).also { o -> boxes.forEach { o[it] = true } }
        val free = (0 until cellCount).filter { walkable[it] && !occupied[it] }
        if (free.isEmpty()) return null
        var player = free.random(random)

        var last = -1
        var swaps = 0
        var bestScore = 0
        var best: Pair<IntArray, Int>? = null
        val reach = BooleanArray(cellCount)
        repeat(steps) {
            reachable(player, occupied, reach)
            val pulls = ArrayList<Long>()
            for (slot in boxes.indices) for (offset in offsets) {
                val stand = boxes[slot] + offset
                val back = stand + offset
                if (back !in 0 until cellCount || stand !in 0 until cellCount) continue
                if (!reach[stand] || !walkable[back] || occupied[back]) continue
                pulls += slot.toLong() shl 32 or (offset.toLong() and 0xffffffffL)
            }
            if (pulls.isEmpty()) return@repeat
            val sticky = pulls.filter { (it shr 32).toInt() == last }
            val pick = if (sticky.isNotEmpty() && random.nextDouble() < stickiness) sticky.random(random) else pulls.random(random)
            val slot = (pick shr 32).toInt()
            val offset = pick.toInt()
            if (slot != last) swaps++
            last = slot
            occupied[boxes[slot]] = false
            boxes[slot] += offset
            occupied[boxes[slot]] = true
            player = boxes[slot] + offset

            val onGoal = boxes.count { isGoal[it] }
            val spread = boxes.indices.sumOf { distance(boxes[it], goals[it]) }
            val score = swaps * spread / (1 + 2 * onGoal)
            if (score > bestScore) {
                bestScore = score
                best = boxes.copyOf() to player
            }
        }
        val (layout, at) = best ?: return null
        val occupiedAtBest = BooleanArray(cellCount).also { o -> layout.forEach { o[it] = true } }
        reachable(at, occupiedAtBest, reach)
        return Candidate(
            boxes = layout.sorted().map { position(it) },
            playerArea = (0 until cellCount).filter { reach[it] }.map { position(it) },
            score = bestScore,
        )
    }

    private fun reachable(start: Int, occupied: BooleanArray, reach: BooleanArray) {
        reach.fill(false)
        val queue = IntArray(cellCount)
        var head = 0
        var tail = 0
        reach[start] = true
        queue[tail++] = start
        while (head < tail) {
            val cell = queue[head++]
            for (offset in offsets) {
                val next = cell + offset
                if (next !in 0 until cellCount || reach[next] || !walkable[next] || occupied[next]) continue
                reach[next] = true
                queue[tail++] = next
            }
        }
    }

    private fun distance(a: Int, b: Int) = abs(a % width - b % width) + abs(a / width - b / width)

    private fun position(cell: Int) = Position(cell % width, cell / width)
}
