package dev.stefan.sokoban.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.stefan.sokoban.R
import dev.stefan.sokoban.core.Direction
import dev.stefan.sokoban.core.GameEngine
import dev.stefan.sokoban.core.GameState
import dev.stefan.sokoban.core.LevelParser
import dev.stefan.sokoban.core.MoveResult
import dev.stefan.sokoban.core.levels.LevelPack
import dev.stefan.sokoban.core.progress.Progress
import dev.stefan.sokoban.core.progress.ProgressRules
import dev.stefan.sokoban.game.BoardEvent
import dev.stefan.sokoban.ui.board.GameBoard
import dev.stefan.sokoban.ui.board3d.GameBoard3D
import dev.stefan.sokoban.ui.components.GameIcon
import dev.stefan.sokoban.ui.components.GameIconView
import dev.stefan.sokoban.ui.components.PrimaryButton
import dev.stefan.sokoban.ui.components.SecondaryButton
import dev.stefan.sokoban.ui.theme.GameType
import dev.stefan.sokoban.ui.theme.palette
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlin.math.sin

@Composable
fun HomeScreen(
    progress: Progress,
    onPlay: (Int) -> Unit,
    onLevels: () -> Unit,
    onSettings: () -> Unit,
    board3d: Boolean = false,
) {
    val colors = palette
    val ids = remember { LevelPack.levels.map { it.id } }
    val next = ProgressRules.nextLevelToPlay(progress, ids)
    val nextEntry = LevelPack.entry(next)
    val stars = progress.bestMoves.entries.sumOf { (id, moves) ->
        LevelPack.levels.firstOrNull { it.id == id }?.let { ProgressRules.stars(moves, it.par) } ?: 0
    }

    Box(Modifier.fillMaxSize()) {
        Backdrop(Modifier.fillMaxSize())
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(0.7f))
            Title()
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.home_tagline).uppercase(), style = GameType.label, color = colors.textSecondary)
            Spacer(Modifier.height(22.dp))
            Vignette(Modifier.widthIn(max = 340.dp).fillMaxWidth().height(118.dp), board3d)
            Spacer(Modifier.weight(1f))

            val breathe = rememberInfiniteTransition(label = "play")
            val scale by breathe.animateFloat(1f, 1.035f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "scale")
            PrimaryButton(
                text = stringResource(R.string.play),
                onClick = { onPlay(next) },
                modifier = Modifier
                    .width(264.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    },
                icon = GameIcon.PLAY,
                height = 68.dp,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.continue_level, nextEntry.number, nextEntry.name),
                style = GameType.caption,
                color = colors.textSecondary,
            )
            Spacer(Modifier.height(26.dp))
            SecondaryButton(stringResource(R.string.levels), onLevels, Modifier.width(236.dp), icon = GameIcon.LEVELS)
            Spacer(Modifier.height(12.dp))
            SecondaryButton(stringResource(R.string.settings), onSettings, Modifier.width(236.dp), icon = GameIcon.SETTINGS)
            Spacer(Modifier.weight(0.6f))

            val solved = progress.completed.size
            val summary = stringResource(R.string.home_progress, solved, LevelPack.size)
            val starsText = stringResource(R.string.stars_total, stars, LevelPack.size * 3)
            Row(
                Modifier.clearAndSetSemantics { contentDescription = "$summary. $starsText" },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                GameIconView(GameIcon.STAR, colors.star, size = 16.dp)
                Spacer(Modifier.width(4.dp))
                Text("$stars / ${LevelPack.size * 3}", style = GameType.caption, color = colors.textSecondary)
                Text("  ·  ", style = GameType.caption, color = colors.textMuted)
                Text(summary, style = GameType.caption, color = colors.textSecondary)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** "SOKOBAN", its letters dropping in one after the other. */
@Composable
private fun Title() {
    val colors = palette
    val title = stringResource(R.string.app_name).uppercase()
    val letters = remember(title) { List(title.length) { Animatable(0f) } }
    LaunchedEffect(letters) {
        letters.forEachIndexed { index, letter ->
            launch {
                delay(index * 60L)
                letter.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 500f))
            }
        }
    }
    Row(Modifier.semantics { heading() }) {
        title.forEachIndexed { index, char ->
            Text(
                char.toString(),
                style = GameType.display,
                color = if (index % 2 == 0) colors.textPrimary else colors.accent,
                modifier = Modifier.graphicsLayer {
                    val t = letters[index].value
                    alpha = t.coerceIn(0f, 1f)
                    translationY = (1f - t) * -60f
                },
            )
        }
    }
}

