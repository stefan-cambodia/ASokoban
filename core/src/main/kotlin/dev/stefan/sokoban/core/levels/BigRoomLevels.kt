package dev.stefan.sokoban.core.levels

/** World 6 — big rooms, up to 14x12, with six to eight crates. */
internal object BigRoomLevels {

    val levels = listOf(
        LevelDefinition(
            id = "w6-01",
            name = "Customs",
            par = 118,
            map = """
                ##############
                #      #     #
                #  ##$ $  ## #
                #  #.  #  .$ #
                ## #. ### .# #
                # $ .  #  .  #
                # #          #
                # ## # $ ##$##
                #    #  # @  #
                ##############
            """.trimIndent(),
        ),
        LevelDefinition(
            id = "w6-02",
            name = "Cloister",
            par = 170,
            map = """
                #############
                #     #     #
                # ###$# ### #
                # #       # #
                # # #.*.#$# #
                #  $. $$. $ #
                # # #.*.# # #
                # #  @    # #
                # ### # ### #
                #           #
                #############
            """.trimIndent(),
        ),
        LevelDefinition(
            id = "w6-03",
            name = "Crossroads",
            par = 168,
            map = """
                ##############
                #    #  #    #
                #  .   . $ . #
                #    #  #@$  #
                ## ### $### ##
                #  .       . #
                ## ### $### ##
                #    #  #    #
                # $*   .$ $. #
                #    #  #    #
                ##############
            """.trimIndent(),
        ),
        LevelDefinition(
            id = "w6-04",
            name = "Pier",
            par = 172,
            map = """
                ##############
                #   #    #   #
                # . $ ## $ . #
                #   #    #   #
                ##$### ##@####
                #   .    $.  #
                # #$ ####  # #
                # $.$#  # .$ #
                #    .  .    #
                ##############
            """.trimIndent(),
        ),
        LevelDefinition(
            id = "w6-05",
            name = "Granary",
            par = 179,
            map = """
                ############
                #    #     #
                # .. # ##  #
                # .. #     #
                # $  ###$# #
                ##$$# @    #
                #      #$$##
                # #$###    #
                #     # .. #
                #  ## $ .. #
                #     #    #
                ############
            """.trimIndent(),
        ),
        LevelDefinition(
            id = "w6-06",
            name = "Switchyard",
            par = 127,
            map = """
                ##############
                #...       $ #
                #$## #### ## #
                #    #  #  $ #
                #*## #  #$##+#
                #    #  #  $ #
                #$## ## # ## #
                #...       $ #
                ##############
            """.trimIndent(),
        ),
        LevelDefinition(
            id = "w6-07",
            name = "Market",
            par = 133,
            map = """
                ##############
                #  .   $  .  #
                # ## $##  ## #
                #          $ #
                #  #  ..  #  #
                # ## $##  ## #
                #       @ $  #
                #  #  ..  #  #
                # ##  ##$ ## #
                #     $    $ #
                #  .      .  #
                ##############
            """.trimIndent(),
        ),
        LevelDefinition(
            id = "w6-08",
            name = "Atrium",
            par = 135,
            map = """
                #############
                #     #     #
                # ## $   ## #
                # #  ...  # #
                #    # #  $ #
                ##  .   .$ ##
                # $  # #  $ #
                # #  ...  # #
                # ##$$ $ ## #
                # @   #     #
                #############
            """.trimIndent(),
        ),
        LevelDefinition(
            id = "w6-09",
            name = "Freight Yard",
            par = 252,
            map = """
                ##############
                #      #     #
                # #  #   # # #
                #    ##      #
                ##$#    #$#$ #
                #    # @.... #
                # #    #.... #
                #   ## $$#$  #
                # # $  # $ # #
                #    #       #
                ##############
            """.trimIndent(),
        ),
        LevelDefinition(
            id = "w6-10",
            name = "Mill",
            par = 191,
            map = """
                ############
                ###  #    ##
                #  .   ##$ #
                # #.#$     #
                # $.  # ## #
                ## ##$#  $ #
                #     @.## #
                # ## # .   #
                #    # $.$ #
                # $#   ##..#
                #      #   #
                ############
            """.trimIndent(),
        ),
    )
}
