package dev.stefan.sokoban.core.levels

import dev.stefan.sokoban.core.Level
import dev.stefan.sokoban.core.LevelParser

/**
 * One level as authored.
 *
 * @property id stable identifier used for saved progress: reordering levels in
 *   the pack must never move a player's records onto another level.
 * @property par the move count of a known solution, verified by the test suite.
 *   Matching it earns three stars.
 * @property hint short guidance shown on tutorial levels.
 */
data class LevelDefinition(
    val id: String,
    val name: String,
    val par: Int,
    val map: String,
    val hint: String? = null,
)

data class World(
    val number: Int,
    val title: String,
    val subtitle: String,
    val levels: List<LevelDefinition>,
)

/** A level placed in the campaign: its global [index] and the [world] it belongs to. */
class LevelEntry internal constructor(
    val index: Int,
    val world: World,
    val definition: LevelDefinition,
) {
    val id: String get() = definition.id
    val name: String get() = definition.name
    val par: Int get() = definition.par
    val hint: String? get() = definition.hint

    /** 1-based number shown to the player. */
    val number: Int get() = index + 1

    val level: Level by lazy { LevelParser.parse(definition.map) }
}

/** The complete campaign: worlds of increasing difficulty. */
object LevelPack {

    val worlds: List<World> = listOf(
        World(1, "First Steps", "Learn to push", TutorialLevels.levels),
        World(2, "Warehouse", "Beginner", BeginnerLevels.levels),
        World(3, "Workshop", "Intermediate", IntermediateLevels.levels),
        World(4, "Depot", "Advanced", AdvancedLevels.levels),
        World(5, "Vault", "Master", MasterLevels.levels),
    )

    val levels: List<LevelEntry> = buildList {
        for (world in worlds) for (definition in world.levels) add(LevelEntry(size, world, definition))
    }

    val size: Int get() = levels.size

    fun entry(index: Int): LevelEntry = levels[index]

    fun indexOf(id: String): Int = levels.indexOfFirst { it.id == id }
}
