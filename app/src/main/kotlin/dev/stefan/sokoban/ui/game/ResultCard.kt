package dev.stefan.sokoban.ui.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.stefan.sokoban.R
import dev.stefan.sokoban.feedback.Feedback
import dev.stefan.sokoban.game.LevelResult
import dev.stefan.sokoban.ui.board.Particles
import dev.stefan.sokoban.ui.components.GameIcon
import dev.stefan.sokoban.ui.components.GameIconView
import dev.stefan.sokoban.ui.components.PrimaryButton
import dev.stefan.sokoban.ui.components.SecondaryButton
import dev.stefan.sokoban.ui.theme.GameType
import dev.stefan.sokoban.ui.theme.palette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Full-screen confetti, released each time [trigger] becomes non-null or changes. */
@Composable
fun ConfettiLayer(trigger: Any?, modifier: Modifier = Modifier) {
    val colors = palette
    val particles = remember { Particles(capacity = 180, gravity = 520f, drag = 0.8f) }
    val density = LocalDensity.current
    BoxWithConstraints(modifier) {
        val width = constraints.maxWidth.toFloat()
        LaunchedEffect(particles) { particles.run() }
        LaunchedEffect(trigger) {
            if (trigger != null) {
                val confettiColors = listOf(colors.accent, colors.star, colors.goal, colors.crate, colors.wallHighlight)
                particles.rain(width, 150, with(density) { 420.dp.toPx() }, with(density) { 11.dp.toPx() }, confettiColors, 3.4f)
            }
        }
        Spacer(Modifier.fillMaxSize().drawBehind { particles.draw(this, Offset.Zero, 1f, confetti = true) })
    }
}

@Composable
fun ResultCard(
    result: LevelResult,
    feedback: Feedback,
    onNext: (Int) -> Unit,
    onReplay: () -> Unit,
    onLevels: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = palette
    val stars = remember { List(3) { Animatable(0f) } }
    val count = remember { Animatable(0f) }
    LaunchedEffect(result) {
        launch { count.animateTo(result.moves.toFloat(), tween(COUNT_MS, easing = FastOutSlowInEasing)) }
        delay(STAR_START_MS)
        stars.forEachIndexed { index, star ->
            launch { star.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = 420f)) }
            if (index < result.stars) feedback.star(index)
            delay(STAR_STAGGER_MS)
        }
    }

    val shape = RoundedCornerShape(32.dp)
    Column(
        modifier
            .widthIn(max = 380.dp)
            .fillMaxWidth()
            .shadow(24.dp, shape, ambientColor = colors.shadow, spotColor = colors.shadow)
            .background(colors.surfaceRaised, shape)
            .border(1.dp, colors.outline, shape)
            .padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(if (result.nextIndex == null) R.string.all_complete else R.string.level_complete).uppercase(),
            style = GameType.title,
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(18.dp))

        val starsLabel = stringResource(R.string.stars_description, result.stars)
        Row(
            Modifier.semantics { contentDescription = starsLabel },
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            stars.forEachIndexed { index, star ->
                val lift = if (index == 1) 10.dp else 0.dp
                GameIconView(
                    GameIcon.STAR,
                    if (index < result.stars) colors.star else colors.starEmpty,
                    Modifier
                        .padding(bottom = lift)
                        .graphicsLayer {
                            scaleX = star.value
                            scaleY = star.value
                            rotationZ = (1f - star.value) * -40f
                        },
                    size = if (index == 1) 58.dp else 48.dp,
                )
            }
        }
        Spacer(Modifier.height(14.dp))

        Text(
            count.value.toInt().toString(),
            style = GameType.number.copy(fontSize = 48.sp),
            color = colors.textPrimary,
        )
        Text(stringResource(R.string.moves).uppercase(), style = GameType.label, color = colors.textMuted)
        Spacer(Modifier.height(14.dp))

        if (result.isNewBest) {
            val glow = rememberInfiniteTransition(label = "best")
            val pulse by glow.animateFloat(1f, 1.07f, infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "pulse")
            Box(
                Modifier
                    .graphicsLayer {
                        scaleX = pulse
                        scaleY = pulse
                    }
                    .background(colors.accent, RoundedCornerShape(50))
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            ) {
                Text(stringResource(R.string.new_best).uppercase(), style = GameType.label.copy(fontSize = 13.sp), color = colors.onAccent)
            }
        } else if (result.previousBest != null) {
            Text(stringResource(R.string.best_result, result.previousBest).uppercase(), style = GameType.label.copy(fontSize = 13.sp), color = colors.textSecondary)
        }
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.par_result, result.par), style = GameType.caption, color = colors.textMuted)

        if (result.unlockedNumbers.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                result.unlockedNumbers.forEach { number ->
                    Row(
                        Modifier
                            .background(colors.goal.copy(alpha = 0.14f), RoundedCornerShape(50))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        GameIconView(GameIcon.CHECK, colors.goal, size = 14.dp)
                        Spacer(Modifier.padding(start = 4.dp))
                        Text(stringResource(R.string.level_unlocked, number), style = GameType.caption, color = colors.goal, maxLines = 1, softWrap = false)
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        val next = result.nextIndex
        if (next != null) {
            PrimaryButton(stringResource(R.string.next_level), { onNext(next) }, Modifier.fillMaxWidth(), icon = GameIcon.NEXT)
        } else {
            PrimaryButton(stringResource(R.string.levels), onLevels, Modifier.fillMaxWidth(), icon = GameIcon.LEVELS)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SecondaryButton(stringResource(R.string.replay), onReplay, Modifier.weight(1f), icon = GameIcon.RESTART, compact = true)
            if (next != null) {
                SecondaryButton(stringResource(R.string.levels), onLevels, Modifier.weight(1f), icon = GameIcon.LEVELS, compact = true)
            }
        }
    }
}

private const val COUNT_MS = 650
private const val STAR_START_MS = 280L
private const val STAR_STAGGER_MS = 170L
