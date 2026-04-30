package com.npusensei.app

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.npusensei.app.ui.theme.NPUSenseiTheme
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var gemmaEngine: GemmaReasoningEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        gemmaEngine = GemmaReasoningEngine(this)

        enableEdgeToEdge()
        setContent {
            NPUSenseiTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    NpuSenseiApp()

                    Button(
                        onClick = { testGemmaInference() },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 64.dp)
                    ) {
                        Text("Test Gemma 4 E2B (NPU)")
                    }
                }
            }
        }
    }

    private fun testGemmaInference() {
        lifecycleScope.launch {
            val config = GemmaModelConfig.bestAvailable(this@MainActivity)
                ?: GemmaModelConfig.GEMMA4_E2B_NPU

            Log.i(TAG, "Using model: ${config.name}")

            val initResult = gemmaEngine.initialize(config)
            if (initResult.isFailure) {
                Log.e(TAG, "Init failed: ${initResult.exceptionOrNull()?.message}")
                return@launch
            }

            val metrics = initResult.getOrThrow()
            Log.i(TAG, "Engine ready on ${metrics.activeBackend} in ${metrics.initTimeMs}ms")

            val testPrompt = "List 3 steps to wire an LED to a Raspberry Pi GPIO pin"
            Log.i(TAG, "Prompt: $testPrompt")

            val startTime = System.currentTimeMillis()
            var firstToken = true

            gemmaEngine.sendMessage(testPrompt)
                .onStart { Log.i(TAG, "Stream started...") }
                .catch { e -> Log.e(TAG, "Inference error: ${e.message}", e) }
                .onCompletion { Log.i(TAG, "Inference complete.") }
                .collect { chunk ->
                    if (firstToken) {
                        Log.i(TAG, "First-token latency: ${System.currentTimeMillis() - startTime}ms")
                        firstToken = false
                    }
                    Log.i(TAG, "Gemma: $chunk")
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
