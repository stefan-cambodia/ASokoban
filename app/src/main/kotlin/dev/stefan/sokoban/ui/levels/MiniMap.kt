package dev.stefan.sokoban.ui.levels

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import dev.stefan.sokoban.core.Level
import dev.stefan.sokoban.core.Position
import dev.stefan.sokoban.core.Tile
import dev.stefan.sokoban.ui.theme.Palette

/**
 * A level in miniature, for its card in the level list: floor, walls with a
 * darker front edge, targets, crates and the hero, fitted and centred in
 * [size]. Built once per size; drawing only fills what is prepared here.
 */
internal class MiniMap(level: Level, size: Size) {

    private val cell = minOf(size.width / level.width, size.height / level.height)
    private val origin = Offset((size.width - level.width * cell) / 2f, (size.height - level.height * cell) / 2f)
    private val floor = Path()
    private val wallTops = Path()
    private val wallFronts = Path()
    private val goals = level.goals.map { it.topLeft() + Offset(cell / 2f, cell / 2f) }
    private val crateStarts = level.boxStarts.map { it.topLeft() }
    private val crateHomes = level.goals.map { it.topLeft() }
    private val hero = level.playerStart.topLeft() + Offset(cell / 2f, cell / 2f)
    private val ring = Stroke(width = cell * 0.14f)

    init {
        for (y in 0 until level.height) for (x in 0 until level.width) {
            val tile = level.tileAt(x, y)
            val topLeft = Position(x, y).topLeft()
            if (tile == Tile.WALL) {
                wallTops.addRect(Rect(topLeft, Size(cell, cell)))
                // The same three-quarter view as the board: a wall with open
                // floor below it shows its front face.
                if (level.tileAt(x, y + 1) != Tile.WALL) {
                    wallFronts.addRect(Rect(topLeft.x, topLeft.y + cell * 0.68f, topLeft.x + cell, topLeft.y + cell))
                }
            } else if (tile.isWalkable) {
                floor.addRect(Rect(topLeft, Size(cell, cell)))
            }
        }
    }

    private fun Position.topLeft() = Offset(origin.x + x * cell, origin.y + y * cell)

    /**
     * [reveal] 0 shows only a faint outline of the walls, a locked level's
     * teaser; 1 shows the level in colour. [solved] puts every crate home.
     */
    fun draw(scope: DrawScope, palette: Palette, reveal: Float, solved: Boolean) = with(scope) {
        if (reveal < 1f) drawPath(wallTops, palette.textMuted, alpha = SILHOUETTE_ALPHA * (1f - reveal))
        if (reveal <= 0f) return@with
        // On the dark theme the floor is the card's own tone: lift it a little.
        drawPath(floor, if (palette.isDark) lerp(palette.floor, palette.wallTop, 0.25f) else palette.floor, alpha = reveal)
        drawPath(wallTops, palette.wallTop, alpha = reveal)
        drawPath(wallFronts, palette.wallFront, alpha = reveal)
        for (goal in goals) drawCircle(palette.goal, cell * 0.27f, goal, alpha = reveal, style = ring)
        val inset = cell * 0.12f
        val crateSize = Size(cell - inset * 2, cell - inset * 2)
        val radius = CornerRadius(cell * 0.2f)
        val body = if (solved) palette.crateDone else palette.crate
        val edge = if (solved) palette.crateDoneDark else palette.crateDark
        for (topLeft in if (solved) crateHomes else crateStarts) {
            val at = topLeft + Offset(inset, inset)
            drawRoundRect(edge, at, crateSize, radius, alpha = reveal)
            drawRoundRect(body, at, crateSize.copy(height = crateSize.height * 0.78f), radius, alpha = reveal)
        }
        if (!solved) drawCircle(palette.player, cell * 0.34f, hero, alpha = reveal)
    }

    private companion object {
        const val SILHOUETTE_ALPHA = 0.14f
    }
}
