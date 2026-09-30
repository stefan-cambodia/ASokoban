package dev.stefan.sokoban.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LevelParserTest {

    private fun problems(map: String) = LevelParser.validate(map.trimIndent())

    @Test
    fun `parses every standard symbol`() {
        val level = LevelParser.parse(
            """
            #######
            #@$.  #
            # * ###
            #######
            """.trimIndent(),
        )
        assertEquals(7, level.width)
        assertEquals(4, level.height)
        assertEquals(Position(1, 1), level.playerStart)
        assertEquals(listOf(Position(2, 1), Position(2, 2)), level.boxStarts)
        assertEquals(Tile.WALL, level.tileAt(0, 0))
        assertEquals(Tile.GOAL, level.tileAt(3, 1))
        assertEquals(Tile.GOAL, level.tileAt(2, 2))
        assertEquals(Tile.FLOOR, level.tileAt(4, 1))
    }

    @Test
    fun `player on goal and box on goal`() {
        val level = LevelParser.parse(
            """
            ######
            #+$* #
            ######
            """.trimIndent(),
        )
        assertEquals(Position(1, 1), level.playerStart)
        assertEquals(Tile.GOAL, level.tileAt(1, 1))
        assertEquals(listOf(Position(2, 1), Position(3, 1)), level.boxStarts)
        assertEquals(listOf(Position(1, 1), Position(3, 1)), level.goals)
    }

    @Test
    fun `dashes and underscores are floor`() {
        val level = LevelParser.parse("#####\n#@\$.#\n#-_-#\n#####")
        assertEquals(Tile.FLOOR, level.tileAt(1, 2))
        assertEquals(Tile.FLOOR, level.tileAt(2, 2))
    }

    @Test
    fun `ragged rows are padded and the outside is void`() {
        val level = LevelParser.parse(
            """
              ####
            ###  #
            #@$ .#
            ######
            """.trimIndent(),
        )
        assertEquals(6, level.width)
        assertEquals(Tile.VOID, level.tileAt(0, 0))
        assertEquals(Tile.VOID, level.tileAt(1, 0))
        assertEquals(Tile.FLOOR, level.tileAt(3, 1))
        assertEquals(Tile.VOID, level.tileAt(-1, 0))
    }

    @Test
    fun `round trip through text`() {
        val text = """
              ####
            ###  #
            #@$ .#
            # *  #
            ######
        """.trimIndent()
        assertEquals(text, LevelParser.parse(text).toText())
    }

    @Test
    fun `valid level has no problems`() {
        assertEquals(emptyList<String>(), problems("#####\n#@$.#\n#####"))
    }

    @Test
    fun `rejects a missing player`() {
        assertTrue(problems("#####\n# $.#\n#####").any { "no player" in it })
    }

    @Test
    fun `rejects several players`() {
        assertTrue(problems("######\n#@$.@#\n######").any { "2 players" in it })
    }

    @Test
    fun `rejects unknown characters`() {
        val found = problems("#####\n#@$.#\n#x  #\n#####")
        assertTrue(found.any { "Invalid character 'x' at row 3, column 2" in it }, found.toString())
    }

    @Test
    fun `rejects box and goal count mismatch`() {
        assertTrue(problems("######\n#@$$.#\n######").any { "2 boxes but 1 goals" in it })
    }

    @Test
    fun `rejects a level without boxes`() {
        assertTrue(problems("####\n#@ #\n####").any { "no box" in it })
    }

    @Test
    fun `rejects an open level`() {
        assertTrue(problems("#####\n#@$. \n#####").any { "not enclosed" in it })
    }

    @Test
    fun `rejects boxes and goals the player cannot reach`() {
        val found = problems(
            """
            #########
            #@$.#$ .#
            #########
            """,
        )
        assertTrue(found.any { "Box at (5,1)" in it }, found.toString())
        assertTrue(found.any { "Goal at (7,1)" in it }, found.toString())
    }

    @Test
    fun `rejects empty and oversized levels`() {
        assertTrue(problems("   \n  ").any { "empty" in it })
        assertTrue(problems("#".repeat(LevelParser.MAX_SIZE + 1) + "\n#@$.#\n#####").any { "size" in it })
    }

    @Test
    fun `parse throws with every problem listed`() {
        val error = assertThrows(LevelFormatException::class.java) {
            LevelParser.parse("#####\n# $$ \n#####")
        }
        assertTrue(error.problems.size >= 2, error.problems.toString())
    }
}
