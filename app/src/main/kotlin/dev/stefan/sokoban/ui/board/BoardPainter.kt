package dev.stefan.sokoban.ui.board

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import dev.stefan.sokoban.core.Direction
import dev.stefan.sokoban.core.Level
import dev.stefan.sokoban.core.Tile
import dev.stefan.sokoban.ui.theme.Palette

/*
 * Drawing of every board element, shared by the game board and the menus.
 *
 * Projection: a gentle three-quarter view. A wall's top face covers its own
 * cell and its front face hangs *down* into the cell in front of it. Crates and
 * the hero only ever extend upwards. With that rule the static board can be
 * drawn once, and moving objects drawn over it, with correct occlusion.
 *
 * All sizes are fractions of the tile size, so everything scales cleanly.
 */

/** How far a wall's front face drops into the cell in front of it. */
const val WALL_DEPTH = 0.2f

private val EYE_SIDES = floatArrayOf(-1f, 1f)

/** Floor, grout and wall relief: everything that never moves. */
internal class StaticBoard(level: Level, private val tile: Float, private val origin: Offset) {

    private val floorEven = Path()
    private val floorOdd = Path()
    private val speckles = ArrayList<Offset>()
    private val grout = Path()
    private val wallTops = Path()
    private val wallCaps = Path()
    private val wallFronts = Path()
    private val sideShadows = ArrayList<Offset>()
    private val wallHighlights = ArrayList<Pair<Offset, Offset>>()
    private val wallShadows = ArrayList<Pair<Offset, Size>>()
    private val footprint = Path()

    init {
        val radius = tile * 0.18f
        fun wall(x: Int, y: Int) = level.tileAt(x, y) == Tile.WALL
        fun cell(x: Int, y: Int) = Offset(origin.x + x * tile, origin.y + y * tile)

        for (y in 0 until level.height) for (x in 0 until level.width) {
            val tileType = level.tileAt(x, y)
            val topLeft = cell(x, y)
            if (tileType != Tile.VOID) {
                footprint.addRoundRect(
                    RoundRect(topLeft.x, topLeft.y, topLeft.x + tile, topLeft.y + tile * (1 + WALL_DEPTH), CornerRadius(radius)),
                )
            }
            if (tileType.isWalkable) {
                grout.addRect(Rect(topLeft, Size(tile, tile)))
                val inset = tile * 0.035f
                val rect = RoundRect(
                    topLeft.x + inset, topLeft.y + inset, topLeft.x + tile - inset, topLeft.y + tile - inset,
                    CornerRadius(tile * 0.12f),
                )
                (if ((x + y) % 2 == 0) floorEven else floorOdd).addRoundRect(rect)
                // Light comes from the top left: a wall on the left shades the floor.
                if (wall(x - 1, y)) sideShadows += topLeft
                // Two faint marks per tile, placed by a hash of the cell: texture
                // without visible repetition.
                val hash = (x * 73856093) xor (y * 19349663)
                for (k in 0..1) {
                    val h = hash * (k + 7) ushr 3
                    speckles += Offset(
                        topLeft.x + tile * (0.2f + (h and 0xFF) / 255f * 0.6f),
                        topLeft.y + tile * (0.2f + (h ushr 8 and 0xFF) / 255f * 0.6f),
                    )
                }
            }
        }

        for (y in 0 until level.height) for (x in 0 until level.width) {
            if (!wall(x, y)) continue
            val topLeft = cell(x, y)
            val up = wall(x, y - 1)
            val down = wall(x, y + 1)
            val left = wall(x - 1, y)
            val right = wall(x + 1, y)
            fun round(convex: Boolean) = if (convex) CornerRadius(radius) else CornerRadius.Zero
            // Rounding only the convex corners merges neighbouring walls into
            // one continuous mass instead of a grid of blocks.
            wallTops.addRoundRect(
                RoundRect(
                    left = topLeft.x, top = topLeft.y, right = topLeft.x + tile, bottom = topLeft.y + tile,
                    topLeftCornerRadius = round(!up && !left),
                    topRightCornerRadius = round(!up && !right),
                    bottomRightCornerRadius = round(!down && !right),
                    bottomLeftCornerRadius = round(!down && !left),
                ),
            )
            // A slightly lighter cap on every block: the wall reads as stones.
            val capInset = tile * 0.09f
            wallCaps.addRoundRect(
                RoundRect(
                    topLeft.x + capInset, topLeft.y + capInset, topLeft.x + tile - capInset, topLeft.y + tile - capInset,
                    CornerRadius(tile * 0.12f),
                ),
            )
            if (!down) {
                val depth = tile * WALL_DEPTH
                wallFronts.addRoundRect(
                    RoundRect(
                        left = topLeft.x, top = topLeft.y + tile * 0.5f, right = topLeft.x + tile, bottom = topLeft.y + tile + depth,
                        topLeftCornerRadius = CornerRadius.Zero,
                        topRightCornerRadius = CornerRadius.Zero,
                        // Square where the front continues or meets a wall beside it.
                        bottomRightCornerRadius = round(!right),
                        bottomLeftCornerRadius = round(!left),
                    ),
                )
                if (level.tileAt(x, y + 1).isWalkable) {
                    wallShadows += Offset(topLeft.x, topLeft.y + tile + depth) to Size(tile, tile * 0.22f)
                }
            }
            if (!up) {
                val inset = if (!left || !right) radius else 0f
                val startX = topLeft.x + if (!left) inset else 0f
                val endX = topLeft.x + tile - if (!right) inset else 0f
                val lineY = topLeft.y + tile * 0.07f
                wallHighlights += Offset(startX, lineY) to Offset(endX, lineY)
            }
        }
    }

