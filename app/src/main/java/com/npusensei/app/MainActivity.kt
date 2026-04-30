package com.npusensei.app

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.npusensei.app.ui.theme.NPUSenseiTheme
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
                onSuccess = { Log.i(TAG, "Engine ready on ${it.activeBackend} in ${it.initTimeMs}ms") },
                onFailure = { Log.e(TAG, "Init failed: ${it.message}") },
            )
        }

        enableEdgeToEdge()
        setContent {
            NPUSenseiTheme {
                NpuSenseiApp(gemmaEngine = gemmaEngine)
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
