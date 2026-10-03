package dev.stefan.sokoban.ui.game

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.stefan.sokoban.R
import dev.stefan.sokoban.core.Direction
import dev.stefan.sokoban.core.Position
import dev.stefan.sokoban.core.levels.LevelEntry
import dev.stefan.sokoban.feedback.Feedback
import dev.stefan.sokoban.game.GamePhase
import dev.stefan.sokoban.game.GameUiState
import dev.stefan.sokoban.game.GameViewModel
import dev.stefan.sokoban.ui.board.GameBoard
import dev.stefan.sokoban.ui.board3d.GameBoard3D
import dev.stefan.sokoban.ui.components.CircleIconButton
import dev.stefan.sokoban.ui.components.GameIcon
import dev.stefan.sokoban.ui.components.GameIconView
import dev.stefan.sokoban.ui.components.StatPill
import dev.stefan.sokoban.ui.theme.GameType
import dev.stefan.sokoban.ui.theme.palette
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun GameScreen(
    levelIndex: Int,
    viewModel: GameViewModel,
    feedback: Feedback,
    board3d: Boolean,
    onBack: () -> Unit,
    onOpenLevel: (Int) -> Unit,
) {
    // Navigation opens the level; this covers a screen restored without it.
    LaunchedEffect(levelIndex) { viewModel.open(levelIndex) }
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)
    val state = uiState?.takeIf { it.entry.index == levelIndex } ?: return

    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    val onTapCell: (Position) -> Unit = { cell ->
        val player = state.game.player
        val dx = cell.x - player.x
        val dy = cell.y - player.y
        when {
            // Tapping a neighbour steps (or pushes) that way; further away, walk there.
            abs(dx) + abs(dy) == 1 -> viewModel.move(Direction.entries.first { it.dx == dx && it.dy == dy })
            state.game.isFree(cell) -> viewModel.walkTo(cell)
        }
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val result = (state.phase as? GamePhase.Complete)?.result
                if (result != null) {
                    // On the result card: Enter continues, R replays.
                    if (event.nativeKeyEvent.repeatCount > 0) return@onPreviewKeyEvent true
                    when (event.key) {
                        Key.Enter, Key.NumPadEnter, Key.N, Key.Spacebar -> result.nextIndex?.let(onOpenLevel) ?: onBack()
                        Key.R -> viewModel.restart()
                        else -> return@onPreviewKeyEvent false
                    }
                    return@onPreviewKeyEvent true
                }
                // A key kept down repeats far faster than the hero walks.
                val held = event.nativeKeyEvent.repeatCount > 0
                when (event.key) {
                    Key.DirectionUp, Key.W -> viewModel.move(Direction.UP, held)
                    Key.DirectionDown, Key.S -> viewModel.move(Direction.DOWN, held)
                    Key.DirectionLeft, Key.A -> viewModel.move(Direction.LEFT, held)
                    Key.DirectionRight, Key.D -> viewModel.move(Direction.RIGHT, held)
                    Key.Z, Key.U, Key.Backspace -> viewModel.undo(held)
                    Key.R -> if (!held) viewModel.restart()
                    else -> return@onPreviewKeyEvent false
                }
                true
            },
    ) {
        val landscape = maxWidth > maxHeight * 1.1f
        val bottomGap = if (maxHeight >= 700.dp) 16.dp else 8.dp
        val padSize = when {
            landscape -> minOf(maxHeight * 0.5f, 220.dp)
            maxHeight >= 820.dp -> 188.dp
            maxHeight >= 700.dp -> 168.dp
            else -> 142.dp
        }
        val game = state.game
        val level = state.entry.level
        val description = stringResource(
            R.string.board_description,
            state.entry.number, level.width, level.height, game.boxesOnGoals, game.boxes.size,
            game.player.x + 1, game.player.y + 1,
        ) + if (state.stuckCrates.isNotEmpty()) " " + stringResource(R.string.board_state_stuck) else ""
        val board: @Composable (Modifier) -> Unit = { modifier ->
            if (board3d) {
                GameBoard3D(
                    level = level,
                    game = game,
                    stuckCrates = state.stuckCrates,
                    events = viewModel.events,
                    description = description,
                    modifier = modifier,
                    onMove = { viewModel.move(it) },
                    onTapCell = onTapCell,
                )
            } else {
                GameBoard(
                    level = level,
                    game = game,
                    stuckCrates = state.stuckCrates,
                    events = viewModel.events,
                    description = description,
                    modifier = modifier,
                    onMove = { viewModel.move(it) },
                    onTapCell = onTapCell,
                )
            }
        }
        val controls: @Composable () -> Unit = {
            Controls(
                padSize = padSize,
                canUndo = state.game.canUndo && state.phase == GamePhase.Playing,
                highlightUndo = state.stuckCrates.isNotEmpty(),
                onMove = viewModel::move,
                onUndo = viewModel::undo,
            )
        }

        val complete = state.phase as? GamePhase.Complete
        Box(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                // Under the result card the game is scenery: screen readers skip it.
                .then(if (complete != null) Modifier.clearAndSetSemantics { } else Modifier),
        ) {
            if (landscape) {
                Row(Modifier.fillMaxSize()) {
                    Column(Modifier.weight(1f).fillMaxSize()) {
                        TopBar(state.entry, onBack, viewModel::restart)
                        board(Modifier.weight(1f).fillMaxWidth().padding(12.dp))
                    }
                    Column(
                        Modifier.width(padSize + 96.dp).fillMaxSize().padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Stats(state)
                        MessageSlot(state)
                        Spacer(Modifier.height(12.dp))
                        controls()
                    }
                }
            } else {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    TopBar(state.entry, onBack, viewModel::restart)
                    Stats(state)
                    MessageSlot(state)
                    board(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp))
                    Spacer(Modifier.height(8.dp))
                    controls()
                    Spacer(Modifier.height(bottomGap))
                }
            }
        }

        ConfettiLayer(
            trigger = state.takeIf { it.phase != GamePhase.Playing }?.game,
            modifier = Modifier.fillMaxSize(),
        )

        AnimatedVisibility(visible = complete != null, enter = fadeIn(tween(250)), exit = fadeOut(tween(200))) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(palette.background.copy(alpha = 0.72f))
                    // Swallow touches so the board underneath stays still.
                    .pointerInput(Unit) { awaitEachGesture { awaitFirstDown().consume() } },
            )
        }
        AnimatedVisibility(
            visible = complete != null,
            modifier = Modifier.align(Alignment.Center),
            enter = fadeIn(tween(200)) +
                scaleIn(spring(dampingRatio = 0.6f, stiffness = 380f), initialScale = 0.85f) +
                slideInVertically(spring(dampingRatio = 0.75f, stiffness = 380f)) { it / 5 },
            exit = fadeOut(tween(150)),
        ) {
            val result = complete?.result ?: return@AnimatedVisibility
            ResultCard(
                result = result,
                feedback = feedback,
                onNext = onOpenLevel,
                onReplay = viewModel::restart,
                onLevels = onBack,
                modifier = Modifier.padding(20.dp).windowInsetsPadding(WindowInsets.safeDrawing),
            )
        }
    }
}

