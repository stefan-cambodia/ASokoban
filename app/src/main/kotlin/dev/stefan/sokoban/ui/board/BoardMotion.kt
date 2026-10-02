package dev.stefan.sokoban.ui.board

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import dev.stefan.sokoban.core.Direction
import dev.stefan.sokoban.core.GameState
import dev.stefan.sokoban.core.Level
import dev.stefan.sokoban.core.Position
import dev.stefan.sokoban.game.BoardEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Colours of the one-shot effects, resolved once per theme. */
internal class EffectColors(val sparks: List<Color>, val dust: List<Color>)

/**
 * All the motion on one level's board.
 *
 * Positions are in tiles. The hero and each crate glide on their own spring;
 * when a new move interrupts one, the spring keeps its velocity, so fast
 * sequences flow as one continuous motion instead of stuttering.
 *
 * Everything here is read from draw blocks only: animating never recomposes.
 */
@Stable
internal class BoardMotion(private val level: Level, game: GameState, private val scope: CoroutineScope) {

    val hero: Animatable<Offset, AnimationVector2D> = Animatable(game.player.toOffset(), Offset.VectorConverter)
    val crates: List<Animatable<Offset, AnimationVector2D>> = game.boxes.map { Animatable(it.toOffset(), Offset.VectorConverter) }
    val crateDone: List<Animatable<Float, AnimationVector1D>> = game.boxes.map { Animatable(if (level.isGoal(it)) 1f else 0f) }
    val crateSquash: List<Animatable<Float, AnimationVector1D>> = game.boxes.map { Animatable(0f) }
    val crateGlow: List<Animatable<Float, AnimationVector1D>> = game.boxes.map { Animatable(0f) }
    val crateStuck: List<Animatable<Float, AnimationVector1D>> = game.boxes.map { Animatable(0f) }

    /** The light sweeping across a crate that reached a target: 0..1, idle at either end. */
    val crateGlint: List<Animatable<Float, AnimationVector1D>> = game.boxes.map { Animatable(0f) }
    val goalLit: Map<Position, Animatable<Float, AnimationVector1D>> = level.goals.associateWith { Animatable(0f) }

    val lean = Animatable(0f)
    val bump = Animatable(0f)
    var bumpDirection by mutableStateOf(Direction.DOWN)
        private set
    var facing by mutableStateOf(game.facing)
        private set

    val entrance = Animatable(0f)
    val celebration = Animatable(0f)

    val sparkles = Particles(capacity = 96, gravity = 5f, drag = 2.5f)

    /** Floor-level puffs: they drift up a little and stop quickly. */
    val dust = Particles(capacity = 120, gravity = -0.6f, drag = 5f)

    /** Plays the level's arrival: board fades in, crates pop, the hero lands. */
    suspend fun enter() {
        entrance.snapTo(0f)
        entrance.animateTo(1f, tween(ENTRANCE_MS, easing = FastOutSlowInEasing))
    }

    /** Moves everything towards [game], starting only the animations that changed. */
    fun sync(game: GameState, stuck: Set<Int>) {
        facing = game.facing
        glide(hero, game.player.toOffset(), HERO_SPRING)
        game.boxes.forEachIndexed { index, cell ->
            glide(crates[index], cell.toOffset(), CRATE_SPRING)
            val done = if (level.isGoal(cell)) 1f else 0f
            if (crateDone[index].targetValue != done) scope.launch { crateDone[index].animateTo(done, tween(DONE_MS)) }
            val warn = if (index in stuck) 1f else 0f
            if (crateStuck[index].targetValue != warn) scope.launch { crateStuck[index].animateTo(warn, tween(STUCK_MS)) }
        }
        if (!game.isSolved && celebration.targetValue != 0f) scope.launch { celebration.snapTo(0f) }
    }

