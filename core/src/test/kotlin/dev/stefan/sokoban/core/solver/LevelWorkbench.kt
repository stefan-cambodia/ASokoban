package dev.stefan.sokoban.core.solver

import dev.stefan.sokoban.core.GameEngine
import dev.stefan.sokoban.core.LevelParser
import dev.stefan.sokoban.core.Position
import dev.stefan.sokoban.core.levels.LevelPack
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import java.io.File
import java.util.concurrent.ForkJoinPool
import kotlin.random.Random
import kotlin.time.measureTimedValue

/**
 * Level design tools. Not part of the regular test run: each one is enabled by a
 * Gradle property, for example
 *
 * ```
 * ./gradlew :core:test --tests '*LevelWorkbench*' -Psokoban.report=/tmp/report.txt --rerun
 * ./gradlew :core:test --tests '*LevelWorkbench*' -Psokoban.generate=/tmp/rooms.txt --rerun
 * ./gradlew :core:test --tests '*LevelWorkbench*' -Psokoban.scramble=/tmp/big-rooms.txt --rerun
 * ```
 */
class LevelWorkbench {

    /**
     * Solves every level of the pack and writes, per level, the move-optimal
     * solution when the search affords it, plus push count and search effort.
     */
    @Test
    @EnabledIfSystemProperty(named = "sokoban.report", matches = ".+")
    fun report() {
        val output = File(System.getProperty("sokoban.report"))
        val lines = mutableListOf<String>()
        val solutions = mutableListOf<String>()
        for (entry in LevelPack.levels) {
            val level = entry.level
            val (result, time) = measureTimedValue { Solver(level).solve(Solver.Metric.MOVES, 1_500_000) }
            val pushResult = Solver(level).solve(Solver.Metric.PUSHES, 3_000_000)
            val moves = (result as? Solver.Result.Solved)?.moves
            val pushes = (pushResult as? Solver.Result.Solved)?.pushes
            lines += "%-6s %-22s %2dx%-2d boxes=%d par=%-4d moves=%-5s pushes=%-4s effort=%-8d %s".format(
                entry.id, entry.name, level.width, level.height, level.boxStarts.size, entry.par,
                moves ?: result::class.simpleName, pushes ?: pushResult::class.simpleName,
                result.explored, time,
            )
            val solved = (result as? Solver.Result.Solved) ?: (pushResult as? Solver.Result.Solved)
            if (solved != null) solutions += "${entry.id} ${solved.lurd}"
        }
        output.writeText(lines.joinToString("\n") + "\n\n" + solutions.joinToString("\n") + "\n")
    }

    /**
     * Reads room designs — walls, goals and a player marking the inside — and
     * writes the hardest start positions the reverse search finds for each.
     *
     * Input blocks are separated by blank lines. A block may start with
     * `; name`, and `; depth -k` to also propose layouts k pushes below the
     * maximum (easier variants).
     */
    @Test
    @EnabledIfSystemProperty(named = "sokoban.generate", matches = ".+")
    fun generate() {
        val input = File(System.getProperty("sokoban.generate"))
        val output = StringBuilder()
        val blocks = input.readText().split(Regex("\\n\\s*\\n")).filter { it.isNotBlank() }
        for (block in blocks) {
            val meta = block.lines().filter { it.startsWith(";") }.map { it.drop(1).trim() }
            val name = meta.firstOrNull { !it.startsWith("depth") } ?: "room"
            val below = meta.firstOrNull { it.startsWith("depth") }?.substringAfter("-")?.trim()?.toIntOrNull() ?: 0
            val map = block.lines().filter { !it.startsWith(";") }.joinToString("\n")
                .replace('.', '*').replace('$', ' ')
            val room = runCatching { LevelParser.parse(map) }.getOrElse {
                output.appendLine("=== $name: INVALID ${it.message}\n")
                continue
            }
            val (result, time) = measureTimedValue { runCatching { ReverseSearch(room).run() } }
            val outcome = result.getOrElse {
                output.appendLine("=== $name: FAILED ${it.message}\n")
                continue
            }
            output.appendLine("=== $name: max depth ${outcome.maxDepth}, ${outcome.states} states, $time")

            for (depth in listOf(outcome.maxDepth, outcome.maxDepth - below).distinct()) {
                val pool = outcome.candidates[depth] ?: continue
                // Prefer layouts with every box off its goal, then the ones the
                // forward solver finds hardest.
                val scored = pool
                    .sortedBy { candidate -> candidate.boxes.count { room.isGoal(it) } }
                    .take(if (outcome.states > 100_000) 8 else 40)
                    .mapNotNull { candidate ->
                        val text = render(room, candidate.boxes, pickPlayer(candidate.playerArea))
                        val level = LevelParser.parse(text)
                        // Move-optimal when affordable; the hardest rooms fall back to push-optimal.
                        val solved = Solver(level).solve(Solver.Metric.MOVES, 800_000) as? Solver.Result.Solved
                            ?: Solver(level).solve(Solver.Metric.PUSHES, 3_000_000) as? Solver.Result.Solved
                            ?: return@mapNotNull null
                        check(GameEngine.replay(level, solved.lurd).isSolved)
                        Triple(text, solved, candidate.boxes.count { room.isGoal(it) })
                    }
                    .sortedByDescending { (_, solved, onGoal) -> solved.explored / (1 + onGoal) }
                    .take(3)
                for ((text, solved, onGoal) in scored) {
                    output.appendLine(
                        "--- depth=$depth moves=${solved.moves} pushes=${solved.pushes} " +
                            "effort=${solved.explored} onGoal=$onGoal",
                    )
                    output.appendLine(text)
                    output.appendLine("; ${solved.lurd}")
                }
            }
            output.appendLine()
        }
        File(input.path + ".out").writeText(output.toString())
    }

