package dev.stefan.sokoban.ui.board3d

import dev.stefan.sokoban.core.Position
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class Board3dGeometryTest {

    /** Every triangle must face the way its normals say, or it is culled from the wrong side. */
    private fun assertOutward(mesh: MeshData) {
        val p = mesh.positions()
        val n = mesh.normals()
        val idx = mesh.indices()
        fun at(i: Int) = Vec3(p[i * 3], p[i * 3 + 1], p[i * 3 + 2])
        for (t in idx.indices step 3) {
            val (a, b, c) = Triple(idx[t], idx[t + 1], idx[t + 2])
            val face = (at(b) - at(a)) cross (at(c) - at(a))
            if (face.length() < 1e-9f) continue
            val normal = Vec3(n[a * 3], n[a * 3 + 1], n[a * 3 + 2])
            assertTrue(face dot normal > 0f, "triangle $t faces inward")
        }
    }

    @Test
    fun `a box has six outward faces`() {
        val mesh = MeshData().apply { box(Vec3(0f, 0f, 0f), Vec3(1f, 2f, 3f), { Rgba.WHITE }) }
        assertEquals(24, mesh.vertexCount)
        assertEquals(36, mesh.indexCount)
        assertOutward(mesh)
    }

    @Test
    fun `hidden box faces can be left out`() {
        val mesh = MeshData().apply { box(Vec3(0f, 0f, 0f), Vec3(1f, 1f, 1f), { Rgba.WHITE }, skip = 0b001100) }
        assertEquals(4 * 6, mesh.indexCount)
    }

    @Test
    fun `bevelled boxes, spheres and rings face outward`() {
        assertOutward(MeshData().apply { bevelBox(Vec3(0f, 0.5f, 0f), Vec3(0.4f, 0.5f, 0.3f), 0.06f, { _, _ -> Rgba.WHITE }, Rgba.WHITE, skipBottom = false) })
        assertOutward(MeshData().apply { sphere(Vec3(1f, 1f, 1f), Vec3(0.3f, 0.2f, 0.25f), Rgba.WHITE) })
        assertOutward(MeshData().apply { ring(Vec3(0f, 0f, 0f), 0.2f, 0.3f, 0.02f, Rgba.WHITE) })
        assertOutward(MeshData().apply { cylinder(Vec3(0f, 0f, 0f), 0.1f, 0.4f, Rgba.WHITE) })
    }

    @Test
    fun `a bevelled box stays inside its size`() {
        val mesh = MeshData().apply { bevelBox(Vec3(0f, 0.5f, 0f), Vec3(0.4f, 0.5f, 0.3f), 0.06f, { _, _ -> Rgba.WHITE }, Rgba.WHITE) }
        val (min, max) = mesh.bounds()
        assertEquals(-0.4f, min.x, 1e-5f)
        assertEquals(0.4f, max.x, 1e-5f)
        assertEquals(1f, max.y, 1e-5f)
        assertEquals(0.3f, max.z, 1e-5f)
    }

    @Test
    fun `the camera frames the whole board`() {
        for ((columns, rows) in listOf(8 to 5, 14 to 12, 12 to 12, 10 to 7)) {
            val rig = CameraRig(columns, rows, wallHeight = 0.55f).apply { fit(1000, 900) }
            for (x in listOf(0f, columns.toFloat())) for (z in listOf(0f, rows.toFloat())) for (y in listOf(0f, 0.55f)) {
                val (px, py) = rig.project(Vec3(x, y, z))
                assertTrue(px in 0f..1000f && py in 0f..900f, "$columns x $rows: corner ($x, $y, $z) at ($px, $py)")
            }
        }
    }

    @Test
    fun `the board is centred and fills one side of the frame`() {
        val rig = CameraRig(14, 12, wallHeight = 0.55f).apply { fit(1000, 900) }
        val points = buildList {
            for (x in listOf(0f, 14f)) for (z in listOf(0f, 12f)) for (y in listOf(0f, 0.55f)) add(rig.project(Vec3(x, y, z)))
        }
        val left = points.minOf { it.first }
        val right = points.maxOf { it.first }
        val top = points.minOf { it.second }
        val bottom = points.maxOf { it.second }
        assertEquals(1000f - right, left, 4f)
        assertEquals(900f - bottom, top, 4f)
        assertTrue(left < 40f || top < 40f, "fills the frame: $left, $top")
    }

    @Test
    fun `a tap finds the cell it lands on`() {
        val rig = CameraRig(14, 12, wallHeight = 0.55f).apply { fit(1080, 1000) }
        for (row in 0 until 12) for (column in 0 until 14) {
            val (px, py) = rig.project(Vec3(column + 0.5f, 0f, row + 0.5f))
            assertEquals(Position(column, row), rig.cellAt(px, py))
        }
    }

    private fun zoomedRig(): CameraRig = CameraRig(14, 12, wallHeight = 0.55f).apply {
        zoomTilePixels = 200f
        fit(1080, 1000)
    }

    @Test
    fun `zooming keeps the ground under the fingers`() {
        val rig = zoomedRig()
        assertTrue(rig.maxZoom > 1.5f)
        val fingers = androidx.compose.ui.geometry.Offset(700f, 400f)
        val before = rig.groundAt(fingers.x, fingers.y)!!
        rig.pinch(fingers, androidx.compose.ui.geometry.Offset.Zero, 1.5f)
        assertEquals(1.5f, rig.zoom, 1e-4f)
        val after = rig.groundAt(fingers.x, fingers.y)!!
        assertEquals(before.first, after.first, 0.05f)
        assertEquals(before.second, after.second, 0.05f)
    }

    @Test
    fun `zoom stays between fitted and large tiles, and the view stays over the board`() {
        val rig = zoomedRig()
        rig.pinch(androidx.compose.ui.geometry.Offset(540f, 500f), androidx.compose.ui.geometry.Offset.Zero, 100f)
        assertEquals(rig.maxZoom, rig.zoom, 1e-4f)
        rig.slide(1000f, -1000f)
        val (x, z) = rig.pan
        // The view reaches the board's edge but no further than half a cell past it.
        val right = rig.groundAt(1080f, 500f)!!.first
        assertTrue(x > 0f && z < 0f, "pan $x, $z")
        assertTrue(right in 13.5f..15.5f, "right edge of the view at x = $right")
        rig.pinch(androidx.compose.ui.geometry.Offset(540f, 500f), androidx.compose.ui.geometry.Offset.Zero, 0.001f)
        assertEquals(1f, rig.zoom, 1e-4f)
        assertEquals(0f to 0f, rig.pan)
    }

    @Test
    fun `zoomed in, the view slides to keep a far cell in sight`() {
        val rig = zoomedRig()
        assertEquals(0f to 0f, rig.slideToShow(0.5f, 0.5f), "fitted, nothing to do")
        rig.pinch(androidx.compose.ui.geometry.Offset(540f, 500f), androidx.compose.ui.geometry.Offset.Zero, 2.5f)
        repeat(60) {
            val (dx, dz) = rig.slideToShow(0.5f, 11.5f)
            rig.slide(dx * 0.3f, dz * 0.3f)
        }
        val (px, py) = rig.project(Vec3(0.5f, 0f, 11.5f))
        assertTrue(px in 0f..1080f && py in 0f..1000f, "corner cell at $px, $py")
    }
}
