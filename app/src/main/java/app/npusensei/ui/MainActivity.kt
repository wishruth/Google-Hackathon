package app.npusensei.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import androidx.compose.runtime.collectAsState
import app.npusensei.ui.camera.CameraPreview
import app.npusensei.ui.overlays.BoundingBoxOverlay

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val boxes by viewModel.latestBoundingBoxes.collectAsState()
            Box(Modifier.fillMaxSize()) {
                CameraPreview(viewModel = viewModel, modifier = Modifier.fillMaxSize())
                BoundingBoxOverlay(boxes = boxes, modifier = Modifier.fillMaxSize())
                Text(
                    text = "Backend: ${viewModel.selectedBackend}",
                    color = Color.White,
                    fontSize = 14.sp,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
        }
    }
}
