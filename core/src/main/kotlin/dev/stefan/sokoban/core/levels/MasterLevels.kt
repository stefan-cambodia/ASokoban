package dev.stefan.sokoban.core.levels

/** World 5 — dense puzzles with long solutions. */
internal object MasterLevels {

    val levels = listOf(
        LevelDefinition(
            id = "w5-01",
            name = "Maze",
            par = 74,
            map = """
                ##########
                #    #   #
                # ## *$#@#
                # #  # $ #
                #  .   #.#
                ## # # # #
                #  . $. $#
                #    #   #
                ##########
            """.trimIndent(),
        ),
        LevelDefinition(
            id = "w5-02",
            name = "Courts",
            par = 80,
            map = """
                ##########
                #    #   #
                #  .   . #
                ### ##$# #
                #  + $.$ #
                # # $#  ##
                #  .  $ #
                #########
            """.trimIndent(),
        ),
        LevelDefinition(
            id = "w5-03",
            name = "Cathedral",
            par = 157,
            map = """
                  #########
                  #   #   #
                ### #   # #
                #   . . . #
                # # ##### #
                #  $+$. * #
                ###$#$ $# #
                  #       #
                  #########
            """.trimIndent(),
        ),
        LevelDefinition(
            id = "w5-04",
            name = "Arcade",
            par = 81,
            map = """
                ###########
                #    #    #
                # ## $$##@#
                #   ... $ #
                # #  # $# #
                #   ..* $ #
                ####   ####
                   #####
            """.trimIndent(),
        ),
        LevelDefinition(
            id = "w5-05",
            name = "Strongroom",
            par = 171,
            map = """
                #########
                #...#   #
                #.. #$$ #
                # $   # #
                ##$#@   #
                #    #$ #
                #       #
                #########
            """.trimIndent(),
        ),
        LevelDefinition(
            id = "w5-06",
            name = "Twin Halls",
            par = 86,
            map = """
                ###########
                #    #    #
                # *.$  .. #
                #  # # #  #
                ## #   # ##
                #@$$ #    #
                # $.   .$ #
                ###########
            """.trimIndent(),
        ),
        LevelDefinition(
            id = "w5-07",
            name = "Plaza",
            par = 100,
            map = """
                ##########
                #    #   #
                # *  $ + #
                #  #*#   #
                ##$#   # #
                #   *#.  #
                # .   $  #
                ##########
            """.trimIndent(),
        ),
        LevelDefinition(
            id = "w5-08",
            name = "Manor",
            par = 113,
            map = """
                ###########
                #   #     #
                #@#$ $# # #
                # $ #.* # #
                ### #.* # #
                # $   # # #
                # #  .  . #
                #     #   #
                ###########
            """.trimIndent(),
        ),
        LevelDefinition(
            id = "w5-09",
            name = "So Close",
            par = 179,
            map = """
                 #########
                 #   #   #
                ##$.@  .$##
                #  # # #  #
                # .$   $. #
                #  # # #  #
                ##$.   .$##
                 #   #   #
                 #########
            """.trimIndent(),
        ),
        LevelDefinition(
            id = "w5-10",
            name = "The Vault",
            par = 250,
            map = """
                  #######
                ###     ###
                # $ #.# $ #
                # #  .  # #
                #  $.+.$  #
                # #  .  # #
                # $ # # $ #
                ###     ###
                  #######
            """.trimIndent(),
        ),
    )
}
