package dev.stefan.sokoban.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.stefan.sokoban.R
import dev.stefan.sokoban.core.levels.LevelEntry
import dev.stefan.sokoban.core.levels.World

/*
 * :core names its levels and worlds in English. The screens show them from
 * string resources instead, in the device language; the tests keep the
 * English resources identical to :core.
 */

val LevelEntry.displayName: String
    @Composable get() = stringResource(LevelText.name(id))

val LevelEntry.displayHint: String?
    @Composable get() = LevelText.hint(id)?.let { stringResource(it) }

val World.displayTitle: String
    @Composable get() = stringResource(LevelText.worldTitle(number))

val World.displaySubtitle: String
    @Composable get() = stringResource(LevelText.worldSubtitle(number))

internal object LevelText {

    @StringRes
    fun name(id: String): Int = names.getValue(id)

    @StringRes
    fun hint(id: String): Int? = hints[id]

    @StringRes
    fun worldTitle(number: Int): Int = worldTitles[number - 1]

    @StringRes
    fun worldSubtitle(number: Int): Int = worldSubtitles[number - 1]

    private val worldTitles = listOf(
        R.string.world_1_title,
        R.string.world_2_title,
        R.string.world_3_title,
        R.string.world_4_title,
        R.string.world_5_title,
        R.string.world_6_title,
        R.string.world_7_title,
    )

    private val worldSubtitles = listOf(
        R.string.world_1_subtitle,
        R.string.world_2_subtitle,
        R.string.world_3_subtitle,
        R.string.world_4_subtitle,
        R.string.world_5_subtitle,
        R.string.world_6_subtitle,
        R.string.world_7_subtitle,
    )

    private val names = mapOf(
        "w1-01" to R.string.level_w1_01,
        "w1-02" to R.string.level_w1_02,
        "w1-03" to R.string.level_w1_03,
        "w1-04" to R.string.level_w1_04,
        "w1-05" to R.string.level_w1_05,
        "w1-06" to R.string.level_w1_06,
        "w1-07" to R.string.level_w1_07,
        "w1-08" to R.string.level_w1_08,
        "w1-09" to R.string.level_w1_09,
        "w1-10" to R.string.level_w1_10,
        "w2-01" to R.string.level_w2_01,
        "w2-02" to R.string.level_w2_02,
        "w2-03" to R.string.level_w2_03,
        "w2-04" to R.string.level_w2_04,
        "w2-05" to R.string.level_w2_05,
        "w2-06" to R.string.level_w2_06,
        "w2-07" to R.string.level_w2_07,
        "w2-08" to R.string.level_w2_08,
        "w2-09" to R.string.level_w2_09,
        "w2-10" to R.string.level_w2_10,
        "w3-01" to R.string.level_w3_01,
        "w3-02" to R.string.level_w3_02,
        "w3-03" to R.string.level_w3_03,
        "w3-04" to R.string.level_w3_04,
        "w3-05" to R.string.level_w3_05,
        "w3-06" to R.string.level_w3_06,
        "w3-07" to R.string.level_w3_07,
        "w3-08" to R.string.level_w3_08,
        "w3-09" to R.string.level_w3_09,
        "w3-10" to R.string.level_w3_10,
        "w4-01" to R.string.level_w4_01,
        "w4-02" to R.string.level_w4_02,
        "w4-03" to R.string.level_w4_03,
        "w4-04" to R.string.level_w4_04,
        "w4-05" to R.string.level_w4_05,
        "w4-06" to R.string.level_w4_06,
        "w4-07" to R.string.level_w4_07,
        "w4-08" to R.string.level_w4_08,
        "w4-09" to R.string.level_w4_09,
        "w4-10" to R.string.level_w4_10,
        "w5-01" to R.string.level_w5_01,
        "w5-02" to R.string.level_w5_02,
        "w5-03" to R.string.level_w5_03,
        "w5-04" to R.string.level_w5_04,
        "w5-05" to R.string.level_w5_05,
        "w5-06" to R.string.level_w5_06,
        "w5-07" to R.string.level_w5_07,
        "w5-08" to R.string.level_w5_08,
        "w5-09" to R.string.level_w5_09,
        "w5-10" to R.string.level_w5_10,
        "w6-01" to R.string.level_w6_01,
        "w6-02" to R.string.level_w6_02,
        "w6-03" to R.string.level_w6_03,
        "w6-04" to R.string.level_w6_04,
        "w6-05" to R.string.level_w6_05,
        "w6-06" to R.string.level_w6_06,
        "w6-07" to R.string.level_w6_07,
        "w6-08" to R.string.level_w6_08,
        "w6-09" to R.string.level_w6_09,
        "w6-10" to R.string.level_w6_10,
        "w7-01" to R.string.level_w7_01,
        "w7-02" to R.string.level_w7_02,
        "w7-03" to R.string.level_w7_03,
        "w7-04" to R.string.level_w7_04,
        "w7-05" to R.string.level_w7_05,
        "w7-06" to R.string.level_w7_06,
        "w7-07" to R.string.level_w7_07,
        "w7-08" to R.string.level_w7_08,
        "w7-09" to R.string.level_w7_09,
        "w7-10" to R.string.level_w7_10,
    )

    private val hints = mapOf(
        "w1-01" to R.string.hint_w1_01,
        "w1-02" to R.string.hint_w1_02,
        "w1-03" to R.string.hint_w1_03,
        "w1-04" to R.string.hint_w1_04,
        "w1-05" to R.string.hint_w1_05,
        "w1-06" to R.string.hint_w1_06,
        "w1-07" to R.string.hint_w1_07,
        "w1-08" to R.string.hint_w1_08,
        "w1-09" to R.string.hint_w1_09,
    )
}
