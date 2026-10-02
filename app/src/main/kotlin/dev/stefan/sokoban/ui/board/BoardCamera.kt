package dev.stefan.sokoban.ui.board

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import dev.stefan.sokoban.core.Level
import dev.stefan.sokoban.core.Position

/**
 * Zoom and pan over a fitted board, for levels whose tiles come out small.
 *
 * A point of the fitted board shows on screen at `point * zoom + pan`. The
 * board never drifts off: along an axis where it is narrower than the
 * [viewport] it stays centred, along one where it is wider it always covers it.
 */
@Stable
internal class BoardCamera(
    private val level: Level,
    private val metrics: BoardMetrics,
    private val viewport: Size,
    /** The largest tile worth zooming to, in pixels. */
    zoomTile: Float,
) {

    val maxZoom: Float = (zoomTile / metrics.tile).coerceAtLeast(1f)

    /** Small levels already show large tiles: pinching them does nothing. */
    val canZoom: Boolean get() = maxZoom >= MIN_USEFUL_ZOOM

    var zoom by mutableFloatStateOf(1f)
        private set

    var pan by mutableStateOf(Offset.Zero)
        private set

    val isZoomed: Boolean get() = zoom > 1.01f

    /** The fitted-board point under screen [point]. */
    fun toContent(point: Offset): Offset = (point - pan) / zoom

    /** One pinch step: scale by [zoomChange] about [centroid], then drag by [panChange]. */
    fun transform(centroid: Offset, panChange: Offset, zoomChange: Float) {
        val target = (zoom * zoomChange).coerceIn(1f, maxZoom)
        // The board point under the fingers stays under them.
        val anchored = centroid - (centroid - pan) * (target / zoom)
        set(target, anchored + panChange)
    }

    fun set(zoom: Float, pan: Offset) {
        this.zoom = zoom.coerceIn(1f, maxZoom)
        this.pan = clamp(this.zoom, pan)
    }

    /**
     * The pan that brings [cell] back into view, a margin away from the edges,
     * or null when it shows already.
     */
    fun panToShow(cell: Position): Offset? {
        if (!isZoomed) return null
        val size = metrics.tile * zoom
        val margin = size * FOLLOW_MARGIN
        val topLeft = metrics.topLeft(cell.x.toFloat(), cell.y.toFloat()) * zoom + pan
        fun shift(start: Float, view: Float) = when {
            start < margin -> margin - start
            start + size > view - margin -> view - margin - (start + size)
            else -> 0f
        }
        val target = clamp(zoom, pan + Offset(shift(topLeft.x, viewport.width), shift(topLeft.y, viewport.height)))
        return target.takeIf { it != pan }
    }

    private fun clamp(zoom: Float, pan: Offset): Offset {
        val left = metrics.origin.x
        val top = metrics.origin.y
        fun axis(value: Float, start: Float, end: Float, view: Float): Float =
            if ((end - start) * zoom <= view) {
                view / 2f - zoom * (start + end) / 2f
            } else {
                value.coerceIn(view - end * zoom, -start * zoom)
            }
        return Offset(
            axis(pan.x, left, left + level.width * metrics.tile, viewport.width),
            axis(pan.y, top, top + (level.height + WALL_DEPTH) * metrics.tile, viewport.height),
        )
    }

    private companion object {
        const val MIN_USEFUL_ZOOM = 1.15f

        /** How close to the edge, in tiles, the hero may walk before the view follows. */
        const val FOLLOW_MARGIN = 1.5f
    }
}
