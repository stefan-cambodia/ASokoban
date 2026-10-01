package dev.stefan.sokoban.ui.levels

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.stefan.sokoban.R
import dev.stefan.sokoban.core.levels.LevelEntry
import dev.stefan.sokoban.core.levels.LevelPack
import dev.stefan.sokoban.core.levels.World
import dev.stefan.sokoban.core.progress.Progress
import dev.stefan.sokoban.core.progress.ProgressRules
import dev.stefan.sokoban.feedback.Feedback
import dev.stefan.sokoban.ui.board.easeOutBack
import dev.stefan.sokoban.ui.components.CircleIconButton
import dev.stefan.sokoban.ui.components.GameIcon
import dev.stefan.sokoban.ui.components.GameIconView
import dev.stefan.sokoban.ui.components.StarRow
import dev.stefan.sokoban.ui.components.bounceClick
import dev.stefan.sokoban.ui.theme.GameType
import dev.stefan.sokoban.ui.theme.palette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

private enum class CardState { LOCKED, UNLOCKING, OPEN, SOLVED }

@Composable
fun LevelSelectScreen(
    progress: Progress,
    seenUnlocks: Set<String>,
    shownSolved: Set<String>,
    feedback: Feedback,
    onBack: () -> Unit,
    onPlay: (Int) -> Unit,
    onUnlocksSeen: (Set<String>) -> Unit,
    onSolvedShown: (Set<String>) -> Unit,
) {
    val colors = palette
    val ids = remember { LevelPack.levels.map { it.id } }
    val next = ProgressRules.nextLevelToPlay(progress, ids)
    // Levels opened since the player last looked: they get their unlock moment.
    val fresh = remember(progress, seenUnlocks) {
        LevelPack.levels
            .filter { it.index > 0 && it.id !in seenUnlocks && it.id !in progress.completed }
            .filter { ProgressRules.isUnlocked(progress, ids, it.index) }
            .map { it.id }
            .toSet()
    }
    LaunchedEffect(fresh) {
        if (fresh.isEmpty()) return@LaunchedEffect
        delay(UNLOCK_START_MS)
        feedback.unlock()
        delay(UNLOCK_SETTLE_MS)
        onUnlocksSeen(fresh)
    }
    // Levels solved since the list was last open: their stars pop in.
    val justSolved = remember(progress, shownSolved) { progress.completed - shownSolved }
    LaunchedEffect(justSolved) {
        if (justSolved.isEmpty()) return@LaunchedEffect
        delay(SOLVED_SETTLE_MS)
        onSolvedShown(justSolved)
    }
    val totalStars = progress.bestMoves.entries.sumOf { (id, moves) ->
        LevelPack.levels.firstOrNull { it.id == id }?.let { ProgressRules.stars(moves, it.par) } ?: 0
    }

    // The list runs on under the navigation bar; its last row stops above it.
    val insets = WindowInsets.safeDrawing
    val bottomInset = insets.asPaddingValues().calculateBottomPadding()
    BoxWithConstraints(
        Modifier.fillMaxSize().windowInsetsPadding(insets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
    ) {
        val columns = if (maxWidth >= 600.dp) 10 else 5
        val grid = rememberLazyGridState()
        // Open on the world of the level to play next.
        LaunchedEffect(Unit) {
            val world = LevelPack.entry(next).world
            val headerIndex = LevelPack.worlds.indexOf(world).let { w -> w + LevelPack.worlds.take(w).sumOf { it.levels.size } }
            if (headerIndex > 0) grid.scrollToItem(headerIndex)
        }

        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircleIconButton(GameIcon.BACK, stringResource(R.string.back), onBack)
                Text(
                    stringResource(R.string.levels).uppercase(),
                    style = GameType.title,
                    color = colors.textPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                val starsLabel = stringResource(R.string.stars_total, totalStars, LevelPack.size * 3)
                Row(
                    Modifier
                        .clearAndSetSemantics { contentDescription = starsLabel }
                        .background(colors.surfaceRaised, RoundedCornerShape(50))
                        .border(1.dp, colors.outline, RoundedCornerShape(50))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GameIconView(GameIcon.STAR, colors.star, size = 16.dp)
                    Spacer(Modifier.width(4.dp))
                    Text("$totalStars", style = GameType.heading.copy(fontSize = 15.sp), color = colors.textPrimary)
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                state = grid,
                modifier = Modifier.widthIn(max = 720.dp).fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp + bottomInset),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                for (world in LevelPack.worlds) {
                    item(key = "world-${world.number}", span = { GridItemSpan(maxLineSpan) }) {
                        WorldHeader(world, progress)
                    }
                    val entries = LevelPack.levels.filter { it.world == world }
                    items(entries.size, key = { entries[it].id }) { i ->
                        val entry = entries[i]
                        val state = when {
                            entry.id in progress.completed -> CardState.SOLVED
                            entry.id in fresh -> CardState.UNLOCKING
                            ProgressRules.isUnlocked(progress, ids, entry.index) -> CardState.OPEN
                            else -> CardState.LOCKED
                        }
                        LevelCard(
                            entry = entry,
                            state = state,
                            best = progress.bestMoves[entry.id],
                            isNext = entry.index == next && state != CardState.SOLVED,
                            celebrate = entry.id in justSolved,
                            onOpen = { onPlay(entry.index) },
                            onLocked = feedback::bump,
                        )
                    }
                }
            }
        }
        // Under the navigation bar the cards fade into the background.
        if (bottomInset > 0.dp) {
            val fade = colors.backgroundDeep
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(bottomInset + 24.dp)
                    .background(Brush.verticalGradient(0f to fade.copy(alpha = 0f), 0.6f to fade.copy(alpha = 0.9f), 1f to fade)),
            )
        }
    }
}

