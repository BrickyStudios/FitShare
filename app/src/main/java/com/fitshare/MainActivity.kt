package com.fitshare

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.fitshare.data.WorkoutState
import com.fitshare.data.WorkoutViewModel
import com.fitshare.export.ShareHelper
import com.fitshare.export.StoryRenderer
import com.fitshare.ui.screens.EditorScreen
import com.fitshare.ui.screens.ImportScreen
import com.fitshare.ui.theme.FitShareTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            FitShareTheme {
                val navController = rememberNavController()
                val workoutViewModel: WorkoutViewModel = viewModel()
                val context = LocalContext.current
                val scope = rememberCoroutineScope()

                // Handle incoming intent (Gadgetbridge sharing directly to FitShare)
                LaunchedEffect(Unit) {
                    handleIncomingIntent(intent, workoutViewModel)
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = "import",
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable("import") {
                            ImportScreen(
                                viewModel = workoutViewModel,
                                onWorkoutLoaded = { navController.navigate("editor") }
                            )
                        }
                        composable("editor") {
                            val state by workoutViewModel.state.collectAsState()
                            val backgroundUri by workoutViewModel.backgroundUri.collectAsState()

                            if (state !is WorkoutState.Success) {
                                navController.navigate("import") { popUpTo("import") { inclusive = true } }
                                return@composable
                            }

                            EditorScreen(
                                viewModel = workoutViewModel,
                                onShare = {
                                    val workout = (state as WorkoutState.Success).workout
                                    scope.launch {
                                        try {
                                            val bitmap = StoryRenderer.renderToBitmap(
                                                context, workout, backgroundUri
                                            )
                                            // Save to gallery
                                            ShareHelper.saveBitmapToGallery(context, bitmap)
                                            // Share
                                            val shareUri = ShareHelper.saveBitmapForSharing(context, bitmap)
                                            if (shareUri != null) {
                                                ShareHelper.shareImage(
                                                    context, shareUri,
                                                    workout.activityType.labelDe
                                                )
                                            } else {
                                                Toast.makeText(context, "Fehler beim Speichern", Toast.LENGTH_SHORT).show()
                                            }
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Fehler: ${e.message}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                },
                                onBack = {
                                    workoutViewModel.reset()
                                    navController.navigate("import") { popUpTo("import") { inclusive = true } }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun handleIncomingIntent(intent: Intent?, viewModel: WorkoutViewModel) {
        intent ?: return
        val uri = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> intent.getParcelableExtra(Intent.EXTRA_STREAM)
            else -> null
        }
        uri?.let { viewModel.loadFromUri(it) }
    }
}
