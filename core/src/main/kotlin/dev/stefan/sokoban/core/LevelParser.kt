package dev.stefan.sokoban.core

/** A level text that failed validation. [problems] lists every issue found, not only the first. */
class LevelFormatException(val problems: List<String>) :
    IllegalArgumentException("Invalid level: " + problems.joinToString("; "))

/**
 * Parses and validates levels written in the standard Sokoban text format:
 *
 * ```
 * #  wall            .  goal
 *    floor           $  box
 * @  player          *  box on goal
 * +  player on goal
 * ```
 *
 * `-` and `_` are accepted as floor too, as many published collections use them
 * to keep leading spaces visible.
 *
 * Validation guarantees that a parsed [Level] is playable: one player, a board
 * closed by walls around the player, and as many boxes as goals, all of them
 * inside the area the player can walk in.
 */
object LevelParser {

    const val MIN_SIZE = 3
    const val MAX_SIZE = 40
    const val MAX_BOXES = 30

    fun parse(text: String): Level {
        val problems = mutableListOf<String>()
        val level = parse(text, problems)
        if (level == null || problems.isNotEmpty()) throw LevelFormatException(problems)
        return level
    }

    /** Returns every problem in [text]; an empty list means the level is valid. */
    fun validate(text: String): List<String> {
        val problems = mutableListOf<String>()
        parse(text, problems)
        return problems
    }

    private fun parse(text: String, problems: MutableList<String>): Level? {
        val rows = text.lines()
            .map { it.trimEnd() }
            .dropWhile { it.isEmpty() }
            .dropLastWhile { it.isEmpty() }

        if (rows.isEmpty()) {
            problems += "Level is empty"
            return null
        }
        if (rows.any { it.isEmpty() }) problems += "Level contains an empty row"

        val height = rows.size
        val width = rows.maxOf { it.length }
        if (width !in MIN_SIZE..MAX_SIZE || height !in MIN_SIZE..MAX_SIZE) {
            problems += "Level size ${width}x$height is outside $MIN_SIZE..$MAX_SIZE"
            return null
        }

        val walls = BooleanArray(width * height)
        val goals = BooleanArray(width * height)
        val boxes = mutableListOf<Position>()
        val players = mutableListOf<Position>()

        rows.forEachIndexed { y, row ->
            row.forEachIndexed { x, symbol ->
                val index = y * width + x
                when (symbol) {
                    '#' -> walls[index] = true
                    ' ', '-', '_' -> Unit
                    '.' -> goals[index] = true
                    '$' -> boxes += Position(x, y)
                    '*' -> {
                        boxes += Position(x, y)
                        goals[index] = true
                    }
                    '@' -> players += Position(x, y)
                    '+' -> {
                        players += Position(x, y)
                        goals[index] = true
                    }
                    else -> problems += "Invalid character '$symbol' at row ${y + 1}, column ${x + 1}"
                }
            }
        }

        when (players.size) {
            0 -> problems += "Level has no player"
            1 -> Unit
            else -> problems += "Level has ${players.size} players, expected exactly one"
        }
        val goalCount = goals.count { it }
        if (boxes.isEmpty()) problems += "Level has no box"
        if (boxes.size > MAX_BOXES) problems += "Level has ${boxes.size} boxes, maximum is $MAX_BOXES"
        if (boxes.size != goalCount) {
            problems += "Level has ${boxes.size} boxes but $goalCount goals"
        }
        val player = players.singleOrNull() ?: return null

        // The playable area is everything the player can reach without crossing a
        // wall, boxes ignored. Reaching the edge of the text means the level leaks.
        val inside = BooleanArray(width * height)
        val stack = ArrayDeque<Position>()
        stack.addLast(player)
        inside[player.y * width + player.x] = true
        var leaks = false
        while (stack.isNotEmpty()) {
            val cell = stack.removeLast()
            if (cell.x == 0 || cell.y == 0 || cell.x == width - 1 || cell.y == height - 1) leaks = true
            for (direction in Direction.entries) {
                val next = cell + direction
                if (next.x !in 0 until width || next.y !in 0 until height) continue
                val index = next.y * width + next.x
                if (walls[index] || inside[index]) continue
                inside[index] = true
                stack.addLast(next)
            }
        }
        if (leaks) problems += "Playable area is not enclosed by walls"

        boxes.filter { !inside[it.y * width + it.x] }
            .forEach { problems += "Box at $it is outside the playable area" }
        for (y in 0 until height) for (x in 0 until width) {
            val index = y * width + x
            if (goals[index] && !inside[index]) problems += "Goal at ${Position(x, y)} is outside the playable area"
        }
        if (boxes.toSet().size != boxes.size) problems += "Two boxes share a cell"

        val tiles = List(width * height) { index ->
            when {
                walls[index] -> Tile.WALL
                !inside[index] -> Tile.VOID
                goals[index] -> Tile.GOAL
                else -> Tile.FLOOR
            }
        }
        return Level(width, height, tiles, player, boxes.toList())
    }
}