@Composable
private fun WorldHeader(world: World, progress: Progress) {
    val colors = palette
    val solved = world.levels.count { it.id in progress.completed }
    Column(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 4.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.world_label, world.number).uppercase(), style = GameType.label, color = colors.accent)
                Text(world.title, style = GameType.heading.copy(fontSize = 22.sp), color = colors.textPrimary, modifier = Modifier.semantics { heading() })
                Text(world.subtitle, style = GameType.caption, color = colors.textSecondary)
            }
            Text(
                stringResource(R.string.world_progress, solved, world.levels.size),
                style = GameType.heading,
                color = if (solved == world.levels.size) colors.goal else colors.textSecondary,
            )
        }
        Spacer(Modifier.height(8.dp))
        val fraction = solved / world.levels.size.toFloat()
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(colors.outline, RoundedCornerShape(50))
                .drawBehind {
                    if (fraction > 0f) {
                        drawRoundRect(
                            colors.goal,
                            size = size.copy(width = size.width * fraction),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2),
                        )
                    }
                },
        )
    }
}

@Composable
private fun LevelCard(
    entry: LevelEntry,
    state: CardState,
    best: Int?,
    isNext: Boolean,
    celebrate: Boolean,
    onOpen: () -> Unit,
    onLocked: () -> Unit,
) {
    val colors = palette
    val scope = rememberCoroutineScope()
    val shake = remember { Animatable(0f) }
    // Unlock choreography: the lock trembles, bursts, and the number pops in.
    val unlock = remember { Animatable(if (state == CardState.UNLOCKING) 0f else 1f) }
    LaunchedEffect(state) {
        if (state == CardState.UNLOCKING && unlock.value < 1f) {
            delay(UNLOCK_START_MS)
            unlock.animateTo(1f, tween(UNLOCK_ANIMATION_MS))
        }
    }
    // Freshly solved: the card hops and its stars land one after the other.
    val solved = remember { Animatable(if (celebrate) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (solved.value < 1f) {
            delay(SOLVED_START_MS)
            solved.animateTo(1f, tween(SOLVED_ANIMATION_MS, easing = LinearEasing))
        }
    }
    val pulse = if (isNext) {
        val transition = rememberInfiniteTransition(label = "next")
        transition.animateFloat(0f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulse").value
    } else {
        0f
    }

    val stars = best?.let { ProgressRules.stars(it, entry.par) } ?: 0
    val description = when (state) {
        CardState.LOCKED -> stringResource(R.string.level_locked, entry.number)
        CardState.SOLVED -> stringResource(
            R.string.level_solved,
            entry.number,
            entry.name,
            pluralStringResource(R.plurals.moves_count, best ?: 0, best ?: 0),
            pluralStringResource(R.plurals.stars_count, stars, stars),
        )
        else -> stringResource(R.string.level_open, entry.number, entry.name)
    }
    val locked = state == CardState.LOCKED
    val background = when (state) {
        CardState.LOCKED -> colors.surface.copy(alpha = 0.55f)
        CardState.SOLVED -> colors.crateDone.copy(alpha = 0.13f).compositeOver(colors.surfaceRaised)
        else -> colors.surfaceRaised
    }
    val shape = RoundedCornerShape(18.dp)

    Box(
        Modifier
            .aspectRatio(0.8f)
            .clearAndSetSemantics { contentDescription = description }
            .graphicsLayer {
                translationX = shake.value
                val hop = 1f + 0.1f * sin((solved.value / 0.4f).coerceIn(0f, 1f) * PI.toFloat())
                scaleX = hop
                scaleY = hop
            }
            .bounceClick(pressedScale = if (locked) 0.97f else 0.92f) {
                if (locked) {
                    onLocked()
                    scope.launch {
                        shake.animateTo(0f, keyframes {
                            durationMillis = 280
                            -10f at 40
                            9f at 100
                            -6f at 160
                            4f at 220
                        })
                    }
                } else {
                    onOpen()
                }
            }
            .background(background, shape)
            .border(
                width = if (isNext) 2.dp else 1.dp,
                color = if (isNext) colors.accent.copy(alpha = 0.55f + 0.45f * pulse) else colors.outline.copy(alpha = if (locked) 0.5f else 1f),
                shape = shape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        val progress = unlock.value
        // The lock is visible while locked and during the first half of the unlock.
        val lockAlpha = if (locked) 1f else if (state == CardState.UNLOCKING) (1f - (progress - 0.45f) / 0.25f).coerceIn(0f, 1f) else 0f
        if (lockAlpha > 0f) {
            val tremble = if (state == CardState.UNLOCKING && progress < 0.45f) sin(progress * 60f) * 14f else 0f
            val burst = 1f + ((progress - 0.45f) / 0.25f).coerceIn(0f, 1f) * 0.6f
            GameIconView(
                GameIcon.LOCK,
                colors.textMuted,
                Modifier.graphicsLayer {
                    alpha = lockAlpha
                    rotationZ = tremble
                    scaleX = burst
                    scaleY = burst
                },
                size = 22.dp,
            )
        }
        if (state == CardState.UNLOCKING && progress in 0.45f..0.95f) {
            val ring = (progress - 0.45f) / 0.5f
            Box(
                Modifier
                    .fillMaxSize()
                    .drawBehind {
                        drawCircle(
                            colors.star.copy(alpha = 0.8f * (1f - ring)),
                            radius = size.minDimension * (0.2f + 0.45f * ring),
                            center = Offset(size.width / 2, size.height / 2),
                            style = Stroke(width = 3.dp.toPx() * (1f - ring) + 1f),
                        )
                    },
            )
        }
        if (!locked) {
            val numberScale = if (state == CardState.UNLOCKING) {
                ((progress - 0.55f) / 0.45f).coerceIn(0f, 1f).let { t -> if (t == 0f) 0f else 0.6f + 0.4f * t + sin(t * PI.toFloat()) * 0.25f }
            } else {
                1f
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.graphicsLayer {
                    scaleX = numberScale
                    scaleY = numberScale
                },
            ) {
                Text(
                    entry.number.toString(),
                    style = GameType.heading.copy(fontSize = 21.sp),
                    color = colors.textPrimary,
                )
                if (state == CardState.SOLVED) {
                    StarRow(stars, size = 10.dp, spacing = 1.dp, reveal = { index ->
                        easeOutBack(((solved.value - 0.2f - index * 0.2f) / 0.4f).coerceIn(0f, 1f))
                    })
                    Text(best?.toString().orEmpty(), style = GameType.caption.copy(fontSize = 10.sp), color = colors.textSecondary)
                } else {
                    Spacer(Modifier.height(4.dp))
                    Box(Modifier.width(14.dp).height(3.dp).background(if (isNext) colors.accent else colors.outline, RoundedCornerShape(50)))
                }
            }
        }
    }
}

private const val SOLVED_START_MS = 300L
private const val SOLVED_ANIMATION_MS = 750
private const val SOLVED_SETTLE_MS = 1300L
private const val UNLOCK_START_MS = 450L
private const val UNLOCK_ANIMATION_MS = 1100
private const val UNLOCK_SETTLE_MS = 1300L
