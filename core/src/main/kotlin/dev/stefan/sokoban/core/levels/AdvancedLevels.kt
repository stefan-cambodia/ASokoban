package dev.stefan.sokoban.core.levels

/** World 4 — tight rooms where every push counts. */
internal object AdvancedLevels {

    val levels = listOf(
        LevelDefinition(
            id = "w4-01",
            name = "Quarry",
            par = 99,
            map = """
                ##########
                #   #    #
                #@#$  ## #
                # # .. # #
                # $ ..   #
                ###$# #$##
                  #     #
                  #######
            """.trimIndent(),
        ),
        LevelDefinition(
            id = "w4-02",
            name = "Annex",
            par = 92,
            map = """
                 ##########
                 #    #   #
                 #$## $ # #
                ## #*.#   #
                #   ..  ###
                # # ## #  #
                # $$.  .$@#
                ###    #  #
                  ########
            """.trimIndent(),
        ),
        LevelDefinition(
            id = "w4-03",
            name = "Terminal",
            par = 81,
            map = """
                ###########
                #    #    #
                # ## $ ## #
                #  #.#.#  #
                ## $$+$  ##
                #  #.#.#  #
                # ## $ ## #
                #         #
                ###########
            """.trimIndent(),
        ),
        LevelDefinition(
            id = "w4-04",
            name = "Factory Floor",
            par = 74,
            map = """
                ##########
                #  #     #
                #  * ##. #
                ## #  .  #
                # $. #  ##
                # #$$$ . #
                # @  #   #
                ##########
            """.trimIndent(),
        ),
        LevelDefinition(
            id = "w4-05",
            name = "Foundry",
            par = 63,
            map = """
                ##########
                #@ #  #  #
                # $      #
                ##$#..#$##
                #   .. $ #
                # ##  ## #
                #        #
                ##########
            """.trimIndent(),
        ),
        LevelDefinition(
            id = "w4-06",
            name = "Silo",
            par = 61,
            map = """
                 #######
                 #@ #  #
                 #$$   #
                ## ### ##
                #  ...  #
                #  # #  #
                ##$* * ##
                 #     #
                 #######
            """.trimIndent(),
        ),
        LevelDefinition(
            id = "w4-07",
            name = "Garage",
            par = 112,
            map = """
                ##########
                #    #   #
                # .*   # #
                #  ##$.  #
                ##$ #  . #
                 # @$*  ##
                 #    ###
                 ######
            """.trimIndent(),
        ),
        LevelDefinition(
            id = "w4-08",
            name = "Hedges",
            par = 74,
            map = """
                 ########
                 #  #   #
                ##$.  # #
                #  #  . #
                # +  #  #
                #  # $. #
                ##$*$#  #
                 #      #
                 ########
            """.trimIndent(),
        ),
        LevelDefinition(
            id = "w4-09",
            name = "Citadel",
            par = 84,
            map = """
                 ########
                 #  #   #
                ## .$$* #
                #  ## # #
                # .@ .  #
                # # ##$##
                #  .$ * #
                ##      #
                 ########
            """.trimIndent(),
        ),
        LevelDefinition(
            id = "w4-10",
            name = "Ramparts",
            par = 66,
            map = """
                ###########
                #    #  @ #
                # ## #$## #
                #  .   .$ #
                ### #$# ###
                # $*   .  #
                # #  .  # #
                #         #
                ###########
            """.trimIndent(),
        ),
    )
}
