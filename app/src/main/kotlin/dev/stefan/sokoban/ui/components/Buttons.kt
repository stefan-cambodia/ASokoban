package dev.stefan.sokoban.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.stefan.sokoban.ui.theme.GameType
import dev.stefan.sokoban.ui.theme.palette
import kotlinx.coroutines.flow.collectLatest

/**
 * Click with a tactile bounce: the element dips on press and springs back on
 * release. A tap too quick to see still gets its full dip before the rebound.
 */
fun Modifier.bounceClick(
    enabled: Boolean = true,
    pressedScale: Float = 0.94f,
    role: Role = Role.Button,
    onClick: () -> Unit,
): Modifier = composed {
    val interactions = remember { MutableInteractionSource() }
    val scale = remember { Animatable(1f) }
    LaunchedEffect(interactions) {
        interactions.interactions.collectLatest { interaction ->
            when (interaction) {
                is PressInteraction.Press -> scale.animateTo(pressedScale, tween(90))
                is PressInteraction.Release, is PressInteraction.Cancel -> {
                    if (scale.value > pressedScale + 0.005f) scale.animateTo(pressedScale, tween(50))
                    scale.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 700f))
                }
            }
        }
    }
    graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }.clickable(interactionSource = interactions, indication = null, enabled = enabled, role = role, onClick = onClick)
}

/** The dominant call to action: a warm, glossy pill. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: GameIcon? = null,
    height: Dp = 60.dp,
) {
    val colors = palette
    val shape = RoundedCornerShape(percent = 50)
    Box(
        modifier
            .height(height)
            .bounceClick(onClick = onClick)
            .shadow(10.dp, shape, ambientColor = colors.accentDeep, spotColor = colors.accentDeep)
            .background(Brush.verticalGradient(listOf(colors.accent, colors.accentDeep)), shape)
            .border(1.5.dp, Brush.verticalGradient(listOf(colors.onAccent.copy(alpha = 0.35f), colors.onAccent.copy(alpha = 0f))), shape)
            .padding(horizontal = 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (icon != null) {
                GameIconView(icon, colors.onAccent, size = 22.dp)
                Spacer(Modifier.width(10.dp))
            }
            Text(text.uppercase(), style = GameType.button, color = colors.onAccent)
        }
    }
}

/**
 * Secondary actions: a quiet raised pill. [compact] tightens it for side-by-side
 * use on narrow screens, where the label must never wrap.
 */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: GameIcon? = null,
    height: Dp = 54.dp,
    enabled: Boolean = true,
    compact: Boolean = false,
) {
    val colors = palette
    val shape = RoundedCornerShape(percent = 50)
    Box(
        modifier
            .height(height)
            .alpha(if (enabled) 1f else 0.4f)
            .bounceClick(enabled = enabled, onClick = onClick)
            .shadow(4.dp, shape, ambientColor = colors.shadow, spotColor = colors.shadow)
            .background(colors.surfaceRaised, shape)
            .border(1.dp, colors.outline, shape)
            .padding(horizontal = if (compact) 10.dp else 22.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                GameIconView(icon, colors.textPrimary, size = if (compact) 16.dp else 20.dp)
                Spacer(Modifier.width(if (compact) 6.dp else 10.dp))
            }
            Text(
                text.uppercase(),
                style = GameType.button.copy(
                    fontSize = GameType.button.fontSize * if (compact) 0.78f else 0.9f,
                    letterSpacing = if (compact) GameType.button.letterSpacing * 0.5f else GameType.button.letterSpacing,
                ),
                color = colors.textPrimary,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/** Round icon button for bars: back, restart. */
@Composable
fun CircleIconButton(
    icon: GameIcon,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
) {
    val colors = palette
    Box(
        modifier
            .size(size)
            .bounceClick(onClick = onClick)
            .semantics { this.contentDescription = contentDescription }
            .shadow(4.dp, CircleShape, ambientColor = colors.shadow, spotColor = colors.shadow)
            .background(colors.surfaceRaised, CircleShape)
            .border(1.dp, colors.outline, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        GameIconView(icon, colors.textPrimary, size = size * 0.46f)
    }
}

/** Three stars, [earned] of them lit. */
@Composable
fun StarRow(earned: Int, modifier: Modifier = Modifier, size: Dp = 14.dp, spacing: Dp = 2.dp) {
    val colors = palette
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(spacing)) {
        repeat(3) { index ->
            GameIconView(GameIcon.STAR, if (index < earned) colors.star else colors.starEmpty, size = size)
        }
    }
}

/** A small pill of information: label on top, value below. */
@Composable
fun StatPill(label: String, value: String, modifier: Modifier = Modifier) {
    val colors = palette
    Box(
        modifier
            .background(colors.surface.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label.uppercase(), style = GameType.label, color = colors.textMuted)
            Text(value, style = GameType.heading, color = colors.textPrimary)
        }
    }
}
