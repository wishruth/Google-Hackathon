package com.npusensei.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.npusensei.app.ui.theme.NPUSenseiTheme
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
                    // Original App UI
                    NpuSenseiApp()
                    
                    // Temporary Gemma NPU Test Button
                    Button(
                        onClick = {
                            lifecycleScope.launch {
                                gemmaEngine.initializeAndRunTest()
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 64.dp)
                    ) {
                        Text("Test Gemma Inference (NPU)")
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        gemmaEngine.cleanup()
    }
}
