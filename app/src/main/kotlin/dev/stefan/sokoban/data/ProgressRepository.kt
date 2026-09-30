package dev.stefan.sokoban.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import dev.stefan.sokoban.core.progress.Completion
import dev.stefan.sokoban.core.progress.Progress
import dev.stefan.sokoban.core.progress.ProgressRules
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * Campaign progress in DataStore. The rules live in [ProgressRules]; this class
 * only stores and restores their result, keyed by level id.
 */
class ProgressRepository(
    private val store: DataStore<Preferences>,
    private val levelIds: List<String>,
) {

    val progress: Flow<Progress> = store.safeData().map { it.toProgress(levelIds) }

    /** Unlocked levels whose unlock animation the player has already seen. */
    val seenUnlocks: Flow<Set<String>> = store.safeData().map { it[SEEN_UNLOCKS].orEmpty() }

    /**
     * Records a solve atomically: the rules are applied to the stored progress
     * inside the edit, so two quick completions can never overwrite each other.
     */
    suspend fun complete(index: Int, moves: Int, par: Int): Completion {
        lateinit var completion: Completion
        store.edit { preferences ->
            completion = ProgressRules.complete(preferences.toProgress(levelIds), levelIds, index, moves, par)
            preferences.write(completion.progress)
        }
        return completion
    }

    suspend fun markUnlocksSeen(ids: Set<String>) {
        store.edit { it[SEEN_UNLOCKS] = it[SEEN_UNLOCKS].orEmpty() + ids }
    }

    suspend fun reset() {
        store.edit { it.clear() }
    }

    companion object {
        private val COMPLETED = stringSetPreferencesKey("completed")
        private val UNLOCKED = stringSetPreferencesKey("unlocked")
        private val SEEN_UNLOCKS = stringSetPreferencesKey("seen_unlocks")
        private const val BEST_PREFIX = "best_"

        private fun bestKey(id: String) = intPreferencesKey(BEST_PREFIX + id)

        /**
         * Reads progress defensively: ids of levels that no longer exist and
         * impossible move counts are ignored rather than trusted.
         */
        fun Preferences.toProgress(levelIds: List<String>): Progress {
            val known = levelIds.toSet()
            return Progress(
                completed = this[COMPLETED].orEmpty().filterTo(HashSet()) { it in known },
                bestMoves = levelIds.mapNotNull { id -> this[bestKey(id)]?.takeIf { it > 0 }?.let { id to it } }.toMap(),
                unlocked = this[UNLOCKED].orEmpty().filterTo(HashSet()) { it in known },
            )
        }

        private fun MutablePreferences.write(progress: Progress) {
            this[COMPLETED] = progress.completed
            this[UNLOCKED] = progress.unlocked
            progress.bestMoves.forEach { (id, moves) -> this[bestKey(id)] = moves }
        }
    }
}

/**
 * The store's data, with read failures degraded to empty preferences: a broken
 * disk must cost the player their settings, never the ability to play.
 */
internal fun DataStore<Preferences>.safeData(): Flow<Preferences> = data.catch { error ->
    if (error is IOException) emit(emptyPreferences()) else throw error
}
