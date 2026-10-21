package dev.kharioki.nyumbani

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableIntStateOf
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : ComponentActivity() {

    /** Bumped on every resume, so the screen refreshes apps and Continue watching. */
    private val resumeTick = mutableIntStateOf(0)

    /** Bumped when Home is pressed while we're already in front: close overlays, refocus the dock. */
    private val homeTick = mutableIntStateOf(0)

    private val askForWatchNext = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        resumeTick.intValue++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        if (!WatchNextRepository.hasPermission(this)) {
            // If the TV refuses to show this prompt, grant it over ADB instead (see README).
            runCatching { askForWatchNext.launch(WatchNextRepository.PERMISSION) }
        }

        setContent {
            HomeScreen(
                resumeTick = resumeTick.intValue,
                homeTick = homeTick.intValue,
            )
        }
    }

    override fun onResume() {
        super.onResume()
        resumeTick.intValue++
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME) || intent.action == Intent.ACTION_MAIN) {
            homeTick.intValue++
        }
    }
}
