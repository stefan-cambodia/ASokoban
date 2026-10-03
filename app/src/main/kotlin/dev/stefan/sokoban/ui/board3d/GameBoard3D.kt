package dev.stefan.sokoban.ui.board3d

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import dev.stefan.sokoban.ui.board.effectColors
import dev.stefan.sokoban.ui.theme.palette
import kotlinx.coroutines.flow.Flow

/**
 * The board in 3D: the same game, animations and input as the 2D board,
 * drawn by Filament with perspective, lighting and shadows.
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
    val colors = palette
    val scope = rememberCoroutineScope()
    val motion = remember(level) { BoardMotion(level, game, scope) }
    val effectColors = remember(colors) { effectColors(colors) }
    val move by rememberUpdatedState(onMove)
    val tap by rememberUpdatedState(onTapCell)
    val interactive = onMove != null

    LaunchedEffect(motion) { motion.enter() }
    LaunchedEffect(motion, game, stuckCrates) { motion.sync(game, stuckCrates) }
    LaunchedEffect(motion, events, effectColors) { events.collect { motion.onEvent(it, effectColors) } }

    val context = LocalContext.current
    val host = remember { FilamentHost(context) }
    DisposableEffect(host) { onDispose { host.destroy() } }
    val scene = remember(host, level, colors) { BoardScene(host, level, colors, game.boxes.size) }
    val currentMotion by rememberUpdatedState(motion)
    DisposableEffect(scene) {
        host.rig = scene.rig
        host.onFrame = { time -> scene.update(currentMotion, time) }
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
    val swipeThreshold = with(LocalDensity.current) { 20.dp.toPx() }

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
                .pointerInput(host, swipeThreshold, interactive) {
                    if (!interactive) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var fired = false
                        var travel = Offset.Zero
                        while (true) {
                            val event = awaitPointerEvent()
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
                        if (!fired && travel.getDistance() < viewConfiguration.touchSlop) {
                            host.rig?.cellAt(down.position.x, down.position.y, size.width, size.height)?.let { tap?.invoke(it) }
                        }
                    }
                },
        )
    }
}
