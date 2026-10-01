package dev.stefan.sokoban.ui

import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.stefan.sokoban.app.AppViewModel
import dev.stefan.sokoban.core.levels.LevelPack
import dev.stefan.sokoban.data.ThemeMode
import dev.stefan.sokoban.game.GameViewModel
import dev.stefan.sokoban.ui.game.GameScreen
import dev.stefan.sokoban.ui.home.HomeScreen
import dev.stefan.sokoban.ui.levels.LevelSelectScreen
import dev.stefan.sokoban.ui.settings.SettingsScreen
import dev.stefan.sokoban.ui.theme.SokobanTheme
import dev.stefan.sokoban.ui.theme.palette

sealed interface Screen {
    data object Home : Screen
    data object Levels : Screen
    data object Settings : Screen
    data class Game(val index: Int) : Screen
}

/** The back stack survives rotation and process death as plain strings. */
private val StackSaver = Saver<List<Screen>, ArrayList<String>>(
    save = { stack ->
        ArrayList(
            stack.map { screen ->
                when (screen) {
                    Screen.Home -> "home"
                    Screen.Levels -> "levels"
                    Screen.Settings -> "settings"
                    is Screen.Game -> "game:${screen.index}"
                }
            },
        )
    },
    restore = { saved ->
        saved.mapNotNull { token ->
            when {
                token == "home" -> Screen.Home
                token == "levels" -> Screen.Levels
                token == "settings" -> Screen.Settings
                token.startsWith("game:") -> token.removePrefix("game:").toIntOrNull()
                    ?.takeIf { it in LevelPack.levels.indices }
                    ?.let { Screen.Game(it) }
                else -> null
            }
        }.ifEmpty { listOf(Screen.Home) }
    },
)

@Composable
fun SokobanApp() {
    val app: AppViewModel = viewModel(factory = AppViewModel.Factory)
    val game: GameViewModel = viewModel(factory = GameViewModel.Factory)
    val settings by app.settings.collectAsStateWithLifecycle()
    val progress by app.progress.collectAsStateWithLifecycle()
    val seenUnlocks by app.seenUnlocks.collectAsStateWithLifecycle()
    val shownSolved by app.shownSolved.collectAsStateWithLifecycle()

    // Until preferences are read the window background stays: no theme flash.
    val current = settings ?: return
    val dark = when (current.theme) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val activity = LocalActivity.current as? ComponentActivity
    DisposableEffect(activity, dark) {
        // An explicit style, not `auto`: that one lets the system lay a contrast
        // scrim behind a three-button navigation bar, a pale band across the game.
        val transparent = android.graphics.Color.TRANSPARENT
        val style = if (dark) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent)
        activity?.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        onDispose { }
    }

    SokobanTheme(dark = dark) {
        var stack by rememberSaveable(stateSaver = StackSaver) { mutableStateOf(listOf<Screen>(Screen.Home)) }
        var forward by remember { mutableStateOf(true) }
        fun push(screen: Screen) {
            app.feedback.tap()
            forward = true
            stack = stack + screen
        }
        fun pop() {
            if (stack.size <= 1) return
            forward = false
            stack = stack.dropLast(1)
        }
        fun replaceTop(screen: Screen) {
            forward = true
            stack = stack.dropLast(1) + screen
        }
        // The level is loaded before its screen appears, so the first frame
        // already shows it (and never the end of the previous game).
        fun play(index: Int) {
            game.open(index)
            push(Screen.Game(index))
        }

        BackHandler(enabled = stack.size > 1) { pop() }

        val colors = palette
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(colors.background, colors.backgroundDeep))),
        ) {
            AnimatedContent(
                targetState = stack.last(),
                transitionSpec = { transitionFor(initialState, targetState, forward) },
                label = "screens",
            ) { screen ->
                val loadedProgress = progress
                when (screen) {
                    Screen.Home -> if (loadedProgress != null) {
                        HomeScreen(
                            progress = loadedProgress,
                            onPlay = ::play,
                            onLevels = { push(Screen.Levels) },
                            onSettings = { push(Screen.Settings) },
                        )
                    }
                    Screen.Levels -> {
                        val seen = seenUnlocks
                        val shown = shownSolved
                        if (loadedProgress != null && seen != null && shown != null) {
                            LevelSelectScreen(
                                progress = loadedProgress,
                                seenUnlocks = seen,
                                shownSolved = shown,
                                feedback = app.feedback,
                                onBack = { pop() },
                                onPlay = ::play,
                                onUnlocksSeen = app::markUnlocksSeen,
                                onSolvedShown = app::markSolvedShown,
                            )
                        }
                    }
                    Screen.Settings -> SettingsScreen(
                        settings = current,
                        onBack = { pop() },
                        onSound = app::setSound,
                        onVibration = app::setVibration,
                        onTheme = app::setTheme,
                        onResetProgress = app::resetProgress,
                        onTap = app.feedback::tap,
                    )
                    is Screen.Game -> GameScreen(
                        levelIndex = screen.index,
                        viewModel = game,
                        feedback = app.feedback,
                        onBack = {
                            // From a game, "back" lands on the level list even
                            // when the game was started from Play.
                            if (stack.getOrNull(stack.size - 2) == Screen.Levels) {
                                pop()
                            } else {
                                forward = false
                                stack = stack.dropLast(1) + Screen.Levels
                            }
                        },
                        onOpenLevel = {
                            game.open(it)
                            replaceTop(Screen.Game(it))
                        },
                    )
                }
            }
        }
    }
}

private fun transitionFor(from: Screen, to: Screen, forward: Boolean): ContentTransform = when {
    // Level to level: the board's own entrance is the transition.
    from is Screen.Game && to is Screen.Game -> fadeIn(tween(180)) togetherWith fadeOut(tween(120))
    forward -> (slideInHorizontally(tween(320)) { it / 5 } + fadeIn(tween(260)) + scaleIn(tween(320), initialScale = 0.98f)) togetherWith
        (slideOutHorizontally(tween(320)) { -it / 8 } + fadeOut(tween(200)))
    else -> (slideInHorizontally(tween(320)) { -it / 5 } + fadeIn(tween(260))) togetherWith
        (slideOutHorizontally(tween(320)) { it / 8 } + fadeOut(tween(200)) + scaleOut(tween(320), targetScale = 0.98f))
}