@Composable
private fun TopBar(entry: LevelEntry, onBack: () -> Unit, onRestart: () -> Unit) {
    val colors = palette
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleIconButton(GameIcon.BACK, stringResource(R.string.back), onBack)
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                stringResource(R.string.level_number, entry.number).uppercase(),
                style = GameType.title,
                color = colors.textPrimary,
            )
            Text(
                "${entry.world.title} · ${entry.name}",
                style = GameType.caption,
                color = colors.textSecondary,
                maxLines = 1,
            )
        }
        CircleIconButton(GameIcon.RESTART, stringResource(R.string.restart), onRestart)
    }
}

@Composable
private fun Stats(state: GameUiState) {
    val colors = palette
    val game = state.game
    Row(
        // On a wide screen the three figures stay together instead of drifting to the edges.
        Modifier.widthIn(max = 460.dp).fillMaxWidth().padding(horizontal = 20.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatPill(stringResource(R.string.crates), "${game.boxesOnGoals}/${game.boxes.size}", Modifier.width(92.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.moves).uppercase(), style = GameType.label, color = colors.textMuted)
            AnimatedContent(
                targetState = game.moves,
                transitionSpec = {
                    val up = targetState > initialState
                    (slideInVertically(tween(140)) { if (up) it / 2 else -it / 2 } + fadeIn(tween(140))) togetherWith
                        (slideOutVertically(tween(140)) { if (up) -it / 2 else it / 2 } + fadeOut(tween(100)))
                },
                label = "moves",
            ) { moves ->
                Text(moves.toString(), style = GameType.number, color = colors.textPrimary, textAlign = TextAlign.Center)
            }
        }
        val best = state.best
        StatPill(
            stringResource(if (best != null) R.string.best else R.string.par),
            (best ?: state.entry.par).toString(),
            Modifier.width(92.dp),
        )
    }
}

