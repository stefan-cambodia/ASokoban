package dev.stefan.sokoban

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.stefan.sokoban.ui.SokobanApp

class MainActivity : ComponentActivity() {

    private val container get() = (application as SokobanApplication).container

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { SokobanApp() }
    }

    // The audio thread only lives while the game is visible.
    override fun onStart() {
        super.onStart()
        container.sounds.start()
    }

    override fun onStop() {
        container.sounds.stop()
        super.onStop()
    }
}
