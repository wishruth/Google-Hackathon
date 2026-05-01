package com.npusensei.app.ui.camera

import android.os.Debug
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.npusensei.app.camera.CameraController
import com.npusensei.app.ui.coach.CoachPanel
import com.npusensei.app.viewmodel.CoachViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import kotlinx.coroutines.delay

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CoachCameraScreen(
    viewModel: CoachViewModel,
    onComplete: () -> Unit = {},
) {
    val cameraPerm = rememberPermissionState(android.Manifest.permission.CAMERA)
    LaunchedEffect(Unit) {
        if (!cameraPerm.status.isGranted) cameraPerm.launchPermissionRequest()
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (!cameraPerm.status.isGranted) {
            PermissionGate(
                onRequest = { cameraPerm.launchPermissionRequest() },
                shouldShowRationale = cameraPerm.status.shouldShowRationale,
            )
        } else {
            CameraSurface(viewModel, onComplete)
        }
    }
}

@Composable
private fun CameraSurface(viewModel: CoachViewModel, onComplete: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val controller = remember { CameraController(context) }
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.PERFORMANCE
        }
    }

    DisposableEffect(Unit) {
        controller.bind(lifecycleOwner, previewView, viewModel.analyzer)
        onDispose { controller.shutdown() }
    }

    val detections by viewModel.analyzer.liveDetections.collectAsStateWithLifecycle()
    val frameSize by viewModel.analyzer.frameSize.collectAsStateWithLifecycle()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val yoloMs by viewModel.analyzer.yoloLatencyMs.collectAsStateWithLifecycle()
    val yoloFps by viewModel.analyzer.yoloFps.collectAsStateWithLifecycle()
    var showOverlay by remember { mutableStateOf(true) }
    var showBenchmark by remember { mutableStateOf(false) }

    var memoryMb by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(showBenchmark) {
        while (showBenchmark) {
            val mi = Debug.MemoryInfo()
            Debug.getMemoryInfo(mi)
            memoryMb = mi.totalPss / 1024f
            delay(1000)
        }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize(),
        )
        if (showOverlay) {
            CoachDetectionOverlay(
                detections = detections,
                frameSize = frameSize,
                highlightBox = state.highlightBox,
                highlightPx = state.highlightPx,
                nextStepLabel = state.currentStep?.highlight?.label,
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (showBenchmark) {
            BenchmarkOverlay(
                yoloMs = yoloMs,
                yoloFps = yoloFps,
                gemmaMs = state.coachLatencyMs,
                gemmaThinking = state.coachThinking,
                memoryMb = memoryMb,
                detectionCount = detections.size,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(start = 12.dp, top = 8.dp),
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 8.dp, end = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                color = if (showBenchmark) Color(0xFF009E5E).copy(alpha = 0.7f)
                else Color.Black.copy(alpha = 0.45f),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
            ) {
                IconButton(
                    onClick = { showBenchmark = !showBenchmark },
                    modifier = Modifier.size(44.dp),
                ) {
                    Icon(
                        Icons.Filled.Analytics,
                        contentDescription = "Toggle benchmark",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Surface(
                color = Color.Black.copy(alpha = 0.45f),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
            ) {
                IconButton(
                    onClick = { viewModel.clearHighlights() },
                    modifier = Modifier.size(44.dp),
                ) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = "Clear highlights",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Surface(
                color = Color.Black.copy(alpha = 0.45f),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
            ) {
                IconButton(
                    onClick = { showOverlay = !showOverlay },
                    modifier = Modifier.size(44.dp),
                ) {
                    Icon(
                        imageVector = if (showOverlay) Icons.Filled.Visibility
                        else Icons.Filled.VisibilityOff,
                        contentDescription = "Toggle detection overlay",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }

        CoachPanel(
            state = state,
            onPrev = viewModel::previousStep,
            onNext = viewModel::nextStep,
            onAsk = { viewModel.askCoach("What should I do right now?") },
            onAskQuestion = { question -> viewModel.askCoach(question) },
            onGrade = { viewModel.askCoach("Based on what you can see on camera, is the current step complete? Grade my work — tell me if I did this step correctly and if I'm ready to move on to the next step.") },
            onComplete = onComplete,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun BenchmarkOverlay(
    yoloMs: Long,
    yoloFps: Float,
    gemmaMs: Long,
    gemmaThinking: Boolean,
    memoryMb: Float,
    detectionCount: Int,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = Color.Black.copy(alpha = 0.7f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFF009E5E).copy(alpha = 0.5f)),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                "BENCHMARK",
                color = Color(0xFF009E5E),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.5.sp,
            )
            Spacer(Modifier.height(8.dp))

            BenchRow("YOLO latency", "${yoloMs}ms", Color(0xFF4FC3F7))
            BenchRow("YOLO fps", "${"%.1f".format(yoloFps)}", Color(0xFF4FC3F7))
            BenchRow("Objects", "$detectionCount", Color(0xFF4FC3F7))

            Spacer(Modifier.height(6.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.15f))
            )
            Spacer(Modifier.height(6.dp))

            BenchRow(
                "Gemma latency",
                if (gemmaThinking) "thinking…" else "${gemmaMs}ms",
                Color(0xFFFFB74D),
            )
            BenchRow("Memory", "${"%.0f".format(memoryMb)} MB", Color(0xFFE0E0E0))
        }
    }
}

@Composable
private fun BenchRow(label: String, value: String, valueColor: Color) {
    Row(
        Modifier.padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = Color(0xFFAAAAAA),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            value,
            color = valueColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun PermissionGate(onRequest: () -> Unit, shouldShowRationale: Boolean) {
    Surface(
        Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Camera permission needed",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = if (shouldShowRationale) {
                    "We use the camera to see your breadboard and guide you " +
                        "through wiring it up. Nothing leaves your device."
                } else {
                    "Tap Allow to give access to your camera."
                },
                modifier = Modifier
                    .padding(vertical = 16.dp)
                    .fillMaxWidth(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onRequest) { Text("Allow camera") }
        }
    }
}
