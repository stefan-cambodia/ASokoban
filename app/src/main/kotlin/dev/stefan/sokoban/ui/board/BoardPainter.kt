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
import androidx.compose.ui.graphics.StrokeJoin
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

/** cos 45°: steps along a diagonal. */
private const val DIAGONAL = 0.70710677f

/** One stone of a wall's top face, with its own shade: -1 darker .. 1 lighter. */
private class Stone(val rect: RoundRect, val tone: Float)

/** Floor, grout and wall relief: everything that never moves. */
internal class StaticBoard(level: Level, private val tile: Float, private val origin: Offset) {

    private val floorEven = Path()
    private val floorOdd = Path()
    private val floorLit = Path()
    private val floorShaded = Path()
    private val speckles = ArrayList<Offset>()
    private val grout = Path()
    private val wallTops = Path()
    private val stones = ArrayList<Stone>()
    private val wallFronts = ArrayList<Pair<Path, RoundRect>>()
    private val sideShadows = ArrayList<Offset>()
    private val wallHighlights = ArrayList<Pair<Offset, Offset>>()
    private val wallShadows = ArrayList<Pair<Offset, Size>>()
    private val footprint = Path()

    init {
        val radius = tile * 0.18f
        fun wall(x: Int, y: Int) = level.tileAt(x, y) == Tile.WALL
        fun cell(x: Int, y: Int) = Offset(origin.x + x * tile, origin.y + y * tile)
        fun hash(x: Int, y: Int) = ((x * 73856093) xor (y * 19349663)) * 0x2545F491 ushr 7

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
                // A bevel on every paver: lit along the top and left edges,
                // shaded along the bottom and right ones.
                val edge = inset + tile * 0.03f
                val corner = tile * 0.14f
                floorLit.moveTo(topLeft.x + edge, topLeft.y + tile - corner)
                floorLit.lineTo(topLeft.x + edge, topLeft.y + corner)
                floorLit.quadraticTo(topLeft.x + edge, topLeft.y + edge, topLeft.x + corner, topLeft.y + edge)
                floorLit.lineTo(topLeft.x + tile - corner, topLeft.y + edge)
                floorShaded.moveTo(topLeft.x + tile - edge, topLeft.y + corner)
                floorShaded.lineTo(topLeft.x + tile - edge, topLeft.y + tile - corner)
                floorShaded.quadraticTo(topLeft.x + tile - edge, topLeft.y + tile - edge, topLeft.x + tile - corner, topLeft.y + tile - edge)
                floorShaded.lineTo(topLeft.x + corner, topLeft.y + tile - edge)
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

        // Stones laid over the wall tops. Most cells hold one stone; a hash of
        // the cell joins some with the neighbour to the right or below and
        // splits a few in two, so the wall reads as masonry rather than as a
        // grid of identical blocks.
        val taken = HashSet<Int>()
        val gap = tile * 0.06f
        fun stone(left: Float, top: Float, right: Float, bottom: Float, seed: Int) {
            val tone = (seed ushr 11 and 0xFF) / 255f * 2f - 1f
            stones += Stone(RoundRect(left + gap, top + gap, right - gap, bottom - gap, CornerRadius(tile * 0.1f)), tone)
        }
        for (y in 0 until level.height) for (x in 0 until level.width) {
            if (!wall(x, y) || !taken.add(y * level.width + x)) continue
            val topLeft = cell(x, y)
            val h = hash(x, y)
            val right = y * level.width + x + 1
            val below = (y + 1) * level.width + x
            when {
                h % 8 == 0 && wall(x + 1, y) && right !in taken -> {
                    taken += right
                    stone(topLeft.x, topLeft.y, topLeft.x + tile * 2, topLeft.y + tile, h)
                }
                h % 8 == 1 && wall(x, y + 1) && below !in taken -> {
                    taken += below
                    stone(topLeft.x, topLeft.y, topLeft.x + tile, topLeft.y + tile * 2, h)
                }
                h % 8 == 2 -> {
                    stone(topLeft.x, topLeft.y, topLeft.x + tile, topLeft.y + tile / 2, h)
                    stone(topLeft.x, topLeft.y + tile / 2, topLeft.x + tile, topLeft.y + tile, h * 31)
                }
                else -> stone(topLeft.x, topLeft.y, topLeft.x + tile, topLeft.y + tile, h)
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
            if (!down) {
                val depth = tile * WALL_DEPTH
                val front = RoundRect(
                    left = topLeft.x, top = topLeft.y + tile * 0.5f, right = topLeft.x + tile, bottom = topLeft.y + tile + depth,
                    topLeftCornerRadius = CornerRadius.Zero,
                    topRightCornerRadius = CornerRadius.Zero,
                    // Square where the front continues or meets a wall beside it.
                    bottomRightCornerRadius = round(!right),
                    bottomLeftCornerRadius = round(!left),
                )
                wallFronts += Path().apply { addRoundRect(front) } to front
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
        // Soft shadow under the whole structure: widening outlines, each faint,
        // add up to a falloff away from the edge. (A blur filter would need a
        // bitmap on Android 8, where hardware canvases ignore it.)
        val shadowAlpha = if (palette.isDark) 0.36f else 0.14f
        withTransform({ translate(0f, tile * 0.1f) }) {
            drawPath(footprint, palette.shadow.copy(alpha = shadowAlpha / 2))
            for (step in 1..SHADOW_STEPS) {
                drawPath(
                    footprint,
                    palette.shadow.copy(alpha = shadowAlpha / SHADOW_STEPS),
                    style = Stroke(width = tile * 0.065f * step, join = StrokeJoin.Round),
                )
            }
        }
        drawPath(grout, palette.floorEdge)
        drawPath(floorEven, palette.floor)
        drawPath(floorOdd, palette.floorAlt)
        val bevel = Stroke(width = tile * 0.03f, cap = StrokeCap.Round)
        drawPath(floorLit, Color.White.copy(alpha = if (palette.isDark) 0.05f else 0.45f), style = bevel)
        drawPath(floorShaded, palette.floorEdge.copy(alpha = if (palette.isDark) 0.9f else 0.6f), style = bevel)
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
        // Front faces darken towards the floor.
        val frontLow = lerp(palette.wallFront, palette.shadow, if (palette.isDark) 0.3f else 0.25f)
        for ((path, rect) in wallFronts) {
            val faceTop = rect.bottom - tile * WALL_DEPTH
            drawPath(path, Brush.verticalGradient(listOf(palette.wallFront, frontLow), startY = faceTop, endY = rect.bottom))
        }
        // The wall tops show between the stones as mortar.
        drawPath(wallTops, palette.wallTop)
        val stoneLine = tile * 0.028f
        for (stone in stones) {
            val r = stone.rect
            val base = lerp(palette.wallTop, palette.wallHighlight, 0.22f + 0.1f * stone.tone)
            drawRoundRect(
                Brush.verticalGradient(
                    listOf(lerp(base, palette.wallHighlight, 0.35f), base, lerp(base, palette.wallTop, 0.4f)),
                    startY = r.top,
                    endY = r.bottom,
                ),
                Offset(r.left, r.top),
                Size(r.width, r.height),
                CornerRadius(r.topLeftCornerRadius.x),
            )
            // Each stone catches the light on its top edge and is shaded on its lower one.
            val inset = r.topLeftCornerRadius.x
            drawLine(
                palette.wallHighlight.copy(alpha = 0.7f),
                Offset(r.left + inset, r.top + stoneLine / 2),
                Offset(r.right - inset, r.top + stoneLine / 2),
                strokeWidth = stoneLine,
                cap = StrokeCap.Round,
            )
            drawLine(
                palette.wallFront.copy(alpha = 0.45f),
                Offset(r.left + inset, r.bottom - stoneLine / 2),
                Offset(r.right - inset, r.bottom - stoneLine / 2),
                strokeWidth = stoneLine,
                cap = StrokeCap.Round,
            )
        }
        val highlight = palette.wallHighlight.copy(alpha = 0.6f)
        for ((start, end) in wallHighlights) {
            drawLine(highlight, start, end, strokeWidth = tile * 0.04f, cap = StrokeCap.Round)
        }
    }

    private companion object {
        const val SHADOW_STEPS = 6
    }
}

/**
 * A target: a ring and a dot. [pulse] (0..1) breathes gently while empty;
 * [lit] (0..1) is the burst of light when a crate arrives.
 */
internal fun DrawScope.drawGoal(topLeft: Offset, tile: Float, palette: Palette, pulse: Float, lit: Float) {
    val center = Offset(topLeft.x + tile / 2f, topLeft.y + tile / 2f)
    // The target is set into the floor: a shallow recess, shaded along its
    // upper edge and lit along its lower rim.
    val recess = tile * 0.34f
    val recessBounds = Offset(center.x - recess, center.y - recess)
    drawCircle(palette.floorEdge.copy(alpha = if (palette.isDark) 0.6f else 0.28f), recess, center)
    drawArc(
        palette.shadow.copy(alpha = if (palette.isDark) 0.4f else 0.14f), 190f, 160f, false,
        recessBounds, Size(recess * 2, recess * 2), style = Stroke(width = tile * 0.04f, cap = StrokeCap.Round),
    )
    drawArc(
        Color.White.copy(alpha = if (palette.isDark) 0.07f else 0.55f), 25f, 130f, false,
        recessBounds, Size(recess * 2, recess * 2), style = Stroke(width = tile * 0.03f, cap = StrokeCap.Round),
    )
    val ringRadius = tile * (0.25f + 0.02f * pulse)
    drawCircle(palette.goal.copy(alpha = 0.16f + 0.08f * pulse), ringRadius + tile * 0.06f, center)
    drawCircle(palette.goal, ringRadius, center, style = Stroke(width = tile * 0.065f))
    drawArc(
        palette.goalGlow.copy(alpha = 0.75f), 200f, 110f, false,
        Offset(center.x - ringRadius, center.y - ringRadius), Size(ringRadius * 2, ringRadius * 2),
        style = Stroke(width = tile * 0.025f, cap = StrokeCap.Round),
    )
    drawCircle(palette.goal, tile * 0.085f, center)
    drawCircle(palette.goalGlow.copy(alpha = 0.8f), tile * 0.028f, center + Offset(-tile * 0.025f, -tile * 0.025f))
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

    /** Position of the light sweeping across the face, 0..1; none at either end. */
    var glint = 0f

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
    // Ground shadow stays on the floor even when the crate hops; stacked
    // widening layers give it a soft edge.
    val shadowAlpha = (if (palette.isDark) 0.4f else 0.2f) / 3f
    for (layer in 0..2) {
        val grow = tile * 0.035f * layer
        drawRoundRect(
            palette.shadow.copy(alpha = shadowAlpha),
            Offset(topLeft.x + tile * 0.1f - grow, topLeft.y + tile * 0.32f - grow / 2),
            Size(tile * 0.8f + grow * 2, tile * 0.66f + grow * 1.5f),
            CornerRadius(tile * 0.2f + grow),
        )
    }

    val pivot = Offset(topLeft.x + tile / 2, topLeft.y + tile * 0.92f)
    val scaleX = look.scale * (1f + look.squash * 0.08f)
    val scaleY = look.scale * (1f - look.squash * 0.1f)
    withTransform({ scale(scaleX, scaleY, pivot) }) {
        drawRoundRect(
            Brush.verticalGradient(listOf(dark, lerp(dark, palette.shadow, 0.2f)), startY = top + faceHeight / 2, endY = top + depth + faceHeight),
            Offset(left, top + depth),
            Size(width, faceHeight),
            radius,
        )
        drawRoundRect(
            Brush.verticalGradient(listOf(lerp(body, light, 0.35f), body, lerp(body, dark, 0.18f)), startY = top, endY = top + faceHeight),
            Offset(left, top),
            Size(width, faceHeight),
            radius,
        )
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
            // Plank seams behind the brace.
            val seam = detail.copy(alpha = detail.alpha * 0.55f * braceAlpha)
            for (k in 1..2) {
                val y = panelTopLeft.y + panelSize.height * k / 3f
                drawLine(seam, Offset(panelTopLeft.x + tile * 0.03f, y), Offset(panelTopLeft.x + panelSize.width - tile * 0.03f, y), strokeWidth = tile * 0.022f)
            }
            drawLine(
                detail.copy(alpha = detail.alpha * braceAlpha),
                Offset(panelTopLeft.x + tile * 0.05f, panelTopLeft.y + panelSize.height - tile * 0.05f),
                Offset(panelTopLeft.x + panelSize.width - tile * 0.05f, panelTopLeft.y + tile * 0.05f),
                strokeWidth = tile * 0.06f,
                cap = StrokeCap.Round,
            )
        }
        // A nail in each corner of the frame.
        val nail = tile * 0.06f
        for ((nx, ny) in listOf(left + nail to top + nail, left + width - nail to top + nail, left + nail to top + faceHeight - nail, left + width - nail to top + faceHeight - nail)) {
            drawCircle(dark.copy(alpha = 0.75f), tile * 0.022f, Offset(nx, ny))
            drawCircle(light.copy(alpha = 0.8f), tile * 0.009f, Offset(nx - tile * 0.006f, ny - tile * 0.006f))
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
        if (look.glint > 0f && look.glint < 1f) {
            // A diagonal band of light crosses the face, top left to bottom
            // right: a gradient across the band, transparent beyond it.
            val band = tile * 0.2f
            val travel = (width + faceHeight) * DIAGONAL
            val along = -band + (travel + band * 2) * look.glint
            val start = Offset(left + (along - band) * DIAGONAL, top + (along - band) * DIAGONAL)
            val end = Offset(left + (along + band) * DIAGONAL, top + (along + band) * DIAGONAL)
            val shine = Color.White.copy(alpha = if (palette.isDark) 0.45f else 0.6f)
            drawRoundRect(
                Brush.linearGradient(listOf(Color.Transparent, shine, Color.Transparent), start, end),
                Offset(left, top),
                Size(width, faceHeight),
                radius,
            )
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
        // Lit from the top left, so the body reads as round.
        drawRoundRect(
            Brush.radialGradient(
                listOf(lerp(palette.player, palette.playerLight, 0.45f), palette.player, lerp(palette.player, palette.playerDark, 0.45f)),
                center = Offset(cx - tile * 0.1f, bodyTop + tile * 0.16f),
                radius = tile * 0.62f,
            ),
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
