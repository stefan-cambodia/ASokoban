package dev.stefan.sokoban.core.progress

import kotlin.math.ceil

/**
 * A player's campaign progress, keyed by level id.
 *
 * Plain data with no storage concern: the app maps it to and from DataStore, and
 * [ProgressRules] decides how it evolves.
 */
data class Progress(
    val completed: Set<String> = emptySet(),
    val bestMoves: Map<String, Int> = emptyMap(),
    val unlocked: Set<String> = emptySet(),
)

/** What finishing a level changed. */
data class Completion(
    val progress: Progress,
    val moves: Int,
    val stars: Int,
    val previousBest: Int?,
    val isNewBest: Boolean,
    /** Ids unlocked by this completion, in campaign order. */
    val newlyUnlocked: List<String>,
)

/**
 * Campaign rules.
 *
 * The first level is always open. Solving a level opens the next
 * [UNLOCK_AHEAD] levels, so a player stuck on one puzzle can move on and come
 * back to it later instead of quitting.
 */
object ProgressRules {

    const val UNLOCK_AHEAD = 2

    fun isUnlocked(progress: Progress, levelIds: List<String>, index: Int): Boolean {
        val id = levelIds.getOrNull(index) ?: return false
        return index == 0 || id in progress.unlocked || id in progress.completed
    }

    fun complete(progress: Progress, levelIds: List<String>, index: Int, moves: Int, par: Int): Completion {
        require(moves > 0) { "A solved level needs at least one move" }
        val id = levelIds[index]
        val previousBest = progress.bestMoves[id]
        val isNewBest = previousBest == null || moves < previousBest

        val toUnlock = (index + 1..index + UNLOCK_AHEAD)
            .mapNotNull { levelIds.getOrNull(it) }
            .filter { !isUnlocked(progress, levelIds, levelIds.indexOf(it)) }

        val updated = progress.copy(
            completed = progress.completed + id,
            bestMoves = if (isNewBest) progress.bestMoves + (id to moves) else progress.bestMoves,
            unlocked = progress.unlocked + id + toUnlock,
        )
        return Completion(
            progress = updated,
            moves = moves,
            stars = stars(moves, par),
            previousBest = previousBest,
            isNewBest = isNewBest,
            newlyUnlocked = toUnlock,
        )
    }

    /**
     * Three stars at or under par, two within a reasonable margin, one otherwise:
     * every solve earns at least one star.
     */
    fun stars(moves: Int, par: Int): Int = when {
        moves <= par -> 3
        moves <= twoStarLimit(par) -> 2
        else -> 1
    }

    fun twoStarLimit(par: Int): Int = ceil(par * 1.25).toInt() + 4

    /** The level to open when the player taps Play: the first unsolved open level. */
    fun nextLevelToPlay(progress: Progress, levelIds: List<String>): Int {
        val firstOpenUnsolved = levelIds.indices.firstOrNull { index ->
            levelIds[index] !in progress.completed && isUnlocked(progress, levelIds, index)
        }
        return firstOpenUnsolved ?: 0
    }
}