    fun draw(scope: DrawScope, palette: Palette) = with(scope) {
        // Soft shadow under the whole structure, built from a few offset passes.
        for (pass in 1..3) {
            withTransform({ translate(0f, tile * 0.06f * pass) }) {
                drawPath(footprint, palette.shadow.copy(alpha = if (palette.isDark) 0.16f else 0.06f))
            }
        }
        drawPath(grout, palette.floorEdge)
        drawPath(floorEven, palette.floor)
        drawPath(floorOdd, palette.floorAlt)
        for (speck in speckles) drawCircle(palette.floorEdge.copy(alpha = 0.55f), tile * 0.018f, speck)
        for ((topLeft, size) in wallShadows) {
            drawRect(
                Brush.verticalGradient(
                    listOf(palette.shadow.copy(alpha = if (palette.isDark) 0.35f else 0.16f), Color.Transparent),
                    startY = topLeft.y,
                    endY = topLeft.y + size.height,
                ),
                topLeft,
                size,
            )
        }
        val sideShade = Brush.horizontalGradient(
            listOf(palette.shadow.copy(alpha = if (palette.isDark) 0.28f else 0.1f), Color.Transparent),
            startX = 0f,
            endX = tile * 0.22f,
        )
        for (topLeft in sideShadows) {
            withTransform({ translate(topLeft.x, topLeft.y) }) {
                drawRect(sideShade, Offset.Zero, Size(tile * 0.22f, tile))
            }
        }
        drawPath(wallFronts, palette.wallFront)
        drawPath(wallTops, palette.wallTop)
        drawPath(wallCaps, lerp(palette.wallTop, palette.wallHighlight, 0.2f))
        val highlight = palette.wallHighlight.copy(alpha = 0.8f)
        for ((start, end) in wallHighlights) {
            drawLine(highlight, start, end, strokeWidth = tile * 0.05f, cap = StrokeCap.Round)
        }
    }
}

/**
 * A target: a ring and a dot. [pulse] (0..1) breathes gently while empty;
 * [lit] (0..1) is the burst of light when a crate arrives.
 */
internal fun DrawScope.drawGoal(topLeft: Offset, tile: Float, palette: Palette, pulse: Float, lit: Float) {
    val center = Offset(topLeft.x + tile / 2f, topLeft.y + tile / 2f)
    val ringRadius = tile * (0.25f + 0.02f * pulse)
    drawCircle(palette.goal.copy(alpha = 0.16f + 0.08f * pulse), ringRadius + tile * 0.06f, center)
    drawCircle(palette.goal, ringRadius, center, style = Stroke(width = tile * 0.065f))
    drawCircle(palette.goal, tile * 0.085f, center)
    if (lit > 0f) {
        drawCircle(palette.goalGlow.copy(alpha = 0.55f * lit), tile * (0.3f + 0.45f * (1f - lit)), center, style = Stroke(tile * 0.05f * lit + 1f))
    }
}

