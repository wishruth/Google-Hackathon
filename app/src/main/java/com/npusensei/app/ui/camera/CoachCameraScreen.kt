package com.npusensei.app.ui.camera

import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.npusensei.app.camera.CameraController
import com.npusensei.app.ui.coach.CoachPanel
import com.npusensei.app.viewmodel.CoachViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CoachCameraScreen(
    viewModel: CoachViewModel,
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
            CameraSurface(viewModel)
        }
    }
}

@Composable
private fun CameraSurface(viewModel: CoachViewModel) {
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

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize(),
        )
        CoachDetectionOverlay(
            detections = detections,
            frameSize = frameSize,
            highlightBox = state.highlightBox,
            highlightPx = state.highlightPx,
            nextStepLabel = state.currentStep?.highlight?.label,
            modifier = Modifier.fillMaxSize(),
        )
        CoachPanel(
            state = state,
            onPrev = viewModel::previousStep,
            onNext = viewModel::nextStep,
            onAsk = { viewModel.askCoach("What should I do right now?") },
            modifier = Modifier.align(Alignment.BottomCenter),
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
