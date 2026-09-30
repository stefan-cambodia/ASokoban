package dev.stefan.sokoban.core.levels

/** World 1 — each level introduces one idea, with a hint to name it. */
internal object TutorialLevels {

    val levels = listOf(
        LevelDefinition(
            id = "w1-01",
            name = "First Push",
            par = 1,
            hint = "Walk into the crate to push it onto the target.",
            map = """
                ########
                #      #
                # @ $ .#
                #      #
                ########
            """.trimIndent(),
        ),
        LevelDefinition(
            id = "w1-02",
            name = "Change of Plan",
            par = 1,
            hint = "Walk around a crate to push it in a new direction.",
            map = """
                #######
                #     #
                # @$  #
                #     #
                #   . #
                #######
            """.trimIndent(),
        ),
        LevelDefinition(
            id = "w1-03",
            name = "Pair",
            par = 1,
            hint = "Every crate needs a target of its own.",
            map = """
                ########
                #      #
                # $  . #
                #  @   #
                # $  . #
                #      #
                ########
            """.trimIndent(),
        ),
        LevelDefinition(
            id = "w1-04",
            name = "Along the Wall",
            par = 1,
            hint = "Against a wall, a crate can only slide along it. Stuck? Tap Undo.",
            map = """
                #######
                #.    #
                #  $  #
                #   @ #
                #######
            """.trimIndent(),
        ),
        LevelDefinition(
            id = "w1-05",
            name = "Doorway",
            par = 1,
            hint = "Crates fit through narrow gaps — line them up first.",
            map = """
                ##########
                #    #   #
                #  $     #
                # @  #  .#
                ##########
            """.trimIndent(),
        ),
        LevelDefinition(
            id = "w1-06",
            name = "Far End First",
            par = 1,
            hint = "Fill the deepest target first, or the way gets blocked.",
            map = """
                ##########
                #    #####
                # $  $ ..#
                #  @ #####
                #    #
                ######
            """.trimIndent(),
        ),
        LevelDefinition(
            id = "w1-07",
            name = "One at a Time",
            par = 1,
            hint = "You can only push one crate at a time.",
            map = """
                #########
                #       #
                # @$$  .#
                #     . #
                #########
            """.trimIndent(),
        ),
        LevelDefinition(
            id = "w1-08",
            name = "Three",
            par = 1,
            hint = "Plan which crate goes where before you push.",
            map = """
                 #######
                 #  .  #
                ##$ $ $##
                #   @   #
                #  . .  #
                #########
            """.trimIndent(),
        ),
        LevelDefinition(
            id = "w1-09",
            name = "Corners",
            par = 1,
            hint = "A crate pushed into a corner can never leave it.",
            map = """
                ########
                #  .   #
                # $##$ #
                #  @ . #
                #      #
                ########
            """.trimIndent(),
        ),
        LevelDefinition(
            id = "w1-10",
            name = "Graduation",
            par = 1,
            map = """
                 ########
                 #   .  #
                ## $##$ #
                #  . @  #
                # $##.  #
                #       #
                #########
            """.trimIndent(),
        ),
    )
}