/** Visual state of one crate for a frame. */
internal class CrateLook {
    /** 0 = plain wood, 1 = on a target. */
    var done = 0f

    /** 0..1: deadlock warning strength. */
    var stuck = 0f

    /** Landing squash: 0 = none, positive = flattened. */
    var squash = 0f

    /** Arrival glow around the crate, 0..1. */
    var glow = 0f

    /** Entrance scale, 0..1. */
    var scale = 1f

    /** Celebration hop height, in tiles. */
    var hop = 0f
}

internal fun DrawScope.drawCrate(topLeft: Offset, tile: Float, palette: Palette, look: CrateLook) {
    val body = lerp(lerp(palette.crate, palette.crateDone, look.done), palette.danger, look.stuck * 0.35f)
    val dark = lerp(lerp(palette.crateDark, palette.crateDoneDark, look.done), palette.danger, look.stuck * 0.35f)
    val light = lerp(palette.crateLight, palette.crateDoneLight, look.done)

    val left = topLeft.x + tile * 0.1f
    val width = tile * 0.8f
    val top = topLeft.y + tile * 0.06f - look.hop * tile
    val faceHeight = tile * 0.72f
    val depth = tile * 0.12f
    val radius = CornerRadius(tile * 0.16f)

    if (look.glow > 0f) {
        drawCircle(
            Brush.radialGradient(
                listOf(palette.goalGlow.copy(alpha = 0.6f * look.glow), Color.Transparent),
                center = Offset(topLeft.x + tile / 2, topLeft.y + tile / 2),
                radius = tile * (0.7f + 0.3f * look.glow),
            ),
            radius = tile * (0.7f + 0.3f * look.glow),
            center = Offset(topLeft.x + tile / 2, topLeft.y + tile / 2),
        )
    }
    // Ground shadow stays on the floor even when the crate hops.
    drawRoundRect(
        palette.shadow.copy(alpha = if (palette.isDark) 0.35f else 0.16f),
        Offset(topLeft.x + tile * 0.08f, topLeft.y + tile * 0.3f),
        Size(tile * 0.84f, tile * 0.68f),
        CornerRadius(tile * 0.2f),
    )

    val pivot = Offset(topLeft.x + tile / 2, topLeft.y + tile * 0.92f)
    val scaleX = look.scale * (1f + look.squash * 0.08f)
    val scaleY = look.scale * (1f - look.squash * 0.1f)
    withTransform({ scale(scaleX, scaleY, pivot) }) {
        drawRoundRect(dark, Offset(left, top + depth), Size(width, faceHeight), radius)
        drawRoundRect(body, Offset(left, top), Size(width, faceHeight), radius)
        // Top edge catches the light.
        drawLine(
            light.copy(alpha = 0.9f),
            Offset(left + tile * 0.14f, top + tile * 0.05f),
            Offset(left + width - tile * 0.14f, top + tile * 0.05f),
            strokeWidth = tile * 0.045f,
            cap = StrokeCap.Round,
        )
        val panelInset = tile * 0.11f
        val panelTopLeft = Offset(left + panelInset, top + panelInset)
        val panelSize = Size(width - panelInset * 2, faceHeight - panelInset * 2)
        val detail = dark.copy(alpha = 0.55f)
        drawRoundRect(detail, panelTopLeft, panelSize, CornerRadius(tile * 0.07f), style = Stroke(width = tile * 0.045f))
        val braceAlpha = 1f - look.done
        if (braceAlpha > 0f) {
            drawLine(
                detail.copy(alpha = detail.alpha * braceAlpha),
                Offset(panelTopLeft.x + tile * 0.05f, panelTopLeft.y + panelSize.height - tile * 0.05f),
                Offset(panelTopLeft.x + panelSize.width - tile * 0.05f, panelTopLeft.y + tile * 0.05f),
                strokeWidth = tile * 0.06f,
                cap = StrokeCap.Round,
            )
        }
        if (look.done > 0f) {
            // A check mark replaces the brace once the crate is home.
            val mark = light.copy(alpha = look.done)
            val cx = left + width / 2
            val cy = top + faceHeight / 2
            val s = tile * 0.13f * (0.6f + 0.4f * look.done)
            drawLine(mark, Offset(cx - s, cy), Offset(cx - s * 0.25f, cy + s * 0.75f), strokeWidth = tile * 0.075f, cap = StrokeCap.Round)
            drawLine(mark, Offset(cx - s * 0.25f, cy + s * 0.75f), Offset(cx + s * 1.1f, cy - s * 0.8f), strokeWidth = tile * 0.075f, cap = StrokeCap.Round)
        }
        if (look.stuck > 0f) {
            drawRoundRect(
                palette.danger.copy(alpha = 0.9f * look.stuck),
                Offset(left - tile * 0.02f, top - tile * 0.02f),
                Size(width + tile * 0.04f, faceHeight + depth + tile * 0.04f),
                CornerRadius(tile * 0.18f),
                style = Stroke(width = tile * 0.05f),
            )
        }
    }
}

