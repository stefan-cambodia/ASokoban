package dev.stefan.sokoban.ui.board

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import dev.stefan.sokoban.core.Level
import dev.stefan.sokoban.core.LevelParser
import dev.stefan.sokoban.core.Position
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BoardCameraTest {

    /** A phone's board area, in pixels at 1x. */
    private val viewport = Size(360f, 320f)

    private fun room(width: Int, height: Int): Level = LevelParser.parse(
        buildList {
            add("#".repeat(width))
            add("#@$." + " ".repeat(width - 5) + "#")
            repeat(height - 3) { add("#" + " ".repeat(width - 2) + "#") }
            add("#".repeat(width))
        }.joinToString("\n"),
    )

    private fun camera(level: Level): BoardCamera {
        val metrics = BoardMetrics.fit(level, viewport.width, viewport.height, maxTile = 72f)
        return BoardCamera(level, metrics, viewport, zoomTile = 60f)
    }

    private fun assertClose(expected: Offset, actual: Offset) {
        assertEquals(expected.x, actual.x, 0.01f, "x of $actual")
        assertEquals(expected.y, actual.y, 0.01f, "y of $actual")
    }

    @Test
    fun `starts on the fitted board`() {
        val camera = camera(room(14, 12))
        assertEquals(1f, camera.zoom)
        assertFalse(camera.isZoomed)
        assertClose(Offset(100f, 50f), camera.toContent(Offset(100f, 50f)))
    }

    @Test
    fun `only boards with small tiles can zoom`() {
        assertTrue(camera(room(14, 12)).canZoom)
        assertFalse(camera(room(5, 5)).canZoom)
    }

    @Test
    fun `pinching keeps the point under the fingers in place`() {
        val camera = camera(room(14, 12))
        val fingers = Offset(180f, 160f)
        camera.transform(fingers, Offset.Zero, 2f)
        assertEquals(2f, camera.zoom)
        assertClose(fingers, camera.toContent(fingers))
    }

    @Test
    fun `zoom stays between the fitted board and large tiles`() {
        val camera = camera(room(14, 12))
        camera.transform(Offset(180f, 160f), Offset.Zero, 10f)
        assertEquals(camera.maxZoom, camera.zoom)
        camera.transform(Offset(180f, 160f), Offset.Zero, 0.01f)
        assertEquals(1f, camera.zoom)
        assertClose(Offset.Zero, camera.pan)
    }

    @Test
    fun `the board cannot be dragged away from the frame`() {
        val level = room(14, 12)
        val camera = camera(level)
        camera.transform(Offset(180f, 160f), Offset.Zero, 2f)
        camera.transform(Offset(180f, 160f), Offset(5000f, 5000f), 1f)
        // The board's top-left corner reaches the frame's, no further.
        val metrics = BoardMetrics.fit(level, viewport.width, viewport.height, 72f)
        assertClose(Offset.Zero, metrics.origin * camera.zoom + camera.pan)
    }

    @Test
    fun `the view follows the hero out of sight`() {
        val level = room(14, 12)
        val camera = camera(level)
        assertNull(camera.panToShow(Position(12, 10)), "fitted, everything shows")

        camera.transform(Offset.Zero, Offset.Zero, 2f)
        assertNull(camera.panToShow(Position(2, 2)), "already in view")
        val far = Position(12, 10)
        val pan = requireNotNull(camera.panToShow(far)) { "out of view, so the view follows" }
        camera.set(camera.zoom, pan)
        val metrics = BoardMetrics.fit(level, viewport.width, viewport.height, 72f)
        val topLeft = metrics.topLeft(far.x.toFloat(), far.y.toFloat()) * camera.zoom + camera.pan
        val size = metrics.tile * camera.zoom
        assertTrue(topLeft.x >= 0f && topLeft.x + size <= viewport.width, "x $topLeft")
        assertTrue(topLeft.y >= 0f && topLeft.y + size <= viewport.height, "y $topLeft")
    }
}
