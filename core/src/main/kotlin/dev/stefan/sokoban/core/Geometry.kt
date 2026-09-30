package dev.stefan.sokoban.core

/** A cell on the board. `x` grows to the right, `y` grows downwards. */
data class Position(val x: Int, val y: Int) {

    operator fun plus(direction: Direction): Position = Position(x + direction.dx, y + direction.dy)

    operator fun minus(direction: Direction): Position = Position(x - direction.dx, y - direction.dy)

    override fun toString(): String = "($x,$y)"
}

/**
 * The four moves of Sokoban. [symbol] is the standard LURD notation letter for a
 * plain move; a push uses the upper-case letter.
 */
enum class Direction(val dx: Int, val dy: Int, val symbol: Char) {
    UP(0, -1, 'u'),
    DOWN(0, 1, 'd'),
    LEFT(-1, 0, 'l'),
    RIGHT(1, 0, 'r');

    val opposite: Direction
        get() = when (this) {
            UP -> DOWN
            DOWN -> UP
            LEFT -> RIGHT
            RIGHT -> LEFT
        }

    val isHorizontal: Boolean get() = dy == 0

    companion object {
        /** Parses a LURD letter, either case. Returns null for anything else. */
        fun fromSymbol(symbol: Char): Direction? = entries.firstOrNull { it.symbol == symbol.lowercaseChar() }
    }
}
