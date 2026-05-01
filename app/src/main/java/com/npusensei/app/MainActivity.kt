package com.npusensei.app

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.npusensei.app.ui.camera.CoachCameraScreen
import com.npusensei.app.ui.home.CoachHomeScreen
import com.npusensei.app.ui.theme.NPUSenseiTheme
import com.npusensei.app.viewmodel.CoachViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var gemmaEngine: GemmaReasoningEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        gemmaEngine = GemmaReasoningEngine(this)

        lifecycleScope.launch {
            val config = GemmaModelConfig.bestAvailable(this@MainActivity)
                ?: GemmaModelConfig.GEMMA4_E2B_NPU
            Log.i(TAG, "Initializing model: ${config.name}")
            val result = gemmaEngine.initialize(config)
            result.fold(
                onSuccess = {
                    Log.i(TAG, "Engine ready on ${it.activeBackend} in ${it.initTimeMs}ms")
                    gemmaEngine.startConversation(PromptTemplates.SYSTEM_PROMPT)
                },
                onFailure = { Log.e(TAG, "Init failed: ${it.message}") },
            )
        }

        enableEdgeToEdge()
        setContent {
            NPUSenseiTheme {
                var route by remember { mutableStateOf<AppRoute>(AppRoute.CoachHome) }
                val coachVm: CoachViewModel = viewModel(factory = CoachViewModel.Factory)

                when (route) {
                    AppRoute.CoachHome -> CoachHomeScreen(onPick = { bp ->
                        coachVm.selectBlueprint(bp)
                        route = AppRoute.CoachCamera
                    })
                    AppRoute.CoachCamera -> CoachCameraScreen(viewModel = coachVm)
                    AppRoute.LegacyGemma -> NpuSenseiApp(gemmaEngine = gemmaEngine)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        gemmaEngine.cleanup()
    }

    companion object {
        private const val TAG = "NPUSensei"
    }
}

private sealed interface AppRoute {
    data object CoachHome : AppRoute
    data object CoachCamera : AppRoute
    data object LegacyGemma : AppRoute
}
