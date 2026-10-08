package dev.stefan.sokoban.ui.board3d

import android.app.ActivityManager
import android.content.Context
import android.util.Log

/**
 * Whether this device can draw the 3D board. Filament needs OpenGL ES 3.0,
 * which a few Android 8 phones lack; and should an engine still fail to
 * start, the board stays 2D for the rest of the run.
 */
object Board3dSupport {

    @Volatile
    private var failed = false

    fun isSupported(context: Context): Boolean {
        if (failed) return false
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return manager.deviceConfigurationInfo.reqGlEsVersion >= GLES_3_0
    }

    /** A Filament engine ready to draw, or null when this device cannot have one. */
    internal fun start(context: Context): FilamentHost? {
        if (!isSupported(context)) return null
        return try {
            FilamentHost(context)
        } catch (e: Exception) {
            fail(e)
        } catch (e: LinkageError) {
            // The native libraries did not load.
            fail(e)
        }
    }

    private fun fail(e: Throwable): FilamentHost? {
        Log.w(TAG, "3D board unavailable, drawing it in 2D", e)
        failed = true
        return null
    }

    private const val GLES_3_0 = 0x30000
    private const val TAG = "Board3d"
}
