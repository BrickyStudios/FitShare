package com.fitshare

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.core.content.IntentCompat
import com.fitshare.data.WorkoutViewModel
import com.fitshare.ui.screens.EditorScreen
import com.fitshare.ui.screens.ImportScreen
import com.fitshare.ui.theme.FitShareTheme
import com.fitshare.ui.theme.Ink
import com.fitshare.ui.theme.uiSpring

class MainActivity : ComponentActivity() {

    private val vm: WorkoutViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)

        setContent {
            FitShareTheme {
                val state by vm.state.collectAsState()
                AnimatedContent(
                    targetState = state.hasData,
                    modifier = Modifier.fillMaxSize().background(Ink),
                    transitionSpec = {
                        // editor comes in from the right and leaves the same way it came
                        val slide = spring<IntOffset>(stiffness = 400f)
                        val dir = if (targetState) 1 else -1
                        (slideInHorizontally(slide) { it / 4 * dir } + fadeIn(uiSpring())) togetherWith
                            (slideOutHorizontally(slide) { -it / 4 * dir } + fadeOut(uiSpring()))
                    },
                    label = "screen"
                ) { editing ->
                    if (editing) EditorScreen(state, vm) else ImportScreen(state, vm::open)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** Files shared from Gadgetbridge / OpenTracks, or opened with "Öffnen mit". */
    private fun handleIntent(intent: Intent?) {
        intent ?: return
        val uris: List<Uri> = when (intent.action) {
            Intent.ACTION_VIEW -> listOfNotNull(intent.data)
            Intent.ACTION_SEND -> listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
            Intent.ACTION_SEND_MULTIPLE ->
                IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
            else -> emptyList()
        }
        vm.open(uris)
    }
}
