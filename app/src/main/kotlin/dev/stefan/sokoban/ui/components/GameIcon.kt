package dev.stefan.sokoban.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The game's icon set, drawn in code on a 24-unit grid with round strokes, so
 * every icon shares one visual language with the hand-drawn board.
 */
enum class GameIcon {
    BACK, RESTART, UNDO, PLAY, NEXT, LEVELS, SETTINGS, LOCK, STAR, CHECK, SOUND, SOUND_OFF, VIBRATION, THEME, CLOSE, FIT;

    /** Strokes and fills, in 24-unit coordinates scaled by [s]. */
    internal fun shapes(s: Float): List<Pair<Path, Boolean>> {
        val strokes = Path()
        val fills = Path()
        fun p(x: Float, y: Float) = Offset(x * s, y * s)
        fun Path.poly(vararg points: Float, close: Boolean = false) {
            moveTo(points[0] * s, points[1] * s)
            var i = 2
            while (i < points.size) {
                lineTo(points[i] * s, points[i + 1] * s)
                i += 2
            }
            if (close) close()
        }
        fun Path.circle(cx: Float, cy: Float, r: Float) = addOval(Rect(p(cx - r, cy - r), p(cx + r, cy + r)))

        when (this) {
            BACK -> strokes.poly(15f, 5f, 8f, 12f, 15f, 19f)
            NEXT -> {
                strokes.poly(5f, 12f, 19f, 12f)
                strokes.poly(13f, 6f, 19f, 12f, 13f, 18f)
            }
            RESTART -> {
                // An almost closed circle with an arrowhead on its end.
                val center = p(12f, 12.5f)
                val radius = 7f * s
                strokes.arcTo(Rect(center, radius), -60f, 300f, forceMoveTo = true)
                val end = 240.0 * PI / 180
                val tip = Offset(center.x + radius * cos(end).toFloat(), center.y + radius * sin(end).toFloat())
                val tangent = Offset(-sin(end).toFloat(), cos(end).toFloat())
                val normal = Offset(-tangent.y, tangent.x)
                val back = tip - tangent * (3.2f * s)
                strokes.moveTo(back.x + normal.x * 3f * s, back.y + normal.y * 3f * s)
                strokes.lineTo(tip.x + tangent.x * 0.6f * s, tip.y + tangent.y * 0.6f * s)
                strokes.lineTo(back.x - normal.x * 3f * s, back.y - normal.y * 3f * s)
            }
            UNDO -> {
                strokes.moveTo(4.5f * s, 9f * s)
                strokes.lineTo(14f * s, 9f * s)
                strokes.cubicTo(17.9f * s, 9f * s, 20f * s, 11.6f * s, 20f * s, 14.5f * s)
                strokes.cubicTo(20f * s, 17.4f * s, 17.9f * s, 20f * s, 14f * s, 20f * s)
                strokes.lineTo(9f * s, 20f * s)
                strokes.poly(8.5f, 5f, 4.5f, 9f, 8.5f, 13f)
            }
            PLAY -> {
                fills.poly(8f, 5.5f, 19f, 12f, 8f, 18.5f, close = true)
                strokes.poly(8f, 5.5f, 19f, 12f, 8f, 18.5f, close = true)
            }
            LEVELS -> for ((x, y) in listOf(4f to 4f, 13.5f to 4f, 4f to 13.5f, 13.5f to 13.5f)) {
                strokes.addRoundRect(RoundRect(Rect(p(x, y), p(x + 6.5f, y + 6.5f)), CornerRadius(2f * s)))
            }
            SETTINGS -> {
                for ((y, knob) in listOf(6f to 15f, 12f to 8.5f, 18f to 13f)) {
                    strokes.poly(4f, y, 20f, y)
                    fills.circle(knob, y, 2.6f)
                }
            }
            LOCK -> {
                fills.addRoundRect(RoundRect(Rect(p(5f, 10.5f), p(19f, 20.5f)), CornerRadius(2.5f * s)))
                strokes.moveTo(8f * s, 10.5f * s)
                strokes.lineTo(8f * s, 8f * s)
                strokes.arcTo(Rect(p(8f, 4f), p(16f, 12f)), 180f, 180f, forceMoveTo = false)
                strokes.lineTo(16f * s, 10.5f * s)
            }
            STAR -> {
                val points = FloatArray(20)
                for (i in 0 until 10) {
                    val angle = -PI / 2 + i * PI / 5
                    val r = if (i % 2 == 0) 9.5 else 4.2
                    points[i * 2] = (12 + r * cos(angle)).toFloat()
                    points[i * 2 + 1] = (12.8 + r * sin(angle)).toFloat()
                }
                fills.poly(*points, close = true)
                strokes.poly(*points, close = true)
            }
            CHECK -> strokes.poly(5f, 12.5f, 10f, 17f, 19f, 7.5f)
            SOUND, SOUND_OFF -> {
                fills.poly(4f, 9f, 8f, 9f, 12.5f, 5f, 12.5f, 19f, 8f, 15f, 4f, 15f, close = true)
                strokes.poly(4f, 9f, 8f, 9f, 12.5f, 5f, 12.5f, 19f, 8f, 15f, 4f, 15f, close = true)
                if (this == SOUND) {
                    strokes.arcTo(Rect(p(12f, 12f), 4f * s), -50f, 100f, forceMoveTo = true)
                    strokes.arcTo(Rect(p(12f, 12f), 7.5f * s), -50f, 100f, forceMoveTo = true)
                } else {
                    strokes.poly(16f, 9f, 21f, 15f)
                    strokes.poly(21f, 9f, 16f, 15f)
                }
            }
            VIBRATION -> {
                strokes.addRoundRect(RoundRect(Rect(p(8f, 4f), p(16f, 20f)), CornerRadius(2f * s)))
                strokes.poly(4f, 9f, 4f, 15f)
                strokes.poly(20f, 9f, 20f, 15f)
            }
            THEME -> {
                strokes.circle(12f, 12f, 8f)
                fills.moveTo(12f * s, 4f * s)
                fills.arcTo(Rect(p(12f, 12f), 8f * s), -90f, 180f, forceMoveTo = false)
                fills.close()
            }
            CLOSE -> {
                strokes.poly(6f, 6f, 18f, 18f)
                strokes.poly(18f, 6f, 6f, 18f)
            }
            // Four corners pointing out: the whole board.
            FIT -> {
                strokes.poly(4.5f, 9.5f, 4.5f, 4.5f, 9.5f, 4.5f)
                strokes.poly(14.5f, 4.5f, 19.5f, 4.5f, 19.5f, 9.5f)
                strokes.poly(19.5f, 14.5f, 19.5f, 19.5f, 14.5f, 19.5f)
                strokes.poly(9.5f, 19.5f, 4.5f, 19.5f, 4.5f, 14.5f)
            }
        }
        return listOf(fills to true, strokes to false)
    }
}

@Composable
fun GameIconView(icon: GameIcon, tint: Color, modifier: Modifier = Modifier, size: Dp = 24.dp) {
    Spacer(
        modifier
            .size(size)
            .drawWithCache {
                val scale = this.size.minDimension / 24f
                val shapes = icon.shapes(scale)
                val stroke = Stroke(width = 2.3f * scale, cap = StrokeCap.Round, join = StrokeJoin.Round)
                onDrawBehind {
                    for ((path, fill) in shapes) drawPath(path, tint, style = if (fill) Fill else stroke)
                }
            },
    )
}
