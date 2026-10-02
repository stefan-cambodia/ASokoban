package dev.stefan.sokoban.ui.board

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.stefan.sokoban.R
import dev.stefan.sokoban.core.Direction
import dev.stefan.sokoban.core.GameState
import dev.stefan.sokoban.core.Level
import dev.stefan.sokoban.core.Position
import dev.stefan.sokoban.game.BoardEvent
import dev.stefan.sokoban.game.Swipe
import dev.stefan.sokoban.ui.components.CircleIconButton
import dev.stefan.sokoban.ui.components.GameIcon
import dev.stefan.sokoban.ui.theme.Palette
import dev.stefan.sokoban.ui.theme.palette
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** Where the board sits on screen: tile size and top-left corner, in pixels. */
internal data class BoardMetrics(val tile: Float, val origin: Offset) {

    fun cellAt(point: Offset) = Position(
        floor((point.x - origin.x) / tile).toInt(),
        floor((point.y - origin.y) / tile).toInt(),
    )

    fun topLeft(x: Float, y: Float) = Offset(origin.x + x * tile, origin.y + y * tile)

    companion object {
        /** The largest square tiles that fit, never stretched, centred. */
        fun fit(level: Level, width: Float, height: Float, maxTile: Float): BoardMetrics {
            val rows = level.height + WALL_DEPTH
            val tile = minOf(width / level.width, height / rows, maxTile)
            return BoardMetrics(tile, Offset((width - level.width * tile) / 2f, (height - rows * tile) / 2f))
        }
    }
}

/**
 * A living board: [game] drawn over [level], every change animated, plus the
 * one-shot flourishes in [events].
 *
 * With [onMove] null the board is a display only (the home screen vignette);
 * otherwise it takes swipes and taps and exposes moves to accessibility.
 */
