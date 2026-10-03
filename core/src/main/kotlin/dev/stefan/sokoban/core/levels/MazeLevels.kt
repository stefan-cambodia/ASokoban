package dev.stefan.sokoban.core.levels

/** World 7 — large rooms split by walls and corridors, with eight crates. */
internal object MazeLevels {

    val levels = listOf(
        LevelDefinition(
            id = "w7-01",
            name = "Clockwork",
            par = 167,
            map = """
                #############
                #     #     #
                # #.$ $ $.# #
                #   # # # $ #
                # #. +..$.# #
                #   # # # $ #
                # #   .   # #
                # $ ## ##   #
                # # $     # #
                #           #
                #############
            """.trimIndent(),
        ),
        LevelDefinition(
            id = "w7-02",
            name = "Catacombs",
            par = 185,
            map = """
                ##############
                #   #    #   #
                # * # ##$#$* #
                # $ #  $ #   #
                ##   $ #    ##
                #  ## +.. #  #
                #  #  .$.## ##
                ## #       # #
                #    ## #    #
                # .  #   #   #
                #   ##   #   #
                ##############
            """.trimIndent(),
        ),
        LevelDefinition(
            id = "w7-03",
            name = "Aqueduct",
            par = 251,
            map = """
                ##############
                #     ##     #
                # ###$@$ ### #
                # #  .$ .  # #
                #   ## ##$#  #
                ### #.*..  ###
                #   # ## #   #
                # #   .  . $ #
                # ###$ $ ### #
                #     ##     #
                ##############
            """.trimIndent(),
        ),
        LevelDefinition(
            id = "w7-04",
            name = "Minotaur",
            par = 234,
            map = """
                ##############
                #      #     #
                # ## $...  # #
                #  # $# #  # #
                #   $ .@.$   #
                ## # $# #  ###
                #    ...     #
                # # $# $ # # #
                #  #    # $  #
                #    #     # #
                ##############
            """.trimIndent(),
        ),
        LevelDefinition(
            id = "w7-05",
            name = "Warren",
            par = 235,
            map = """
                ############
                #    #     #
                #  # $  #  #
                ##$#.$#. # #
                #   .  .   #
                # ## $ ##$##
                #   . @.   #
                # #* #. #  #
                #  #    #  #
                # $  ##  $ #
                #  #     # #
                ############
            """.trimIndent(),
        ),
        LevelDefinition(
            id = "w7-06",
            name = "Honeycomb",
            par = 174,
            map = """
                #############
                #   #   #   #
                # # $ # $$# #
                #   #   #   #
                ## . . . . ##
                #  #$# #$#  #
                ## . .@. . ##
                #   #   #   #
                # # $ # $ # #
                #   # $ #   #
                #           #
                #############
            """.trimIndent(),
        ),
        LevelDefinition(
            id = "w7-07",
            name = "Knot",
            par = 318,
            map = """
                ############
                #    #     #
                # ## # ### #
                #  .   #.  #
                ##$#$# $ # #
                #  . ##.@# #
                # #   #  . #
                # # .  $## #
                #   ##$.$  #
                # #  $ #$# #
                #   #    . #
                ############
            """.trimIndent(),
        ),
        LevelDefinition(
            id = "w7-08",
            name = "Cistern",
            par = 283,
            map = """
                #############
                #     #     #
                # # #   #$# #
                #   ..#..   #
                ## # @   # ##
                #   # $ # $ #
                ##$#     # ##
                # $ ..#..   #
                # #$# $ #$# #
                #           #
                #############
            """.trimIndent(),
        ),
        LevelDefinition(
            id = "w7-09",
            name = "Bastion",
            par = 224,
            map = """
                ##############
                #     #      #
                # ##  $ #  # #
                #  # $#   ## #
                #     ##   $ #
                ###  .... #$ #
                #  # $.+ ##  #
                #  #  ..     #
                ## ## $$#  # #
                #     #  $ # #
                #  #         #
                ##############
            """.trimIndent(),
        ),
        LevelDefinition(
            id = "w7-10",
            name = "Heart of the Maze",
            par = 260,
            map = """
                ##############
                # * #   #    #
                # # #$# # ## #
                # #   #  . $ #
                # ### ###$## #
                #   #.. #  # #
                ### #  +#$ # #
                #   ##$.  .# #
                # #    ##$ # #
                # # ##   . $ #
                #   #    #   #
                ##############
            """.trimIndent(),
        ),
    )
}
