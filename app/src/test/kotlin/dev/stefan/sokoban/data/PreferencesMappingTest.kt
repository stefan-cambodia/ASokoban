package dev.stefan.sokoban.data

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import dev.stefan.sokoban.data.ProgressRepository.Companion.toProgress
import dev.stefan.sokoban.data.SettingsRepository.Companion.toSettings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PreferencesMappingTest {

    private val ids = listOf("w1-01", "w1-02", "w1-03")

    @Test
    fun `missing settings use defaults`() {
        assertEquals(Settings(sound = true, vibration = true, theme = ThemeMode.SYSTEM), emptyPreferences().toSettings())
    }

    @Test
    fun `stored settings are read back`() {
        val prefs = preferencesOf(
            booleanPreferencesKey("sound") to false,
            booleanPreferencesKey("vibration") to false,
            stringPreferencesKey("theme") to "DARK",
        )
        assertEquals(Settings(sound = false, vibration = false, theme = ThemeMode.DARK), prefs.toSettings())
    }

    @Test
    fun `unknown theme falls back to system`() {
        val prefs = preferencesOf(stringPreferencesKey("theme") to "NEON")
        assertEquals(ThemeMode.SYSTEM, prefs.toSettings().theme)
    }

    @Test
    fun `progress ignores unknown levels and impossible scores`() {
        val prefs = preferencesOf(
            stringSetPreferencesKey("completed") to setOf("w1-01", "w9-99"),
            stringSetPreferencesKey("unlocked") to setOf("w1-02", "old-level"),
            intPreferencesKey("best_w1-01") to 12,
            intPreferencesKey("best_w1-02") to -4,
            intPreferencesKey("best_w9-99") to 7,
        )
        val progress = prefs.toProgress(ids)
        assertEquals(setOf("w1-01"), progress.completed)
        assertEquals(setOf("w1-02"), progress.unlocked)
        assertEquals(mapOf("w1-01" to 12), progress.bestMoves)
    }

    @Test
    fun `empty progress is a new player`() {
        val progress = emptyPreferences().toProgress(ids)
        assertEquals(emptySet<String>(), progress.completed)
        assertEquals(emptyMap<String, Int>(), progress.bestMoves)
    }
}
