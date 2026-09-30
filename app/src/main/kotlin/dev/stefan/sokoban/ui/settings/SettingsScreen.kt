package dev.stefan.sokoban.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.stefan.sokoban.R
import dev.stefan.sokoban.data.Settings
import dev.stefan.sokoban.data.ThemeMode
import dev.stefan.sokoban.ui.components.CircleIconButton
import dev.stefan.sokoban.ui.components.GameIcon
import dev.stefan.sokoban.ui.components.GameIconView
import dev.stefan.sokoban.ui.components.bounceClick
import dev.stefan.sokoban.ui.theme.GameType
import dev.stefan.sokoban.ui.theme.palette

@Composable
fun SettingsScreen(
    settings: Settings,
    onBack: () -> Unit,
    onSound: (Boolean) -> Unit,
    onVibration: (Boolean) -> Unit,
    onTheme: (ThemeMode) -> Unit,
    onResetProgress: () -> Unit,
    onTap: () -> Unit,
) {
    val colors = palette
    var confirmReset by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleIconButton(GameIcon.BACK, stringResource(R.string.back), onBack)
            Text(
                stringResource(R.string.settings).uppercase(),
                style = GameType.title,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            Spacer(Modifier.size(48.dp))
        }

        Column(
            Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card {
                ToggleRow(GameIcon.SOUND, stringResource(R.string.sound), stringResource(R.string.sound_detail), settings.sound) {
                    onSound(it)
                    onTap()
                }
                Divider()
                ToggleRow(GameIcon.VIBRATION, stringResource(R.string.vibration), stringResource(R.string.vibration_detail), settings.vibration) {
                    onVibration(it)
                    onTap()
                }
                Divider()
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    RowIcon(GameIcon.THEME)
                    Text(stringResource(R.string.theme), style = GameType.heading, color = colors.textPrimary, modifier = Modifier.weight(1f))
                }
                ThemePicker(
                    selected = settings.theme,
                    onSelect = {
                        onTheme(it)
                        onTap()
                    },
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                )
            }

            Card {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .bounceClick(pressedScale = 0.98f) { confirmReset = true }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RowIcon(GameIcon.RESTART, tint = colors.danger)
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.reset_progress), style = GameType.heading, color = colors.danger)
                        Text(stringResource(R.string.reset_progress_detail), style = GameType.caption, color = colors.textSecondary)
                    }
                }
            }

            val version = versionName()
            Text(
                stringResource(R.string.about, version),
                style = GameType.caption,
                color = colors.textMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.reset_confirm_title), style = GameType.heading) },
            text = { Text(stringResource(R.string.reset_confirm_text), style = GameType.body) },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    onResetProgress()
                }) { Text(stringResource(R.string.reset_confirm).uppercase(), style = GameType.button.copy(fontSize = GameType.button.fontSize * 0.8f), color = colors.danger) }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) {
                    Text(stringResource(R.string.cancel).uppercase(), style = GameType.button.copy(fontSize = GameType.button.fontSize * 0.8f), color = colors.textSecondary)
                }
            },
            containerColor = colors.surfaceRaised,
            shape = RoundedCornerShape(28.dp),
        )
    }
}

@Composable
private fun versionName(): String {
    val context = LocalContext.current
    return remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    val colors = palette
    val shape = RoundedCornerShape(24.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(6.dp, shape, ambientColor = colors.shadow, spotColor = colors.shadow)
            .background(colors.surfaceRaised, shape)
            .border(1.dp, colors.outline, shape),
    ) { content() }
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp).background(palette.outline))
}

@Composable
private fun RowIcon(icon: GameIcon, tint: androidx.compose.ui.graphics.Color = palette.textPrimary) {
    Box(
        Modifier.padding(end = 14.dp).size(40.dp).background(palette.background, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        GameIconView(icon, tint, size = 20.dp)
    }
}

@Composable
private fun ToggleRow(icon: GameIcon, title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val colors = palette
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowIcon(icon)
        Column(Modifier.weight(1f)) {
            Text(title, style = GameType.heading, color = colors.textPrimary)
            Text(detail, style = GameType.caption, color = colors.textSecondary)
        }
        PillSwitch(checked)
    }
}

/** A switch drawn in the game's style; the row around it handles the input. */
@Composable
private fun PillSwitch(checked: Boolean) {
    val colors = palette
    val track by animateColorAsState(if (checked) colors.goal else colors.outline, label = "track")
    val thumbOffset by animateDpAsState(if (checked) 22.dp else 0.dp, spring(dampingRatio = 0.6f, stiffness = 700f), label = "thumb")
    Box(
        Modifier
            .width(52.dp)
            .height(30.dp)
            .background(track, RoundedCornerShape(50))
            .padding(3.dp),
    ) {
        Box(
            Modifier
                .offset(x = thumbOffset)
                .size(24.dp)
                .shadow(2.dp, CircleShape)
                .background(colors.surfaceRaised, CircleShape),
        )
    }
}

@Composable
private fun ThemePicker(selected: ThemeMode, onSelect: (ThemeMode) -> Unit, modifier: Modifier = Modifier) {
    val colors = palette
    val labels = mapOf(
        ThemeMode.SYSTEM to stringResource(R.string.theme_system),
        ThemeMode.LIGHT to stringResource(R.string.theme_light),
        ThemeMode.DARK to stringResource(R.string.theme_dark),
    )
    Row(
        modifier
            .fillMaxWidth()
            .background(colors.background, RoundedCornerShape(50))
            .padding(4.dp)
            .selectableGroup(),
    ) {
        ThemeMode.entries.forEach { mode ->
            val isSelected = mode == selected
            val background by animateColorAsState(if (isSelected) colors.accent else colors.background, label = "segment")
            val text by animateColorAsState(if (isSelected) colors.onAccent else colors.textSecondary, label = "segmentText")
            Box(
                Modifier
                    .weight(1f)
                    .height(40.dp)
                    .background(background, RoundedCornerShape(50))
                    .selectable(selected = isSelected, role = Role.RadioButton) { onSelect(mode) },
                contentAlignment = Alignment.Center,
            ) {
                Text(labels.getValue(mode).uppercase(), style = GameType.label.copy(fontSize = 12.sp), color = text)
            }
        }
    }
}
