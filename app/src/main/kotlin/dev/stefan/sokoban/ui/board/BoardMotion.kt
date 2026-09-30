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

    fun onEvent(event: BoardEvent, sparkColors: List<Color>) {
        when (event) {
            is BoardEvent.Stepped -> Unit
            is BoardEvent.Pushed -> {
                pulse(lean, 1f, LEAN_MS)
                val crate = event.crate
                scope.launch {
                    // The squash lands when the crate arrives, not when it leaves.
                    delay(LAND_DELAY_MS)
                    crateSquash[crate].snapTo(1f)
                    crateSquash[crate].animateTo(0f, spring(dampingRatio = 0.35f, stiffness = 600f))
                }
                if (event.enteredGoal) {
                    scope.launch {
                        delay(LAND_DELAY_MS)
                        sparkles.burst(event.to.toOffset() + Offset(0.5f, 0.45f), 14, 3.2f, 0.1f, sparkColors, 0.55f)
                        crateGlow[crate].snapTo(1f)
                        crateGlow[crate].animateTo(0f, tween(GLOW_MS))
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
                bumpDirection = event.direction
                scope.launch {
                    bump.animateTo(1f, tween(BUMP_OUT_MS))
                    bump.animateTo(0f, spring(dampingRatio = 0.4f, stiffness = 900f))
                }
                pulse(lean, 0.6f, LEAN_MS)
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
                        sparkles.burst(center, 10, 2.6f, 0.09f, sparkColors, 0.7f)
                    }
                }
                celebration.animateTo(1f, tween(CELEBRATION_MS))
            }
        }
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
        const val BUMP_OUT_MS = 60
        const val CELEBRATION_MS = 900
        const val HOP_STAGGER_MS = 70L
    }
}

internal fun Position.toOffset() = Offset(x.toFloat(), y.toFloat())