    /**
     * Like [generate], for rooms too large to search exhaustively: random
     * backward walks ([Scramble]) propose layouts, the forward solver keeps the
     * ones it can prove, and the hardest of those are written out.
     *
     * Blocks take the same format as for [generate]; `; seed n` changes the
     * walks of a room.
     */
    @Test
    @EnabledIfSystemProperty(named = "sokoban.scramble", matches = ".+")
    fun scramble() {
        val input = File(System.getProperty("sokoban.scramble"))
        val output = StringBuilder()
        val blocks = input.readText().split(Regex("\\n\\s*\\n")).filter { it.isNotBlank() }
        // Each solve can hold millions of states: only a few at a time.
        val pool = ForkJoinPool(2)
        for (block in blocks) {
            val meta = block.lines().filter { it.startsWith(";") }.map { it.drop(1).trim() }
            val name = meta.firstOrNull { !it.startsWith("seed") } ?: "room"
            val seed = meta.firstOrNull { it.startsWith("seed") }?.substringAfter("seed")?.trim()?.toIntOrNull() ?: 0
            val map = block.lines().filter { !it.startsWith(";") }.joinToString("\n")
                .replace('.', '*').replace('$', ' ')
            val room = runCatching { LevelParser.parse(map) }.getOrElse {
                output.appendLine("=== $name: INVALID ${it.message}\n")
                continue
            }
            val (scored, time) = measureTimedValue {
                val scramble = Scramble(room)
                val random = Random(seed * 7919 + name.hashCode())
                val walks = (0 until SCRAMBLE_WALKS).mapNotNull { scramble.walk(random, SCRAMBLE_STEPS) }
                    .distinctBy { it.boxes }
                    .sortedByDescending { it.score }
                    .take(SCRAMBLE_SOLVES)
                pool.submit<List<Pair<String, Solver.Result.Solved>>> {
                    walks.parallelStream().map { candidate ->
                        val text = render(room, candidate.boxes, pickPlayer(candidate.playerArea))
                        val level = LevelParser.parse(text)
                        val solved = Solver(level).solve(Solver.Metric.PUSHES, SCRAMBLE_BUDGET) as? Solver.Result.Solved
                        solved?.let {
                            check(GameEngine.replay(level, it.lurd).isSolved)
                            text to it
                        }
                    }.toList().filterNotNull()
                }.get().sortedByDescending { (_, solved) -> solved.explored }.take(4)
            }
            output.appendLine("=== $name: ${scored.size} proven, $time")
            for ((text, solved) in scored) {
                // Move-optimal when affordable, for a fair par.
                val level = LevelParser.parse(text)
                val byMoves = Solver(level).solve(Solver.Metric.MOVES, 1_500_000) as? Solver.Result.Solved
                val best = byMoves?.takeIf { it.moves <= solved.moves } ?: solved
                output.appendLine(
                    "--- moves=${best.moves}${if (byMoves == null) "+" else ""} pushes=${solved.pushes} " +
                        "effort=${solved.explored} boxes=${level.boxStarts.size}",
                )
                output.appendLine(text)
                output.appendLine("; ${best.lurd}")
            }
            output.appendLine()
            File(input.path + ".out").writeText(output.toString())
        }
        pool.shutdown()
    }

    /** A player start inside [area]: the open cell nearest the area's centre. */
    private fun pickPlayer(area: List<Position>): Position {
        val cx = area.map { it.x }.average()
        val cy = area.map { it.y }.average()
        val cells = area.toSet()
        return area.maxBy { cell ->
            val open = listOf(Position(1, 0), Position(-1, 0), Position(0, 1), Position(0, -1))
                .count { Position(cell.x + it.x, cell.y + it.y) in cells }
            open * 10 - (cell.x - cx) * (cell.x - cx) - (cell.y - cy) * (cell.y - cy)
        }
    }

    private fun render(room: dev.stefan.sokoban.core.Level, boxes: List<Position>, player: Position): String {
        val boxSet = boxes.toSet()
        return (0 until room.height).joinToString("\n") { y ->
            (0 until room.width).joinToString("") { x ->
                val p = Position(x, y)
                val goal = room.isGoal(p)
                when {
                    p == player -> if (goal) "+" else "@"
                    p in boxSet -> if (goal) "*" else "$"
                    room.isWall(p) -> "#"
                    goal -> "."
                    else -> " "
                }
            }.trimEnd()
        }
    }

    private companion object {
        const val SCRAMBLE_WALKS = 600
        const val SCRAMBLE_STEPS = 400
        const val SCRAMBLE_SOLVES = 15
        const val SCRAMBLE_BUDGET = 1_500_000
    }
}