/** One line under the stats: the stuck warning, or the tutorial hint. */
@Composable
private fun MessageSlot(state: GameUiState) {
    val colors = palette
    val stuck = state.stuckCrates.isNotEmpty() && state.phase == GamePhase.Playing
    val message = when {
        stuck -> stringResource(R.string.stuck_hint)
        else -> state.entry.hint
    }
    Box(Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
        AnimatedContent(
            targetState = message to stuck,
            transitionSpec = { (fadeIn(tween(220)) + scaleIn(initialScale = 0.92f)) togetherWith fadeOut(tween(150)) },
            label = "message",
        ) { (text, isWarning) ->
            if (text == null) return@AnimatedContent
            Text(
                text,
                style = GameType.caption,
                color = if (isWarning) colors.danger else colors.textSecondary,
                textAlign = TextAlign.Center,
                maxLines = 2,
                modifier = Modifier
                    .background(
                        if (isWarning) colors.danger.copy(alpha = 0.12f) else colors.surface.copy(alpha = 0.8f),
                        RoundedCornerShape(50),
                    )
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun Controls(
    padSize: Dp,
    canUndo: Boolean,
    highlightUndo: Boolean,
    onMove: (direction: Direction, held: Boolean) -> Unit,
    onUndo: (held: Boolean) -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        DPad(padSize, onMove)
        Spacer(Modifier.height(10.dp))
        UndoButton(enabled = canUndo, highlight = highlightUndo, onUndo = onUndo)
    }
}

/** Undo: tap to step back, hold to rewind. Pulses when a crate is stuck. */
@Composable
private fun UndoButton(enabled: Boolean, highlight: Boolean, onUndo: (held: Boolean) -> Unit) {
    val colors = palette
    val scope = rememberCoroutineScope()
    val undo by rememberUpdatedState(onUndo)
    val isEnabled by rememberUpdatedState(enabled)
    var pressed by remember { mutableStateOf(false) }
    val transition = rememberInfiniteTransition(label = "undo")
    val beat by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "beat")
    val glow = if (highlight && enabled) beat else 0f
    val label = stringResource(R.string.undo)
    val shape = RoundedCornerShape(50)
    Row(
        Modifier
            .height(50.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .semantics {
                contentDescription = label
                role = Role.Button
                if (!enabled) disabled()
                onClick(label) {
                    undo(false)
                    true
                }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    if (!isEnabled) return@awaitEachGesture
                    pressed = true
                    undo(false)
                    // The rewind ends by itself at the first move, and with the
                    // gesture however that one ends: it must never outlive the hold.
                    val rewind: Job = scope.launch {
                        delay(UNDO_HOLD_MS)
                        while (isEnabled) {
                            undo(true)
                            delay(UNDO_REPEAT_MS)
                        }
                    }
                    try {
                        waitForUpOrCancellation()
                    } finally {
                        rewind.cancel()
                        pressed = false
                    }
                }
            }
            .graphicsLayer {
                val scale = (if (pressed) 0.93f else 1f) + glow * 0.05f
                scaleX = scale
                scaleY = scale
            }
            // Disabled, the button lies flat: a faded layer would cut its shadow square.
            .shadow(if (!enabled) 0.dp else if (pressed) 1.dp else 5.dp, shape, ambientColor = colors.shadow, spotColor = colors.shadow)
            .background(lerp(colors.surfaceRaised, colors.accent.copy(alpha = 0.18f).compositeOver(colors.surfaceRaised), glow), shape)
            .border(1.5.dp, lerp(colors.outline, colors.accent, glow), shape)
            .padding(horizontal = 26.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GameIconView(GameIcon.UNDO, lerp(colors.textPrimary, colors.accentDeep, glow), size = 20.dp)
        Spacer(Modifier.size(8.dp))
        Text(label.uppercase(), style = GameType.button.copy(fontSize = GameType.button.fontSize * 0.85f), color = lerp(colors.textPrimary, colors.accentDeep, glow))
    }
}

private const val UNDO_HOLD_MS = 380L
private const val UNDO_REPEAT_MS = 90L
