package dev.stefan.sokoban.ui.board

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.geometry.Size
import kotlinx.coroutines.channels.Channel
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * A tiny particle engine: fixed-capacity arrays, no allocation per frame, and a
 * frame loop that only runs while particles are alive.
 *
 * Positions are in caller-defined units (tiles on the board, pixels for the
 * full-screen confetti); [draw] maps them with a scale and an origin.
 */
@Stable
class Particles(private val capacity: Int, private val gravity: Float, private val drag: Float) {

    private val x = FloatArray(capacity)
    private val y = FloatArray(capacity)
    private val vx = FloatArray(capacity)
    private val vy = FloatArray(capacity)
    private val life = FloatArray(capacity)
    private val maxLife = FloatArray(capacity)
    private val sizes = FloatArray(capacity)
    private val spin = FloatArray(capacity)
    private val angle = FloatArray(capacity)
    private val colors = arrayOfNulls<Color>(capacity)
    private var count = 0
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private val random = Random(7)

    /** Changes every animated frame: reading it in a draw block schedules redraws. */
    var frame by mutableLongStateOf(0L)
        private set

    val isActive: Boolean get() = count > 0

    /** Radial burst around [center], at [speed] units per second. */
    fun burst(center: Offset, amount: Int, speed: Float, particleSize: Float, palette: List<Color>, lifeSeconds: Float) {
        repeat(amount) {
            val direction = random.nextFloat() * 2f * Math.PI.toFloat()
            val velocity = speed * (0.5f + random.nextFloat() * 0.7f)
            add(
                center.x, center.y, cos(direction) * velocity, sin(direction) * velocity,
                particleSize * (0.6f + random.nextFloat() * 0.6f), palette[random.nextInt(palette.size)],
                lifeSeconds * (0.7f + random.nextFloat() * 0.5f),
            )
        }
        wake.trySend(Unit)
    }

    /** Confetti raining from the top edge of a [width]-wide area. */
    fun rain(width: Float, amount: Int, speed: Float, particleSize: Float, palette: List<Color>, lifeSeconds: Float) {
        repeat(amount) {
            add(
                random.nextFloat() * width, -particleSize * (1f + random.nextFloat() * 8f),
                (random.nextFloat() - 0.5f) * speed * 0.6f, speed * (0.4f + random.nextFloat() * 0.6f),
                particleSize * (0.7f + random.nextFloat() * 0.6f), palette[random.nextInt(palette.size)],
                lifeSeconds * (0.8f + random.nextFloat() * 0.4f),
            )
        }
        wake.trySend(Unit)
    }

    private fun add(px: Float, py: Float, pvx: Float, pvy: Float, s: Float, color: Color, lifeSeconds: Float) {
        if (count >= capacity) return
        val i = count++
        x[i] = px; y[i] = py; vx[i] = pvx; vy[i] = pvy
        sizes[i] = s; colors[i] = color
        life[i] = lifeSeconds; maxLife[i] = lifeSeconds
        angle[i] = random.nextFloat() * 360f
        spin[i] = (random.nextFloat() - 0.5f) * 720f
    }

    /** Runs forever; suspends without frames whenever nothing is alive. */
    suspend fun run() {
        var last = 0L
        while (true) {
            if (count == 0) {
                wake.receive()
                last = 0L
            }
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else ((now - last) / 1e9f).coerceAtMost(0.05f)
                last = now
                step(dt)
                frame = now
            }
        }
    }

    private fun step(dt: Float) {
        var i = 0
        while (i < count) {
            life[i] -= dt
            if (life[i] <= 0f) {
                remove(i)
                continue
            }
            vy[i] += gravity * dt
            val damping = 1f - drag * dt
            vx[i] *= damping
            vy[i] *= damping
            x[i] += vx[i] * dt
            y[i] += vy[i] * dt
            angle[i] += spin[i] * dt
            i++
        }
    }

    private fun remove(i: Int) {
        val last = --count
        x[i] = x[last]; y[i] = y[last]; vx[i] = vx[last]; vy[i] = vy[last]
        life[i] = life[last]; maxLife[i] = maxLife[last]; sizes[i] = sizes[last]
        colors[i] = colors[last]; angle[i] = angle[last]; spin[i] = spin[last]
    }

    /** Draws round sparks ([confetti] false) or spinning paper strips. */
    fun draw(scope: DrawScope, origin: Offset, scale: Float, confetti: Boolean) = with(scope) {
        frame // Subscribes the caller's draw to particle updates.
        for (i in 0 until count) {
            val fade = (life[i] / maxLife[i]).coerceIn(0f, 1f)
            val color = colors[i] ?: continue
            val center = Offset(origin.x + x[i] * scale, origin.y + y[i] * scale)
            val s = sizes[i] * scale
            if (confetti) {
                rotate(angle[i], center) {
                    drawRect(color.copy(alpha = fade.coerceAtMost(0.95f)), center - Offset(s / 2, s / 4), Size(s, s / 2))
                }
            } else {
                drawCircle(color.copy(alpha = fade), s * (0.4f + 0.6f * fade), center)
            }
        }
    }
}
