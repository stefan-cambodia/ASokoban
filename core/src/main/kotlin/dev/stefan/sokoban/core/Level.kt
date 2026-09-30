package dev.stefan.sokoban.core

/** The static content of a board cell. Boxes and the player are not tiles: they move. */
enum class Tile {
    /** Outside the playable area: never drawn as floor, never reachable. */
    VOID,
    WALL,
    FLOOR,
    GOAL;

    val isWalkable: Boolean get() = this == FLOOR || this == GOAL
}

/**
 * An immutable, validated Sokoban level. Build one with [LevelParser]; the
 * constructor is internal so that every [Level] in existence has been validated.
 */
class Level internal constructor(
    val width: Int,
    val height: Int,
    private val tiles: List<Tile>,
    val playerStart: Position,
    val boxStarts: List<Position>,
) {

    val goals: List<Position> = buildList {
        for (y in 0 until height) for (x in 0 until width) {
            if (tiles[y * width + x] == Tile.GOAL) add(Position(x, y))
        }
    }

    private val goalSet: Set<Position> = goals.toSet()

    fun contains(position: Position): Boolean =
        position.x in 0 until width && position.y in 0 until height

    fun tileAt(position: Position): Tile =
        if (contains(position)) tiles[position.y * width + position.x] else Tile.VOID

    fun tileAt(x: Int, y: Int): Tile = tileAt(Position(x, y))

    fun isWalkable(position: Position): Boolean = tileAt(position).isWalkable

    fun isWall(position: Position): Boolean = tileAt(position) == Tile.WALL

    fun isGoal(position: Position): Boolean = position in goalSet

    /** Every walkable cell, in reading order. */
    val floorCells: List<Position> = buildList {
        for (y in 0 until height) for (x in 0 until width) {
            if (tiles[y * width + x].isWalkable) add(Position(x, y))
        }
    }

    /** Serialises the level back to the standard text format. */
    fun toText(): String = buildString {
        val boxes = boxStarts.toSet()
        for (y in 0 until height) {
            val row = StringBuilder()
            for (x in 0 until width) {
                val position = Position(x, y)
                val tile = tileAt(position)
                row.append(
                    when {
                        position == playerStart && tile == Tile.GOAL -> '+'
                        position == playerStart -> '@'
                        position in boxes && tile == Tile.GOAL -> '*'
                        position in boxes -> '$'
                        tile == Tile.WALL -> '#'
                        tile == Tile.GOAL -> '.'
                        else -> ' '
                    },
                )
            }
            append(row.trimEnd())
            if (y < height - 1) append('\n')
        }
    }
}
