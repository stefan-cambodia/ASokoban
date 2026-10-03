package dev.stefan.sokoban.ui.board3d

import dev.stefan.sokoban.core.Position
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

/**
 * Where the camera stands to show a whole board, and which cell a point of
 * the screen falls on.
 *
 * The board lies on the ground (y = 0): column x spans x..x+1, row y spans
 * z = y..y+1, so the bottom row is nearest the camera. The camera looks down
 * on it from the front at [pitchDegrees], as close as it can while every
 * corner of the board, walls included, stays [margin] inside the frame.
 */
internal class CameraRig(
    private val columns: Int,
    private val rows: Int,
    private val wallHeight: Float,
    val fovDegrees: Float = 30f,
    pitchDegrees: Float = 62f,
    private val margin: Float = 0.04f,
) {

    private val back = Vec3(0f, sin(Math.toRadians(pitchDegrees.toDouble())).toFloat(), cos(Math.toRadians(pitchDegrees.toDouble())).toFloat())

    var eye = Vec3(0f, 10f, 10f)
        private set
    var target = Vec3(columns / 2f, 0f, rows / 2f)
        private set

    private var aspect = 1f
    private val tanHalf = tan(Math.toRadians(fovDegrees / 2.0)).toFloat()

    /** Frames the board for a viewport of [width] by [height] pixels. */
    fun fit(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        aspect = width.toFloat() / height
        target = Vec3(columns / 2f, wallHeight / 2f, rows / 2f)
        // Perspective makes the near rows larger, so the box is re-centred on
        // screen a few times while solving for the distance.
        repeat(4) {
            var near = 0.5f
            var far = 400f
            repeat(40) {
                val mid = (near + far) / 2f
                if (fits(target + back * mid)) far = mid else near = mid
            }
            eye = target + back * far
            val (minX, maxX, minY, maxY) = extent(eye)
            val depth = (target - eye).length()
            val (right, up, _) = basis(eye)
            target = target + right * ((minX + maxX) / 2f * depth * tanHalf * aspect) + up * ((minY + maxY) / 2f * depth * tanHalf)
        }
        eye = run {
            var near = 0.5f
            var far = 400f
            repeat(40) {
                val mid = (near + far) / 2f
                if (fits(target + back * mid)) far = mid else near = mid
            }
            target + back * far
        }
    }

    /** Camera axes: right, up and forward. */
    private fun basis(from: Vec3): Triple<Vec3, Vec3, Vec3> {
        val forward = (target - from).normalized()
        val right = (forward cross Vec3.UP).normalized()
        return Triple(right, right cross forward, forward)
    }

    private fun corners() = buildList {
        for (x in listOf(0f, columns.toFloat())) for (z in listOf(0f, rows.toFloat())) for (y in listOf(0f, wallHeight)) add(Vec3(x, y, z))
    }

    /** Screen extent of the board in normalised device coordinates: minX, maxX, minY, maxY. */
    private fun extent(from: Vec3): FloatArray {
        val (right, up, forward) = basis(from)
        val result = floatArrayOf(Float.MAX_VALUE, -Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE)
        for (corner in corners()) {
            val rel = corner - from
            val depth = rel dot forward
            val x = (rel dot right) / (depth * tanHalf * aspect)
            val y = (rel dot up) / (depth * tanHalf)
            result[0] = minOf(result[0], x); result[1] = maxOf(result[1], x)
            result[2] = minOf(result[2], y); result[3] = maxOf(result[3], y)
        }
        return result
    }

    private fun fits(from: Vec3): Boolean {
        val limit = 1f - margin
        return extent(from).all { abs(it) <= limit }
    }

    /** Where the ray through a screen point meets the ground, as (x, z); null when it misses. */
    fun groundAt(px: Float, py: Float, width: Int, height: Int, groundY: Float = 0f): Pair<Float, Float>? {
        val (right, up, forward) = basis(eye)
        val nx = 2f * px / width - 1f
        val ny = 1f - 2f * py / height
        val ray = (forward + right * (nx * tanHalf * aspect) + up * (ny * tanHalf)).normalized()
        if (ray.y >= -1e-4f) return null
        val t = (groundY - eye.y) / ray.y
        val hit = eye + ray * t
        return hit.x to hit.z
    }

    /** The board cell under a screen point. */
    fun cellAt(px: Float, py: Float, width: Int, height: Int): Position? =
        groundAt(px, py, width, height)?.let { (x, z) -> Position(floor(x).toInt(), floor(z).toInt()) }

    /** Projection of a world point to pixels, for tests and overlays. */
    fun project(point: Vec3, width: Int, height: Int): Pair<Float, Float> {
        val (right, up, forward) = basis(eye)
        val rel = point - eye
        val depth = rel dot forward
        val nx = (rel dot right) / (depth * tanHalf * aspect)
        val ny = (rel dot up) / (depth * tanHalf)
        return (nx + 1f) / 2f * width to (1f - ny) / 2f * height
    }
}
