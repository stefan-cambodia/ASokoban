package dev.stefan.sokoban.core.levels

/** World 3 — more crates, ordering and temporary parking. */
internal object IntermediateLevels {

    val levels = listOf(
        LevelDefinition(
            id = "w3-01",
            name = "Side by Side",
            par = 31,
            map = """
                #########
                #   #   #
                # .   . #
                ##$$#$ ##
                 #+$  .#
                 #######
            """.trimIndent(),
        ),
        LevelDefinition(
            id = "w3-02",
            name = "Pillars",
            par = 23,
            map = """
                #########
                #.  # $+#
                #$# # #$#
                #   . $ #
                # # # # #
                #.  $  .#
                #########
            """.trimIndent(),
        ),
        LevelDefinition(
            id = "w3-03",
            name = "Workbench",
            par = 98,
            map = """
                ########
                #  #   #
                # $    #
                #.##$# #
                #+ #   #
                #.$ $###
                #.   #
                ######
            """.trimIndent(),
        ),
        LevelDefinition(
            id = "w3-04",
            name = "Storeroom",
            par = 55,
            map = """
                ##########
                #    #   #
                # ..   # #
                # ..## # #
                # $$ #   #
                ##@#   # #
                # $$ #   #
                #      ###
                ########
            """.trimIndent(),
        ),
        LevelDefinition(
            id = "w3-05",
            name = "Harbor",
            par = 33,
            map = """
                 #########
                 #   #   #
                 # $*..  #
                ## # # # ##
                #         #
                # ## #$## #
                # .$ #@$. #
                ###########
            """.trimIndent(),
        ),
        LevelDefinition(
            id = "w3-06",
            name = "Office",
            par = 65,
            map = """
                 ########
                 #  #   #
                 #   $. #
                ## ##@# #
                #  * $. #
                # ##$ ###
                #  .  #
                #######
            """.trimIndent(),
        ),
        LevelDefinition(
            id = "w3-07",
            name = "Zigzag",
            par = 71,
            map = """
                  ######
                  #    #
                ###$#* #
                # $.   #
                #@## # #
                # $.  .#
                ###    #
                  ######
            """.trimIndent(),
        ),
        LevelDefinition(
            id = "w3-08",
            name = "Canal",
            par = 86,
            map = """
                ##########
                #   #    #
                # #$$$## #
                #  .#. $ #
                ## # .## #
                # @.$  . #
                # ## #   #
                #    #####
                ######
            """.trimIndent(),
        ),
        LevelDefinition(
            id = "w3-09",
            name = "Loft",
            par = 43,
            map = """
                 ########
                 #   # @#
                 #    $ #
                ## ## ###
                #   .   #
                # #**#$ #
                #   .   #
                #########
            """.trimIndent(),
        ),
        LevelDefinition(
            id = "w3-10",
            name = "Hangar",
            par = 55,
            map = """
                ##########
                #        #
                # ##  ## #
                # #.$$.# #
                #  @.* $ #
                # #. $.# #
                # ## $## #
                #        #
                ##########
            """.trimIndent(),
        ),
    )
}
