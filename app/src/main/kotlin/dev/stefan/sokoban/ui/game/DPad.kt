package dev.stefan.sokoban.ui.game

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.stefan.sokoban.R
import dev.stefan.sokoban.core.Direction
import dev.stefan.sokoban.ui.components.GameIcon
import dev.stefan.sokoban.ui.components.GameIconView
import dev.stefan.sokoban.ui.theme.palette
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Direction for a touch at ([dx], [dy]) from the pad's centre, or null inside
 * the central dead zone of radius [deadZone]. The four quadrants are rotated
 * 45°, so the whole disk is live: no gap between keys swallows a press.
 */
internal fun padDirection(dx: Float, dy: Float, deadZone: Float): Direction? {
    if (dx * dx + dy * dy < deadZone * deadZone) return null
    return if (abs(dx) > abs(dy)) {
        if (dx > 0) Direction.RIGHT else Direction.LEFT
    } else {
        if (dy > 0) Direction.DOWN else Direction.UP
    }
}

/**
 * The on-screen pad. Touching anywhere on the disk moves at once; holding
 * repeats after a deliberate pause; sliding to another direction switches
 * immediately, like a physical pad. [onMove] is told which moves are repeats.
 */
@Composable
fun DPad(size: Dp, onMove: (direction: Direction, held: Boolean) -> Unit, modifier: Modifier = Modifier) {
    val colors = palette
    val scope = rememberCoroutineScope()
    val move by rememberUpdatedState(onMove)
    var active by remember { mutableStateOf<Direction?>(null) }

    Box(
        modifier
            .size(size)
            .pointerInput(Unit) {
                val deadZone = this.size.width * DEAD_ZONE
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val center = Offset(this.size.width / 2f, this.size.height / 2f)
                    var repeater: Job? = null
                    fun press(direction: Direction?) {
                        if (direction == active) return
                        repeater?.cancel()
                        active = direction
                        if (direction == null) return
                        move(direction, false)
                        repeater = scope.launch {
                            delay(HOLD_DELAY_MS)
                            while (true) {
                                move(direction, true)
                                delay(REPEAT_MS)
                            }
                        }
                    }
                    // However the gesture ends (lift, or the system taking the
                    // touch away), the repeat stops with it.
                    try {
                        press(padDirection(down.position.x - center.x, down.position.y - center.y, deadZone))
                        down.consume()
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            change.consume()
                            val point = change.position
                            press(padDirection(point.x - center.x, point.y - center.y, deadZone))
                        }
                    } finally {
                        repeater?.cancel()
                        active = null
                    }
                }
            },
    ) {
        // Base plate.
        Box(
            Modifier
                .align(Alignment.Center)
                .size(size * 0.94f)
                .shadow(10.dp, CircleShape, ambientColor = colors.shadow, spotColor = colors.shadow)
                .background(Brush.verticalGradient(listOf(colors.surfaceRaised, colors.surface)), CircleShape)
                .border(1.dp, colors.outline, CircleShape),
        )
        Box(
            Modifier
                .align(Alignment.Center)
                .size(size * 0.17f)
                .background(colors.background, CircleShape)
                .border(1.dp, colors.outline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(size * 0.06f).background(colors.accent.copy(alpha = 0.8f), CircleShape))
        }
        val key = size * 0.3f
        val inset = size * 0.07f
        PadKey(Direction.UP, active == Direction.UP, key, stringResource(R.string.move_up), { move(it, false) }, Modifier.align(Alignment.TopCenter).padding(top = inset))
        PadKey(Direction.DOWN, active == Direction.DOWN, key, stringResource(R.string.move_down), { move(it, false) }, Modifier.align(Alignment.BottomCenter).padding(bottom = inset))
        PadKey(Direction.LEFT, active == Direction.LEFT, key, stringResource(R.string.move_left), { move(it, false) }, Modifier.align(Alignment.CenterStart).padding(start = inset))
        PadKey(Direction.RIGHT, active == Direction.RIGHT, key, stringResource(R.string.move_right), { move(it, false) }, Modifier.align(Alignment.CenterEnd).padding(end = inset))
    }
}

@Composable
private fun PadKey(
    direction: Direction,
    pressed: Boolean,
    size: Dp,
    label: String,
    onActivate: (Direction) -> Unit,
    modifier: Modifier,
) {
    val colors = palette
    val press by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = if (pressed) spring(stiffness = 3000f) else spring(dampingRatio = 0.45f, stiffness = 900f),
        label = "key",
    )
    val shape = RoundedCornerShape(size * 0.3f)
    val rotation = when (direction) {
        Direction.LEFT -> 0f
        Direction.UP -> 90f
        Direction.RIGHT -> 180f
        Direction.DOWN -> 270f
    }
    Box(
        modifier
            .size(size)
            .semantics {
                contentDescription = label
                role = Role.Button
                onClick(label) {
                    onActivate(direction)
                    true
                }
            }
            .graphicsLayer {
                val scale = 1f - 0.1f * press
                scaleX = scale
                scaleY = scale
            }
            .shadow((6 - 5 * press).dp, shape, ambientColor = colors.shadow, spotColor = colors.shadow)
            .background(
                Brush.verticalGradient(
                    listOf(
                        lerp(colors.surfaceRaised, colors.accent.copy(alpha = 0.2f).compositeOver(colors.surfaceRaised), press),
                        lerp(colors.surface, colors.accent.copy(alpha = 0.3f).compositeOver(colors.surface), press),
                    ),
                ),
                shape,
            )
            .border(1.dp, lerp(colors.outline, colors.accent, press), shape),
        contentAlignment = Alignment.Center,
    ) {
        GameIconView(
            GameIcon.BACK,
            lerp(colors.textSecondary, colors.accentDeep, press),
            Modifier.rotate(rotation),
            size = size * 0.42f,
        )
    }
}

private const val DEAD_ZONE = 0.09f
private const val HOLD_DELAY_MS = 320L
private const val REPEAT_MS = 125L
