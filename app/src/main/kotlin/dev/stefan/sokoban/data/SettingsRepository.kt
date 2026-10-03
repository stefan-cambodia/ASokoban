package dev.stefan.sokoban.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class Settings(
    val sound: Boolean = true,
    val vibration: Boolean = true,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    /** Draw the game board in 3D instead of the flat board. */
    val board3d: Boolean = true,
)

class SettingsRepository(private val store: DataStore<Preferences>) {

    val settings: Flow<Settings> = store.safeData().map { it.toSettings() }

    suspend fun setSound(enabled: Boolean) = store.edit { it[SOUND] = enabled }

    suspend fun setVibration(enabled: Boolean) = store.edit { it[VIBRATION] = enabled }

    suspend fun setTheme(mode: ThemeMode) = store.edit { it[THEME] = mode.name }

    suspend fun setBoard3d(enabled: Boolean) = store.edit { it[BOARD_3D] = enabled }

    companion object {
        private val SOUND = booleanPreferencesKey("sound")
        private val VIBRATION = booleanPreferencesKey("vibration")
        private val THEME = stringPreferencesKey("theme")
        private val BOARD_3D = booleanPreferencesKey("board_3d")

        /** Missing or unknown values fall back to defaults instead of failing. */
        fun Preferences.toSettings(): Settings {
            val defaults = Settings()
            return Settings(
                sound = this[SOUND] ?: defaults.sound,
                vibration = this[VIBRATION] ?: defaults.vibration,
                theme = this[THEME]?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } } ?: defaults.theme,
                board3d = this[BOARD_3D] ?: defaults.board3d,
            )
        }
    }
}