    fun onEvent(event: BoardEvent, colors: EffectColors) {
        when (event) {
            is BoardEvent.Stepped -> scuff(event.to, event.direction, colors.dust)
            is BoardEvent.Pushed -> {
                pulse(lean, 1f, LEAN_MS)
                val crate = event.crate
                val direction = event.direction
                scuff(event.to - direction, direction, colors.dust)
                scope.launch {
                    // The squash lands when the crate arrives, not when it leaves.
                    delay(LAND_DELAY_MS)
                    // Dust squeezed out from under its two lower corners,
                    // still carried forward by the push.
                    val base = event.to.toOffset() + Offset(0.5f, 0.9f)
                    for (side in SIDES) {
                        dust.puff(
                            base + Offset(side * 0.36f, 0f), 6, 1.1f, 0.45f,
                            Offset(side * 0.45f + direction.dx * 0.5f, direction.dy * 0.3f - 0.1f),
                            0.16f, colors.dust, 0.5f,
                        )
                    }
                    crateSquash[crate].snapTo(1f)
                    crateSquash[crate].animateTo(0f, spring(dampingRatio = 0.35f, stiffness = 600f))
                }
                if (event.enteredGoal) {
                    scope.launch {
                        delay(LAND_DELAY_MS)
                        sparkles.burst(event.to.toOffset() + Offset(0.5f, 0.45f), 14, 3.2f, 0.1f, colors.sparks, 0.55f)
                        crateGlow[crate].snapTo(1f)
                        crateGlow[crate].animateTo(0f, tween(GLOW_MS))
                    }
                    scope.launch {
                        // Once the crate has turned green, a glint crosses its face.
                        delay(LAND_DELAY_MS + GLINT_DELAY_MS)
                        crateGlint[crate].snapTo(0f)
                        crateGlint[crate].animateTo(1f, tween(GLINT_MS, easing = FastOutSlowInEasing))
                    }
                    goalLit[event.to]?.let { lit ->
                        scope.launch {
                            lit.snapTo(1f)
                            lit.animateTo(0f, tween(GLOW_MS))
                        }
                    }
                }
            }
            is BoardEvent.Bumped -> {
                val direction = event.direction
                bumpDirection = direction
                scope.launch {
                    bump.animateTo(1f, tween(BUMP_OUT_MS))
                    bump.animateTo(0f, spring(dampingRatio = 0.4f, stiffness = 900f))
                }
                pulse(lean, 0.6f, LEAN_MS)
                if (event.knocked) {
                    // A puff where the hero, or the stuck crate in front of
                    // it, meets what stops it; it bounces back off.
                    val cell = (if (event.intoCrate) event.at + direction else event.at).toOffset()
                    if (direction == Direction.UP) {
                        // The hero or crate hides the middle of the obstacle's
                        // foot, so the dust escapes on either side.
                        for (side in SIDES) {
                            dust.puff(
                                cell + Offset(0.5f + side * 0.4f, WALL_DEPTH + 0.1f), 3, 0.8f, 0.6f,
                                Offset(side * 0.45f, 0.05f), 0.12f, colors.dust, 0.45f,
                            )
                        }
                    } else {
                        dust.puff(
                            cell + contact(direction), 6, 1f, 0.6f,
                            Offset(-direction.dx * 0.5f, -direction.dy * 0.4f - 0.15f),
                            0.13f, colors.dust, 0.45f,
                        )
                    }
                }
            }
            BoardEvent.Undone -> Unit
            BoardEvent.Restarted -> scope.launch {
                celebration.snapTo(0f)
                entrance.snapTo(0.55f)
                entrance.animateTo(1f, tween(RESTART_MS, easing = FastOutSlowInEasing))
            }
            BoardEvent.Solved -> scope.launch {
                celebration.snapTo(0f)
                delay(LAND_DELAY_MS)
                crates.forEachIndexed { index, crate ->
                    val center = crate.targetValue + Offset(0.5f, 0.3f)
                    scope.launch {
                        delay(index * HOP_STAGGER_MS)
                        sparkles.burst(center, 10, 2.6f, 0.09f, colors.sparks, 0.7f)
                    }
                }
                celebration.animateTo(1f, tween(CELEBRATION_MS))
            }
        }
    }

    /** A faint scuff where the hero's back foot pushed off, on its way to [to]. */
    private fun scuff(to: Position, direction: Direction, palette: List<Color>) {
        val heel = (to - direction).toOffset() + Offset(0.5f + direction.dx * 0.1f, 0.88f + direction.dy * 0.05f)
        dust.puff(heel, 3, 0.45f, 0.5f, Offset(-direction.dx * 0.5f, -direction.dy * 0.35f), 0.1f, palette, 0.4f)
    }

    private fun glide(animatable: Animatable<Offset, AnimationVector2D>, target: Offset, spec: androidx.compose.animation.core.SpringSpec<Offset>) {
        if (animatable.targetValue == target) return
        scope.launch { animatable.animateTo(target, spec) }
    }

    private fun pulse(animatable: Animatable<Float, AnimationVector1D>, peak: Float, durationMs: Int) {
        scope.launch {
            animatable.snapTo(peak)
            animatable.animateTo(0f, tween(durationMs))
        }
    }

    companion object {
        private val SIDES = floatArrayOf(-1f, 1f)

        /**
         * Where something standing on a cell meets an obstacle to its side or
         * below, at floor level: the cell below starts at the bottom edge.
         */
        private fun contact(direction: Direction) = when (direction) {
            Direction.LEFT -> Offset(0.08f, 0.8f)
            Direction.RIGHT -> Offset(0.92f, 0.8f)
            Direction.UP, Direction.DOWN -> Offset(0.5f, 0.96f)
        }

        /** Critically damped: fast, precise, no overshoot for the hero. */
        val HERO_SPRING = spring(dampingRatio = 1f, stiffness = 1500f, visibilityThreshold = Offset(0.001f, 0.001f))

        /** A hair of overshoot: crates slide, then settle. */
        val CRATE_SPRING = spring(dampingRatio = 0.72f, stiffness = 1300f, visibilityThreshold = Offset(0.001f, 0.001f))

        const val ENTRANCE_MS = 650
        const val RESTART_MS = 380
        const val DONE_MS = 220
        const val STUCK_MS = 260
        const val LEAN_MS = 180
        const val LAND_DELAY_MS = 90L
        const val GLOW_MS = 700
        const val GLINT_DELAY_MS = 120L
        const val GLINT_MS = 520
        const val BUMP_OUT_MS = 60
        const val CELEBRATION_MS = 900
        const val HOP_STAGGER_MS = 70L
    }
}

internal fun Position.toOffset() = Offset(x.toFloat(), y.toFloat())