@Composable
fun GameBoard(
    level: Level,
    game: GameState,
    stuckCrates: Set<Int>,
    events: Flow<BoardEvent>,
    description: String,
    modifier: Modifier = Modifier,
    onMove: ((Direction) -> Unit)? = null,
    onTapCell: ((Position) -> Unit)? = null,
) {
    val colors = palette
    val scope = rememberCoroutineScope()
    val motion = remember(level) { BoardMotion(level, game, scope) }
    val effectColors = remember(colors) { effectColors(colors) }
    val move by rememberUpdatedState(onMove)
    val tap by rememberUpdatedState(onTapCell)
    val interactive = onMove != null

    LaunchedEffect(motion) { motion.enter() }
    LaunchedEffect(motion) { motion.sparkles.run() }
    LaunchedEffect(motion) { motion.dust.run() }
    LaunchedEffect(motion, game, stuckCrates) { motion.sync(game, stuckCrates) }
    LaunchedEffect(motion, events, effectColors) { events.collect { motion.onEvent(it, effectColors) } }

    val idle = rememberInfiniteTransition(label = "idle")
    val clock = idle.animateFloat(0f, 1f, infiniteRepeatable(tween(IDLE_CYCLE_MS, easing = LinearEasing)), label = "clock")
    val blink = idle.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = BLINK_CYCLE_MS
                0f at BLINK_CYCLE_MS - 260
                1f at BLINK_CYCLE_MS - 190
                0f at BLINK_CYCLE_MS - 110
            },
        ),
        label = "blink",
    )

    val actionLabels = listOf(
        Direction.UP to stringResource(R.string.move_up),
        Direction.DOWN to stringResource(R.string.move_down),
        Direction.LEFT to stringResource(R.string.move_left),
        Direction.RIGHT to stringResource(R.string.move_right),
    )

    BoxWithConstraints(
        modifier.semantics {
            contentDescription = description
            if (interactive) {
                customActions = actionLabels.map { (direction, label) ->
                    CustomAccessibilityAction(label) {
                        move?.invoke(direction)
                        true
                    }
                }
            }
        },
    ) {
        val maxTile = with(LocalDensity.current) { MAX_TILE.toPx() }
        val zoomTile = with(LocalDensity.current) { ZOOM_TILE.toPx() }
        val swipeThreshold = with(LocalDensity.current) { SWIPE_THRESHOLD.toPx() }
        val viewport = Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
        val metrics = remember(level, viewport, maxTile) {
            BoardMetrics.fit(level, viewport.width, viewport.height, maxTile)
        }
        val camera = remember(level, metrics, viewport, zoomTile) { BoardCamera(level, metrics, viewport, zoomTile) }
        var pinching by remember(camera) { mutableStateOf(false) }

        // Zoomed in, the view keeps the hero in sight.
        LaunchedEffect(camera, game.player) {
            val target = camera.panToShow(game.player) ?: return@LaunchedEffect
            val start = camera.pan
            animate(0f, 1f, animationSpec = tween(FOLLOW_MS, easing = FastOutSlowInEasing)) { t, _ ->
                if (!pinching) camera.set(camera.zoom, lerp(start, target, t))
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                // Zoomed in, the board stops at its frame; fitted, its effects may spill over.
                .graphicsLayer { clip = camera.isZoomed }
                .pointerInput(metrics, camera, swipeThreshold, interactive) {
                    if (!interactive) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var fired = false
                        var travel = Offset.Zero
                        // A second finger before any move makes the gesture a pinch to its end.
                        var pinch = false
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.changes.none { it.pressed }) break
                            if (!fired && !pinch && camera.canZoom && event.changes.count { it.pressed } > 1) {
                                pinch = true
                                pinching = true
                            }
                            if (pinch) {
                                camera.transform(event.calculateCentroid(), event.calculatePan(), event.calculateZoom())
                                event.changes.forEach { it.consume() }
                                continue
                            }
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            travel = change.position - down.position
                            // One move per gesture, as soon as the direction is clear.
                            if (!fired) {
                                Swipe.classify(travel.x, travel.y, swipeThreshold)?.let {
                                    fired = true
                                    move?.invoke(it)
                                }
                            }
                            if (fired) change.consume()
                        }
                        pinching = false
                        if (!fired && !pinch && travel.getDistance() < viewConfiguration.touchSlop) {
                            tap?.invoke(metrics.cellAt(camera.toContent(down.position)))
                        }
                    }
                },
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(0f, 0f)
                        scaleX = camera.zoom
                        scaleY = camera.zoom
                        translationX = camera.pan.x
                        translationY = camera.pan.y
                    },
            ) {
                // Static layer: recorded once per size/theme, replayed every frame.
                Spacer(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val entrance = motion.entrance.value
                            alpha = entrance.coerceIn(0f, 1f)
                            val scale = 0.95f + 0.05f * entrance
                            scaleX = scale
                            scaleY = scale
                        }
                        .drawWithCache {
                            val board = StaticBoard(level, metrics.tile, metrics.origin)
                            onDrawBehind { board.draw(this, colors) }
                        },
                )

                val crateLook = remember(motion) { CrateLook() }
                val heroLook = remember(motion) { HeroLook() }
                val order = remember(motion) { IntArray(motion.crates.size + 1) }
                val depth = remember(motion) { FloatArray(motion.crates.size + 1) }

                Spacer(
                    Modifier
                        .fillMaxSize()
                        .drawBehind {
                            drawDynamic(
                                level, motion, metrics, colors, clock.value, blink.value, crateLook, heroLook, order, depth,
                            )
                        },
                )
            }
        }

        if (interactive && camera.canZoom) {
            AnimatedVisibility(
                visible = camera.isZoomed,
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                enter = fadeIn() + scaleIn(initialScale = 0.6f),
                exit = fadeOut() + scaleOut(targetScale = 0.6f),
            ) {
                CircleIconButton(
                    GameIcon.FIT,
                    stringResource(R.string.zoom_fit),
                    onClick = {
                        scope.launch {
                            val zoom = camera.zoom
                            val pan = camera.pan
                            animate(0f, 1f, animationSpec = tween(FOLLOW_MS, easing = FastOutSlowInEasing)) { t, _ ->
                                camera.set(zoom + (1f - zoom) * t, lerp(pan, Offset.Zero, t))
                            }
                        }
                    },
                    size = 40.dp,
                )
            }
        }
    }
}

