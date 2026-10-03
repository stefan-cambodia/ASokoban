package dev.stefan.sokoban.ui.board3d

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import dev.stefan.sokoban.core.Position
import dev.stefan.sokoban.ui.board.ParticleSpace
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

/**
 * Where the camera stands to show a board, and which cell a point of the
 * screen falls on.
 *
 * The board lies on the ground (y = 0): column x spans x..x+1, row y spans
 * z = y..y+1, so the bottom row is nearest the camera. The camera looks down
 * on it from the front at [pitchDegrees]. Fitted, it stands as close as it can
 * while every corner of the board, walls included, stays [margin] inside the
 * frame; zoomed, it moves closer along the same line and may slide over the
 * board, never past its edges.
 */
internal class CameraRig(
    private val columns: Int,
    private val rows: Int,
    private val wallHeight: Float,
    val fovDegrees: Float = 30f,
    pitchDegrees: Float = 62f,
    private val margin: Float = 0.04f,
) {

    private val sinPitch = sin(Math.toRadians(pitchDegrees.toDouble())).toFloat()
    private val back = Vec3(0f, sinPitch, cos(Math.toRadians(pitchDegrees.toDouble())).toFloat())
    private val tanHalf = tan(Math.toRadians(fovDegrees / 2.0)).toFloat()

    var eye = Vec3(0f, 10f, 10f)
        private set
    var target = Vec3(columns / 2f, 0f, rows / 2f)
        private set

    private var width = 1
    private var height = 1
    private var aspect = 1f
    private var fittedTarget = target
    private var fittedDistance = 10f

    /** Pixels a tile may grow to when zoomed in; zero keeps the board fitted. */
    var zoomTilePixels = 0f

    var zoom = 1f
        private set
    var maxZoom = 1f
        private set
    private var panX = 0f
    private var panZ = 0f

    /** Changes whenever the camera moves, for whoever draws with it. */
    var version by mutableIntStateOf(0)
        private set

    val isZoomed: Boolean get() = zoom > 1.01f

    /** Frames the board for a viewport of [width] by [height] pixels, and fits it whole. */
    fun fit(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        this.width = width
        this.height = height
        aspect = width.toFloat() / height
        var center = Vec3(columns / 2f, wallHeight / 2f, rows / 2f)
        // Perspective makes the near rows larger, so the board is re-centred
        // on screen a few times while solving for the distance.
        repeat(4) {
            val distance = closest(center)
            val (minX, maxX, minY, maxY) = extent(center + back * distance, center)
            val (right, up, _) = basis(center + back * distance, center)
            center = center + right * ((minX + maxX) / 2f * distance * tanHalf * aspect) + up * ((minY + maxY) / 2f * distance * tanHalf)
        }
        fittedTarget = center
        fittedDistance = closest(center)
        zoom = 1f
        panX = 0f
        panZ = 0f
        place()
        val tile = pixelsPerUnit(Vec3(columns / 2f, 0f, rows / 2f))
        maxZoom = if (zoomTilePixels > 0f && tile > 0f) (zoomTilePixels / tile).coerceAtLeast(1f) else 1f
    }

    /** The nearest distance from which the whole board shows around [center]. */
    private fun closest(center: Vec3): Float {
        var near = 0.5f
        var far = 400f
        repeat(40) {
            val mid = (near + far) / 2f
            if (fits(center + back * mid, center)) far = mid else near = mid
        }
        return far
    }

    private fun place() {
        target = fittedTarget + Vec3(panX, 0f, panZ)
        eye = target + back * (fittedDistance / zoom)
        val (right, up, forward) = basis(eye, target)
        axisRight = right
        axisUp = up
        axisForward = forward
        version++
    }

    // The camera's axes, kept for projecting many points a frame.
    private var axisRight = Vec3(1f, 0f, 0f)
    private var axisUp = Vec3.UP
    private var axisForward = Vec3(0f, 0f, -1f)

    /** Camera axes: right, up and forward. */
    private fun basis(from: Vec3, at: Vec3): Triple<Vec3, Vec3, Vec3> {
        val forward = (at - from).normalized()
        val right = (forward cross Vec3.UP).normalized()
        return Triple(right, right cross forward, forward)
    }

    private fun corners() = buildList {
        for (x in listOf(0f, columns.toFloat())) for (z in listOf(0f, rows.toFloat())) for (y in listOf(0f, wallHeight)) add(Vec3(x, y, z))
    }

    /** Screen extent of the board in normalised device coordinates: minX, maxX, minY, maxY. */
    private fun extent(from: Vec3, at: Vec3): FloatArray {
        val (right, up, forward) = basis(from, at)
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

    private fun fits(from: Vec3, at: Vec3): Boolean {
        val limit = 1f - margin
        return extent(from, at).all { abs(it) <= limit }
    }

    /** Where the ray through a screen point meets the ground, as (x, z); null when it misses. */
    fun groundAt(px: Float, py: Float, groundY: Float = 0f): Pair<Float, Float>? {
        val (right, up, forward) = basis(eye, target)
        val nx = 2f * px / width - 1f
        val ny = 1f - 2f * py / height
        val ray = (forward + right * (nx * tanHalf * aspect) + up * (ny * tanHalf)).normalized()
        if (ray.y >= -1e-4f) return null
        val t = (groundY - eye.y) / ray.y
        val hit = eye + ray * t
        return hit.x to hit.z
    }

    /** The board cell under a screen point. */
    fun cellAt(px: Float, py: Float): Position? =
        groundAt(px, py)?.let { (x, z) -> Position(floor(x).toInt(), floor(z).toInt()) }

    /** A world point on screen, in pixels. */
    fun screenOf(x: Float, y: Float, z: Float): Offset {
        val rx = x - eye.x
        val ry = y - eye.y
        val rz = z - eye.z
        val f = axisForward
        val r = axisRight
        val u = axisUp
        val depth = rx * f.x + ry * f.y + rz * f.z
        val nx = (rx * r.x + ry * r.y + rz * r.z) / (depth * tanHalf * aspect)
        val ny = (rx * u.x + ry * u.y + rz * u.z) / (depth * tanHalf)
        return Offset((nx + 1f) / 2f * width, (1f - ny) / 2f * height)
    }

    /** How many pixels one unit of the board measures across at [point]. */
    fun pixelsPerUnit(point: Vec3): Float {
        val a = screenOf(point.x - 0.5f, point.y, point.z)
        val b = screenOf(point.x + 0.5f, point.y, point.z)
        return (b - a).getDistance()
    }

    fun project(point: Vec3): Pair<Float, Float> = screenOf(point.x, point.y, point.z).let { it.x to it.y }

    /** One pinch step: zoom by [zoomChange] about [centroid], then drag by [panChange], in pixels. */
    fun pinch(centroid: Offset, panChange: Offset, zoomChange: Float) {
        val anchor = groundAt(centroid.x, centroid.y)
        zoom = (zoom * zoomChange).coerceIn(1f, maxZoom)
        place()
        // The ground under the fingers stays under them while they move.
        val now = groundAt(centroid.x + panChange.x, centroid.y + panChange.y)
        if (anchor != null && now != null) slide(anchor.first - now.first, anchor.second - now.second)
    }

    /** Moves the view over the board by ([dx], [dz]) units, as far as its edges allow. */
    fun slide(dx: Float, dz: Float) {
        val (reachX, reachZ) = reach()
        // "+ 0f" turns the -0 of an empty range into a plain zero.
        panX = (panX + dx).coerceIn(-reachX, reachX) + 0f
        panZ = (panZ + dz).coerceIn(-reachZ, reachZ) + 0f
        place()
    }

    /**
     * How far the view may slide from the centre: until the frame's edge, at
     * the board's middle, reaches the board's edge plus half a cell. Fitted,
     * not at all.
     */
    private fun reach(): Pair<Float, Float> {
        if (zoom <= 1.001f) return 0f to 0f
        val distance = fittedDistance / zoom
        val halfX = distance * tanHalf * aspect
        val halfZ = distance * tanHalf / sinPitch
        val simple = 1f - 1f / zoom
        return maxOf(columns / 2f * simple, columns / 2f + EDGE - halfX) to maxOf(rows / 2f * simple, rows / 2f + EDGE - halfZ)
    }

    /**
     * When zoomed in, how far to slide so that the cell centred at ([x], [z])
     * sits [keep] of the way in from every edge; zero when it already does.
     */
    fun slideToShow(x: Float, z: Float, keep: Float = 0.28f): Pair<Float, Float> {
        if (!isZoomed) return 0f to 0f
        val at = screenOf(x, 0f, z)
        val unit = pixelsPerUnit(Vec3(x, 0f, z))
        fun need(value: Float, size: Int): Float {
            val low = size * keep
            val high = size * (1f - keep)
            return when {
                value < low -> (value - low) / unit
                value > high -> (value - high) / unit
                else -> 0f
            }
        }
        // Moving right on screen is +x on the board; down on screen is +z, a
        // little foreshortened, which the per-frame easing absorbs.
        return need(at.x, width) to need(at.y, height)
    }

    /** Sets zoom and pan outright, for animating back to the fitted board. */
    fun set(zoom: Float, panX: Float, panZ: Float) {
        this.zoom = zoom.coerceIn(1f, maxOf(1f, maxZoom))
        this.panX = panX
        this.panZ = panZ
        slide(0f, 0f)
    }

    val pan: Pair<Float, Float> get() = panX to panZ

    /** Particles of the board, in cells, drawn just above the floor. */
    val particles = object : ParticleSpace {
        override fun point(x: Float, y: Float) = screenOf(x, PARTICLE_HEIGHT, y)
        override fun scale(x: Float, y: Float) = pixelsPerUnit(Vec3(x, PARTICLE_HEIGHT, y))
    }

    private companion object {
        const val PARTICLE_HEIGHT = 0.08f

        /** How far past the board's edge, in cells, a zoomed view may look. */
        const val EDGE = 0.5f
    }
}