/** Visual state of the hero for a frame. */
internal class HeroLook {
    var facing = Direction.DOWN

    /** 0 = eyes open, 1 = closed. */
    var blink = 0f

    /** Idle breathing, -1..1. */
    var breath = 0f

    /** Stretch along the movement axis, 0..1 (from speed). */
    var stretch = 0f

    /** Leaning into a push or a bump, 0..1. */
    var lean = 0f

    /** Step cycle, in radians: moves the feet. */
    var stride = 0f

    /** Sprout sway, -1..1. */
    var sway = 0f

    /** Entrance drop, in tiles above the cell. */
    var drop = 0f

    var scale = 1f

    /** 0..1 joy: closes the eyes into smiles (victory). */
    var joy = 0f
}

/**
 * Pip, the hero: a soft coral blob with a mint sprout, whose eyes always
 * look where it is heading.
 */
internal fun DrawScope.drawHero(topLeft: Offset, tile: Float, palette: Palette, look: HeroLook) {
    val cx = topLeft.x + tile / 2
    val ground = topLeft.y + tile * 0.9f
    val horizontal = look.facing.isHorizontal

    drawOval(
        palette.shadow.copy(alpha = if (palette.isDark) 0.4f else 0.2f),
        Offset(cx - tile * 0.28f, ground - tile * 0.07f),
        Size(tile * 0.56f, tile * 0.14f),
    )

    val dropOffset = -look.drop * tile
    // Stretch along the direction of travel, squash across it; leaning adds
    // a push-shaped squash in the facing axis.
    val along = 1f + look.stretch * 0.12f - look.lean * 0.08f
    val across = 1f - look.stretch * 0.08f + look.lean * 0.06f
    val breathY = 1f + look.breath * 0.025f
    val scaleX = look.scale * (if (horizontal) along else across)
    val scaleY = look.scale * (if (horizontal) across else along) * breathY
    val leanShift = look.lean * tile * 0.06f
    val shiftX = look.facing.dx * leanShift
    val shiftY = look.facing.dy * leanShift + dropOffset

    withTransform({
        translate(shiftX, shiftY)
        scale(scaleX, scaleY, Offset(cx, ground))
    }) {
        // Feet, alternating with the stride.
        val footLift = kotlin.math.sin(look.stride) * tile * 0.035f
        drawOval(palette.playerDark, Offset(cx - tile * 0.25f, ground - tile * 0.1f - footLift.coerceAtLeast(0f)), Size(tile * 0.19f, tile * 0.1f))
        drawOval(palette.playerDark, Offset(cx + tile * 0.06f, ground - tile * 0.1f + footLift.coerceAtMost(0f)), Size(tile * 0.19f, tile * 0.1f))

        val bodyTop = topLeft.y + tile * 0.16f
        val bodyHeight = ground - tile * 0.05f - bodyTop
        drawRoundRect(
            palette.playerDark,
            Offset(cx - tile * 0.32f, bodyTop + tile * 0.04f),
            Size(tile * 0.64f, bodyHeight),
            CornerRadius(tile * 0.3f),
        )
        drawRoundRect(
            palette.player,
            Offset(cx - tile * 0.32f, bodyTop),
            Size(tile * 0.64f, bodyHeight - tile * 0.02f),
            CornerRadius(tile * 0.3f),
        )
        drawOval(
            palette.playerLight.copy(alpha = 0.7f),
            Offset(cx - tile * 0.2f, bodyTop + tile * 0.06f),
            Size(tile * 0.18f, tile * 0.09f),
        )

        // Sprout.
        val stemBase = Offset(cx, bodyTop + tile * 0.02f)
        val stemTip = Offset(cx + look.sway * tile * 0.06f, bodyTop - tile * 0.13f)
        drawLine(palette.playerDark, stemBase, stemTip, strokeWidth = tile * 0.045f, cap = StrokeCap.Round)
        drawOval(
            palette.crateDone,
            Offset(stemTip.x - tile * 0.02f, stemTip.y - tile * 0.05f),
            Size(tile * 0.15f, tile * 0.08f),
        )

        // Face: eyes follow the facing direction; seen from behind when going up.
        val eyeY: Float
        val eyeShift: Float
        val eyeScale: Float
        when (look.facing) {
            Direction.DOWN -> { eyeY = bodyTop + tile * 0.3f; eyeShift = 0f; eyeScale = 1f }
            Direction.LEFT -> { eyeY = bodyTop + tile * 0.28f; eyeShift = -tile * 0.08f; eyeScale = 1f }
            Direction.RIGHT -> { eyeY = bodyTop + tile * 0.28f; eyeShift = tile * 0.08f; eyeScale = 1f }
            Direction.UP -> { eyeY = bodyTop + tile * 0.12f; eyeShift = 0f; eyeScale = 0.75f }
        }
        val eyeWidth = tile * 0.15f * eyeScale
        val open = (1f - look.blink).coerceIn(0.08f, 1f) * (1f - look.joy * 0.85f)
        val eyeHeight = tile * 0.19f * eyeScale * open
        val pupil = Offset(look.facing.dx * tile * 0.035f, look.facing.dy * tile * 0.03f)
        for (side in EYE_SIDES) {
            val ex = cx + eyeShift + side * tile * 0.12f
            val center = Offset(ex, eyeY + tile * 0.095f * eyeScale)
            if (look.joy > 0.5f) {
                // Happy closed eyes: little arcs.
                drawArc(
                    palette.pupil, 200f, 140f, false,
                    Offset(ex - eyeWidth / 2, center.y - eyeWidth * 0.2f), Size(eyeWidth, eyeWidth * 0.8f),
                    style = Stroke(width = tile * 0.04f, cap = StrokeCap.Round),
                )
                continue
            }
            drawOval(palette.eye, Offset(ex - eyeWidth / 2, center.y - eyeHeight / 2), Size(eyeWidth, eyeHeight))
            if (open > 0.3f) {
                drawCircle(palette.pupil, tile * 0.045f * eyeScale, center + pupil)
                drawCircle(palette.eye, tile * 0.014f, center + pupil + Offset(-tile * 0.015f, -tile * 0.018f))
            }
        }
        if (look.facing != Direction.UP) {
            // Cheeks and a small smile.
            val cheekY = eyeY + tile * 0.2f
            drawOval(palette.playerLight.copy(alpha = 0.75f), Offset(cx + eyeShift - tile * 0.25f, cheekY), Size(tile * 0.1f, tile * 0.06f))
            drawOval(palette.playerLight.copy(alpha = 0.75f), Offset(cx + eyeShift + tile * 0.15f, cheekY), Size(tile * 0.1f, tile * 0.06f))
            val smile = tile * (0.09f + 0.04f * look.joy)
            drawArc(
                palette.pupil, 20f, 140f, false,
                Offset(cx + eyeShift - smile / 2, cheekY - tile * 0.04f), Size(smile, smile * 0.7f),
                style = Stroke(width = tile * 0.035f, cap = StrokeCap.Round),
            )
        }
    }
}
