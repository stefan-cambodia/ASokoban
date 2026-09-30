package dev.stefan.sokoban.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.stefan.sokoban.AppContainer
import dev.stefan.sokoban.SokobanApplication
import dev.stefan.sokoban.core.progress.Progress
import dev.stefan.sokoban.data.Settings
import dev.stefan.sokoban.data.ThemeMode
import dev.stefan.sokoban.feedback.Feedback
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** State and actions shared by the menus: progress, settings, unlock bookkeeping. */
class AppViewModel(private val container: AppContainer) : ViewModel() {

    val settings: StateFlow<Settings?> = container.settings

    val progress: StateFlow<Progress?> =
        container.progressRepository.progress.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val seenUnlocks: StateFlow<Set<String>?> =
        container.progressRepository.seenUnlocks.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val feedback: Feedback get() = container.feedback

    fun setSound(enabled: Boolean) = viewModelScope.launch { container.settingsRepository.setSound(enabled) }

    fun setVibration(enabled: Boolean) = viewModelScope.launch { container.settingsRepository.setVibration(enabled) }

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { container.settingsRepository.setTheme(mode) }

    fun markUnlocksSeen(ids: Set<String>) = viewModelScope.launch { container.progressRepository.markUnlocksSeen(ids) }

    fun resetProgress() = viewModelScope.launch { container.progressRepository.reset() }

    companion object {
        val Factory = viewModelFactory {
            initializer { AppViewModel((this[APPLICATION_KEY] as SokobanApplication).container) }
        }
    }
}