/**
 * The hero pushing a crate home, forever: the real board, fed a scripted game.
 */
@Composable
private fun Vignette(modifier: Modifier, board3d: Boolean) {
    val level = remember { LevelParser.parse("########\n#@ \$  .#\n########") }
    var game by remember { mutableStateOf(GameState.start(level)) }
    val events = remember { MutableSharedFlow<BoardEvent>(extraBufferCapacity = 8) }
    LaunchedEffect(level) {
        while (true) {
            delay(1400)
            while (!game.isSolved) {
                val result = GameEngine.move(game, Direction.RIGHT)
                game = result.state
                when (result) {
                    is MoveResult.Walked -> events.emit(BoardEvent.Stepped(Direction.RIGHT, result.to))
                    is MoveResult.Pushed -> events.emit(BoardEvent.Pushed(result.boxIndex, Direction.RIGHT, result.to, result.enteredGoal))
                    is MoveResult.Blocked -> Unit
                }
                delay(if (result is MoveResult.Pushed) 380L else 300L)
            }
            events.emit(BoardEvent.Solved)
            delay(2600)
            game = GameState.start(level)
            events.emit(BoardEvent.Restarted)
        }
    }
    if (board3d) {
        GameBoard3D(
            level = level,
            game = game,
            stuckCrates = emptySet(),
            events = events,
            description = "",
            modifier = modifier.clearAndSetSemantics { },
        )
    } else {
        GameBoard(
            level = level,
            game = game,
            stuckCrates = emptySet(),
            events = events,
            description = "",
            modifier = modifier.clearAndSetSemantics { },
        )
    }
}

/** A faint tiled floor drifting diagonally, with a few floating motes. */
@Composable
private fun Backdrop(modifier: Modifier) {
    val colors = palette
    val drift = rememberInfiniteTransition(label = "backdrop")
    val shift = drift.animateFloat(0f, 1f, infiniteRepeatable(tween(DRIFT_MS, easing = LinearEasing)), label = "shift")
    Box(
        modifier.drawBehind {
            val cell = 56.dp.toPx()
            val offset = shift.value * cell * 2
            val tile = colors.textPrimary.copy(alpha = if (colors.isDark) 0.035f else 0.03f)
            var y = -cell * 2 + offset % (cell * 2)
            var row = 0
            while (y < size.height + cell) {
                var x = -cell * 2 + offset % (cell * 2) + if (row % 2 == 0) 0f else cell
                while (x < size.width + cell) {
                    drawRoundRect(tile, Offset(x + 6f, y + 6f), Size(cell - 12f, cell - 12f), CornerRadius(cell * 0.22f))
                    x += cell * 2
                }
                y += cell
                row++
            }
            val t = shift.value * 2f * Math.PI.toFloat()
            for (i in 0 until MOTES) {
                val seed = i * 97.13f
                val mx = ((seed * 13.7f) % 1f + sin(t + seed) * 0.03f).let { (it + 1f) % 1f } * size.width
                val my = (((seed * 7.3f) % 1f) - shift.value * (0.3f + i * 0.05f) + 1f) % 1f * size.height
                drawCircle(colors.accent.copy(alpha = 0.08f), (3 + i % 3 * 2).dp.toPx(), Offset(mx, my))
            }
        },
    )
}

private const val DRIFT_MS = 24_000
private const val MOTES = 9
