package dev.stefan.sokoban.ui

import dev.stefan.sokoban.R
import dev.stefan.sokoban.core.levels.LevelPack
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class LevelTextTest {

    private val english = strings("src/main/res/values/levels.xml")
    private val khmer = strings("src/main/res/values-km/levels.xml")

    @Test
    fun `every level shows its core name and hint in English`() {
        for (entry in LevelPack.levels) {
            assertEquals(entry.name, english[resourceName(LevelText.name(entry.id))], entry.id)
            assertEquals(entry.hint, LevelText.hint(entry.id)?.let { english[resourceName(it)] }, entry.id)
        }
    }

    @Test
    fun `every world shows its core title and subtitle in English`() {
        for (world in LevelPack.worlds) {
            assertEquals(world.title, english[resourceName(LevelText.worldTitle(world.number))])
            assertEquals(world.subtitle, english[resourceName(LevelText.worldSubtitle(world.number))])
        }
    }

    @Test
    fun `Khmer translates every name and hint`() {
        assertEquals(english.keys, khmer.keys)
        assertEquals(emptyList<String>(), khmer.filterValues { it.isBlank() || it in english.values }.keys.toList())
    }

    private fun resourceName(id: Int): String = R.string::class.java.fields.first { it.getInt(null) == id }.name

    /** The `<string>` resources of [path], relative to the module, by name. */
    private fun strings(path: String): Map<String, String> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(path))
        val nodes = document.getElementsByTagName("string")
        return (0 until nodes.length).associate { index ->
            val node = nodes.item(index)
            node.attributes.getNamedItem("name").nodeValue to node.textContent
        }
    }
}
