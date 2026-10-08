package dev.stefan.sokoban.ui.board3d

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.stefan.sokoban.R
import dev.stefan.sokoban.core.Direction
import dev.stefan.sokoban.core.GameState
import dev.stefan.sokoban.core.Level
import dev.stefan.sokoban.core.Position
import dev.stefan.sokoban.game.BoardEvent
import dev.stefan.sokoban.game.Swipe
import dev.stefan.sokoban.ui.board.BoardMotion
import dev.stefan.sokoban.ui.board.GameBoard
import dev.stefan.sokoban.ui.board.ParticleStyle
import dev.stefan.sokoban.ui.board.effectColors
import dev.stefan.sokoban.ui.components.CircleIconButton
import dev.stefan.sokoban.ui.components.GameIcon
import dev.stefan.sokoban.ui.theme.palette
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * The board in 3D: the same game, animations and input as the 2D board,
 * drawn by Filament with perspective, lighting and shadows. Dust and sparks
 * are the 2D board's particles, projected through the camera onto an overlay.
 *
 * On a device that cannot run Filament, it is the 2D board.
 */
@Composable
fun GameBoard3D(
    level: Level,
    game: GameState,
    stuckCrates: Set<Int>,
    events: Flow<BoardEvent>,
    description: String,
    modifier: Modifier = Modifier,
    onMove: ((Direction) -> Unit)? = null,
    onTapCell: ((Position) -> Unit)? = null,
) {
    val context = LocalContext.current
    val host = remember { Board3dSupport.start(context) }
    if (host == null) {
        GameBoard(level, game, stuckCrates, events, description, modifier, onMove = onMove, onTapCell = onTapCell)
        return
    }
    DisposableEffect(host) { onDispose { host.destroy() } }
    FilamentBoard(host, level, game, stuckCrates, events, description, modifier, onMove, onTapCell)
}

@Composable
private fun FilamentBoard(
    host: FilamentHost,
    level: Level,
    game: GameState,
    stuckCrates: Set<Int>,
    events: Flow<BoardEvent>,
    description: String,
    modifier: Modifier,
    onMove: ((Direction) -> Unit)?,
    onTapCell: ((Position) -> Unit)?,
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

    val density = LocalDensity.current
    LaunchedEffect(host, motion, game) { host.wake() }
    LaunchedEffect(host, motion, events, effectColors) {
        events.collect {
            host.wake()
            motion.onEvent(it, effectColors)
        }
    }
    // Nothing is drawn while the screen is hidden.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(host, lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            host.paused = !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val scene = remember(host, level, colors) { BoardScene(host, level, colors, game.boxes.size) }
    val rig = scene.rig
    val currentMotion by rememberUpdatedState(motion)
    var pinching by remember(scene) { mutableStateOf(false) }
    DisposableEffect(scene) {
        rig.zoomTilePixels = with(density) { ZOOM_TILE.toPx() }
        host.rig = rig
        var last = 0L
        host.onFrame = { time ->
            val dt = if (last == 0L) 0f else ((time - last) / 1e9f).coerceIn(0f, 0.1f)
            last = time
            val current = currentMotion
            var moving = scene.update(current, time) || pinching
            // Zoomed in, the view eases along to keep the hero in sight.
            if (!pinching && rig.isZoomed) {
                val hero = current.hero.value
                val (dx, dz) = rig.slideToShow(hero.x + 0.5f, hero.y + 0.5f)
                if (abs(dx) > 0.01f || abs(dz) > 0.01f) {
                    val k = (dt * FOLLOW_RATE).coerceAtMost(1f)
                    rig.slide(dx * k, dz * k)
                    moving = true
                }
            }
            moving
        }
        onDispose {
            host.onFrame = null
            scene.destroy()
        }
    }

    val actionLabels = listOf(
        Direction.UP to stringResource(R.string.move_up),
        Direction.DOWN to stringResource(R.string.move_down),
        Direction.LEFT to stringResource(R.string.move_left),
        Direction.RIGHT to stringResource(R.string.move_right),
    )
    val swipeThreshold = with(density) { 20.dp.toPx() }

    Box(
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
        AndroidView(factory = { host.textureView }, modifier = Modifier.fillMaxSize())
        Spacer(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    rig.version // Redraws when the camera moves.
                    motion.dust.draw(this, rig.particles, ParticleStyle.Puff)
                    motion.sparkles.draw(this, rig.particles, ParticleStyle.Spark)
                }
                .pointerInput(scene, swipeThreshold, interactive) {
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
                            if (!fired && !pinch && rig.maxZoom > 1.05f && event.changes.count { it.pressed } > 1) {
                                pinch = true
                                pinching = true
                            }
                            if (pinch) {
                                host.wake()
                                rig.pinch(event.calculateCentroid(), event.calculatePan(), event.calculateZoom())
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
                            rig.cellAt(down.position.x, down.position.y)?.let { tap?.invoke(it) }
                        }
                    }
                },
        )

        if (interactive) {
            // Camera moves bump the rig's version: only crossing the zoom threshold recomposes.
            val zoomed = remember(scene) { derivedStateOf { rig.version.let { rig.isZoomed } } }
            AnimatedVisibility(
                visible = zoomed.value,
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                enter = fadeIn() + scaleIn(initialScale = 0.6f),
                exit = fadeOut() + scaleOut(targetScale = 0.6f),
            ) {
                CircleIconButton(
                    GameIcon.FIT,
                    stringResource(R.string.zoom_fit),
                    onClick = {
                        scope.launch {
                            val zoom = rig.zoom
                            val (panX, panZ) = rig.pan
                            animate(0f, 1f, animationSpec = tween(FIT_MS, easing = FastOutSlowInEasing)) { t, _ ->
                                host.wake()
                                rig.set(zoom + (1f - zoom) * t, panX * (1f - t), panZ * (1f - t))
                            }
                        }
                    },
                    size = 40.dp,
                )
            }
        }
    }
}

/** Zooming stops once tiles are about this large. */
private val ZOOM_TILE = 60.dp
private const val FOLLOW_RATE = 7f
private const val FIT_MS = 280
