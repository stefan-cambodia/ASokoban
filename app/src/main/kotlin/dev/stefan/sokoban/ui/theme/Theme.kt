package dev.stefan.sokoban.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.stefan.sokoban.R

/** Nunito, a variable font: one file, every weight the UI needs. */
private fun nunito(weight: Int) = Font(
    resId = R.font.nunito,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Nunito = FontFamily(nunito(500), nunito(600), nunito(700), nunito(800), nunito(900))

/** The handful of text styles the game uses, named by role rather than size. */
object GameType {
    val display = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.Black, fontSize = 52.sp, letterSpacing = 0.08.em)
    val title = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, letterSpacing = 0.06.em)
    val heading = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
    val button = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, letterSpacing = 0.1.em)
    val number = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.Black, fontSize = 30.sp)
    val body = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    val label = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, letterSpacing = 0.14.em)
    val caption = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 12.sp)
}

@Composable
fun SokobanTheme(dark: Boolean, content: @Composable () -> Unit) {
    val palette = if (dark) DarkPalette else LightPalette
    // Material components (dialogs, ripples) follow the same palette.
    val scheme = if (dark) {
        darkColorScheme(
            primary = palette.accent,
            onPrimary = palette.onAccent,
            background = palette.background,
            surface = palette.surface,
            onSurface = palette.textPrimary,
            onSurfaceVariant = palette.textSecondary,
            surfaceContainerHigh = palette.surfaceRaised,
            error = palette.danger,
        )
    } else {
        lightColorScheme(
            primary = palette.accent,
            onPrimary = palette.onAccent,
            background = palette.background,
            surface = palette.surface,
            onSurface = palette.textPrimary,
            onSurfaceVariant = palette.textSecondary,
            surfaceContainerHigh = palette.surfaceRaised,
            error = palette.danger,
        )
    }
    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

/** Shorthand for the current palette. */
val palette: Palette
    @Composable @ReadOnlyComposable get() = LocalPalette.current
