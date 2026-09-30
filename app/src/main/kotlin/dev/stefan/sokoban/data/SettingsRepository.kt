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
)

class SettingsRepository(private val store: DataStore<Preferences>) {

    val settings: Flow<Settings> = store.safeData().map { it.toSettings() }

    suspend fun setSound(enabled: Boolean) = store.edit { it[SOUND] = enabled }

    suspend fun setVibration(enabled: Boolean) = store.edit { it[VIBRATION] = enabled }

    suspend fun setTheme(mode: ThemeMode) = store.edit { it[THEME] = mode.name }

    companion object {
        private val SOUND = booleanPreferencesKey("sound")
        private val VIBRATION = booleanPreferencesKey("vibration")
        private val THEME = stringPreferencesKey("theme")

        /** Missing or unknown values fall back to defaults instead of failing. */
        fun Preferences.toSettings(): Settings {
            val defaults = Settings()
            return Settings(
                sound = this[SOUND] ?: defaults.sound,
                vibration = this[VIBRATION] ?: defaults.vibration,
                theme = this[THEME]?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } } ?: defaults.theme,
            )
        }
    }
}
