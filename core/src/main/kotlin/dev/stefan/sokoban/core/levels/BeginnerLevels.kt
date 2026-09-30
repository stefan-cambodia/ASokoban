package dev.stefan.sokoban.core.levels

/** World 2 — small rooms, two to four crates. */
internal object BeginnerLevels {

    val levels = listOf(
        LevelDefinition(
            id = "w2-01",
            name = "Twin Rooms",
            par = 22,
            map = """
                #########
                #   #   #
                # .$  . #
                #   # @ #
                ##$### ##
                 #     #
                 #######
            """.trimIndent(),
        ),
        LevelDefinition(
            id = "w2-02",
            name = "The Bend",
            par = 24,
            map = """
                  ####
                ###  #
                #  .$#
                # #@ ##
                # . $ #
                #     #
                #######
            """.trimIndent(),
        ),
        LevelDefinition(
            id = "w2-03",
            name = "Loading Bay",
            par = 25,
            map = """
                ########
                #  ..  #
                # @    #
                ## ## ##
                #   $$ #
                #      #
                ########
            """.trimIndent(),
        ),
        LevelDefinition(
            id = "w2-04",
            name = "Long Corridor",
            par = 30,
            map = """
                ##########
                #   #    #
                # .@  .  #
                #   ###  #
                ### # $$ #
                  #      #
                  ########
            """.trimIndent(),
        ),
        LevelDefinition(
            id = "w2-05",
            name = "Elbow",
            par = 31,
            map = """
                ######
                #    ###
                # ..$$ #
                # #  #@#
                # .  $ #
                ########
            """.trimIndent(),
        ),
        LevelDefinition(
            id = "w2-06",
            name = "Stairwell",
            par = 43,
            map = """
                ######
                #    #
                # .  ###
                ## # $@#
                 # . $ #
                 #   ###
                 #####
            """.trimIndent(),
        ),
        LevelDefinition(
            id = "w2-07",
            name = "Back Room",
            par = 39,
            map = """
                  ######
                  #  @ #
                ###$## #
                #  . $ #
                # #.#$ #
                #  .  ##
                ##   ##
                 #####
            """.trimIndent(),
        ),
        LevelDefinition(
            id = "w2-08",
            name = "Cellar",
            par = 25,
            map = """
                 ########
                 #  #   #
                ## .$$#@#
                #  #. $ #
                # .$ #  #
                #  #. ###
                ##    #
                 ######
            """.trimIndent(),
        ),
        LevelDefinition(
            id = "w2-09",
            name = "Alcove",
            par = 35,
            map = """
                 #######
                ##  .  #
                #@$# # #
                # *  . #
                ##$#  ##
                 #    #
                 ######
            """.trimIndent(),
        ),
        LevelDefinition(
            id = "w2-10",
            name = "Hook",
            par = 30,
            map = """
                #######
                #  #  #
                # .  .#
                #  #  #
                ##$# ##
                # $.$ #
                #  @  #
                #######
            """.trimIndent(),
        ),
    )
}