private fun DrawScope.drawDynamic(
    level: Level,
    motion: BoardMotion,
    metrics: BoardMetrics,
    colors: Palette,
    clock: Float,
    blink: Float,
    crateLook: CrateLook,
    heroLook: HeroLook,
    order: IntArray,
    depth: FloatArray,
) {
    val tile = metrics.tile
    val entrance = motion.entrance.value
    val celebration = motion.celebration.value
    val phase = clock * 2f * PI.toFloat()

    val pulse = (sin(phase * 2f) + 1f) / 2f
    for ((cell, lit) in motion.goalLit) {
        drawGoal(metrics.topLeft(cell.x.toFloat(), cell.y.toFloat()), tile, colors, pulse, lit.value)
    }
    // Dust stays on the floor: whatever stands in front of it hides it.
    motion.dust.draw(this, metrics.origin, tile, ParticleStyle.Puff)

    // Painter's order by row: whatever stands lower on screen is in front.
    val heroSlot = motion.crates.size
    for (i in motion.crates.indices) depth[i] = motion.crates[i].value.y
    depth[heroSlot] = motion.hero.value.y + 0.01f
    for (i in order.indices) order[i] = i
    for (i in 1 until order.size) {
        val key = order[i]
        var j = i - 1
        while (j >= 0 && depth[order[j]] > depth[key]) {
            order[j + 1] = order[j]
            j--
        }
        order[j + 1] = key
    }

    for (slot in order) {
        if (slot == heroSlot) {
            val position = motion.hero.value
            val velocity = motion.hero.velocity
            val appear = ((entrance - 0.35f) / 0.55f).coerceIn(0f, 1f)
            heroLook.facing = motion.facing
            heroLook.blink = blink
            heroLook.breath = sin(phase * 2f)
            heroLook.stretch = (hypot(velocity.x, velocity.y) / 14f).coerceIn(0f, 1f)
            heroLook.lean = motion.lean.value
            heroLook.stride = (position.x + position.y) * 2f * PI.toFloat()
            heroLook.sway = (sin(phase * 3f) * 0.6f - velocity.x * 0.05f).coerceIn(-1f, 1f)
            heroLook.drop = (1f - easeOutBounce(appear)) * 1.4f
            heroLook.scale = if (appear > 0f) 1f else 0f
            heroLook.joy = (celebration * 3f).coerceAtMost(1f)
            val bump = motion.bump.value * 0.14f
            val direction = motion.bumpDirection
            drawHero(
                metrics.topLeft(position.x + direction.dx * bump, position.y + direction.dy * bump),
                tile, colors, heroLook,
            )
        } else {
            val position = motion.crates[slot].value
            val appear = ((entrance - 0.2f - slot * 0.05f) / 0.45f).coerceIn(0f, 1f)
            val hopPhase = (celebration * 1.8f - slot * 0.12f).coerceIn(0f, 1f)
            crateLook.done = motion.crateDone[slot].value
            crateLook.stuck = motion.crateStuck[slot].value
            crateLook.squash = motion.crateSquash[slot].value
            crateLook.glow = motion.crateGlow[slot].value
            crateLook.glint = motion.crateGlint[slot].value
            crateLook.scale = easeOutBack(appear)
            crateLook.hop = sin(hopPhase * PI.toFloat()) * 0.3f
            drawCrate(metrics.topLeft(position.x, position.y), tile, colors, crateLook)
        }
    }

    motion.sparkles.draw(this, metrics.origin, tile, ParticleStyle.Spark)
}

private fun effectColors(colors: Palette) = EffectColors(
    sparks = listOf(colors.goalGlow, colors.star, colors.crateDoneLight, Color.White),
    // Pale dust on the light floor, a lighter haze on the dark one.
    dust = if (colors.isDark) {
        listOf(lerp(colors.floor, Color.White, 0.32f).copy(alpha = 0.9f), lerp(colors.floor, colors.wallHighlight, 0.55f).copy(alpha = 0.8f))
    } else {
        listOf(lerp(colors.floor, Color.White, 0.7f).copy(alpha = 0.95f), lerp(colors.floorEdge, colors.shadow, 0.12f).copy(alpha = 0.75f))
    },
)

internal fun easeOutBack(t: Float): Float {
    val c1 = 1.70158f
    val c3 = c1 + 1f
    val u = t - 1f
    return 1f + c3 * u * u * u + c1 * u * u
}

internal fun easeOutBounce(t: Float): Float {
    val n1 = 7.5625f
    val d1 = 2.75f
    return when {
        t < 1f / d1 -> n1 * t * t
        t < 2f / d1 -> (t - 1.5f / d1).let { n1 * it * it + 0.75f }
        t < 2.5f / d1 -> (t - 2.25f / d1).let { n1 * it * it + 0.9375f }
        else -> (t - 2.625f / d1).let { n1 * it * it + 0.984375f }
    }
}

private val MAX_TILE = 72.dp

/** Zooming stops once tiles are this large. */
private val ZOOM_TILE = 60.dp
private const val FOLLOW_MS = 280
private val SWIPE_THRESHOLD = 20.dp
private const val IDLE_CYCLE_MS = 4000
private const val BLINK_CYCLE_MS = 3700
