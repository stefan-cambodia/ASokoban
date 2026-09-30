package dev.stefan.sokoban

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import dev.stefan.sokoban.audio.SoundPlayer
import dev.stefan.sokoban.core.levels.LevelPack
import dev.stefan.sokoban.data.ProgressRepository
import dev.stefan.sokoban.data.Settings
import dev.stefan.sokoban.data.SettingsRepository
import dev.stefan.sokoban.feedback.Feedback
import dev.stefan.sokoban.feedback.Haptics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SokobanApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Application-wide singletons, created once and handed to view models. */
class AppContainer(context: Context) {

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val settingsRepository = SettingsRepository(store(context, "settings"))

    val progressRepository = ProgressRepository(store(context, "progress"), LevelPack.levels.map { it.id })

    /** Null until DataStore has answered: lets the UI avoid a theme flash. */
    val settings: StateFlow<Settings?> = settingsRepository.settings.stateIn(scope, SharingStarted.Eagerly, null)

    val sounds = SoundPlayer(context.assets, scope)

    val haptics = Haptics(context)

    val feedback = Feedback(sounds, haptics)

    init {
        scope.launch {
            settings.filterNotNull().collect { current ->
                sounds.enabled = current.sound
                haptics.enabled = current.vibration
            }
        }
    }

    private fun store(context: Context, name: String): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        // A corrupted file is replaced by empty preferences instead of crashing.
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
        produceFile = { context.preferencesDataStoreFile(name) },
    )
}
