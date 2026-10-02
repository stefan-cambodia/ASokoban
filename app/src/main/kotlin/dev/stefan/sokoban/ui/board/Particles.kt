package dev.stefan.sokoban.ui.board

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.geometry.Size
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** How [Particles.draw] renders each particle. */
enum class ParticleStyle {
    /** Round sparks that shrink as they fade. */
    Spark,

    /** Spinning paper strips. */
    Confetti,

    /** Soft dust that swells as it thins out. */
    Puff,
}

/**
 * A tiny particle engine: fixed-capacity arrays, no allocation per frame, and a
 * frame loop that only runs while particles are alive.
 *
 * Positions are in caller-defined units (tiles on the board, pixels for the
 * full-screen confetti); [draw] maps them with a scale and an origin.
 *
 * Time follows the system animation speed, as Compose animations do; with
 * animations turned off, no particles are made at all.
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
    private val soft = arrayOfNulls<Brush>(capacity)
    private val softBrushes = HashMap<Color, Brush>()
    private var count = 0
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private val random = Random(7)
    private var timeScale = 1f

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

    /**
     * Dust kicked up from the floor: [amount] puffs leave [center] at up to
     * [speed] units per second, squeezed vertically by [flatten] because the
     * floor is seen at an angle, and all carried along [drift].
     */
    fun puff(
        center: Offset,
        amount: Int,
        speed: Float,
        flatten: Float,
        drift: Offset,
        particleSize: Float,
        palette: List<Color>,
        lifeSeconds: Float,
    ) {
        repeat(amount) {
            val direction = random.nextFloat() * 2f * Math.PI.toFloat()
            val velocity = speed * (0.4f + random.nextFloat() * 0.6f)
            val dx = cos(direction)
            val dy = sin(direction) * flatten
            add(
                center.x + dx * particleSize * 0.6f, center.y + dy * particleSize * 0.6f,
                dx * velocity + drift.x, dy * velocity + drift.y,
                particleSize * (0.7f + random.nextFloat() * 0.6f), palette[random.nextInt(palette.size)],
                lifeSeconds * (0.75f + random.nextFloat() * 0.5f), soft = true,
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

    private fun add(px: Float, py: Float, pvx: Float, pvy: Float, s: Float, color: Color, lifeSeconds: Float, soft: Boolean = false) {
        if (count >= capacity || timeScale == 0f) return
        val i = count++
        x[i] = px; y[i] = py; vx[i] = pvx; vy[i] = pvy
        sizes[i] = s; colors[i] = color
        this.soft[i] = if (soft) softBrush(color) else null
        life[i] = lifeSeconds; maxLife[i] = lifeSeconds
        angle[i] = random.nextFloat() * 360f
        spin[i] = (random.nextFloat() - 0.5f) * 720f
    }

    /** Runs forever; suspends without frames whenever nothing is alive. */
    suspend fun run() {
        val motion = currentCoroutineContext()[MotionDurationScale]
        timeScale = motion?.scaleFactor ?: 1f
        var last = 0L
        while (true) {
            if (count == 0) {
                wake.receive()
                last = 0L
            }
            withFrameNanos { now ->
                timeScale = motion?.scaleFactor ?: 1f
                val dt = if (last == 0L) 0f else ((now - last) / 1e9f).coerceAtMost(0.05f)
                last = now
                if (timeScale == 0f) count = 0 else step(dt / timeScale)
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
        soft[i] = soft[last]
    }

    /**
     * A blurred disc of [color], radius 1 around the origin: drawn scaled, it
     * gives each puff a soft edge with no per-frame allocation and no blur
     * filter (which Android 8 lacks).
     */
    private fun softBrush(color: Color) = softBrushes.getOrPut(color) {
        Brush.radialGradient(
            0f to color,
            0.5f to color.copy(alpha = color.alpha * 0.75f),
            1f to color.copy(alpha = 0f),
            center = Offset.Zero,
            radius = 1f,
        )
    }

    /** Draws every live particle in [style]. */
    fun draw(scope: DrawScope, origin: Offset, scale: Float, style: ParticleStyle) = with(scope) {
        frame // Subscribes the caller's draw to particle updates.
        for (i in 0 until count) {
            val fade = (life[i] / maxLife[i]).coerceIn(0f, 1f)
            val color = colors[i] ?: continue
            val center = Offset(origin.x + x[i] * scale, origin.y + y[i] * scale)
            val s = sizes[i] * scale
            when (style) {
                ParticleStyle.Spark -> drawCircle(color.copy(alpha = fade), s * (0.4f + 0.6f * fade), center)
                ParticleStyle.Confetti -> rotate(angle[i], center) {
                    drawRect(color.copy(alpha = fade.coerceAtMost(0.95f)), center - Offset(s / 2, s / 4), Size(s, s / 2))
                }
                ParticleStyle.Puff -> {
                    // Fades in quickly, holds, then swells and thins out.
                    val brush = soft[i] ?: continue
                    val age = 1f - fade
                    val radius = s * (0.8f + 0.9f * age)
                    withTransform({
                        translate(center.x, center.y)
                        scale(radius, radius, Offset.Zero)
                    }) {
                        drawCircle(brush, radius = 1f, center = Offset.Zero, alpha = (fade * 1.6f).coerceAtMost(1f) * (age * 6f).coerceAtMost(1f))
                    }
                }
            }
        }
    }
}
