package com.npusensei.app

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

data class BoundingBox(val x: Float, val y: Float, val width: Float, val height: Float)

data class StepInstruction(val stepNumber: Int, val instruction: String, val targetPins: List<Int>)

@Composable
fun EfficientDetCameraScreen() {
    NpuSenseiArScreen()
}

@Composable
fun NpuSenseiArScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    var hasCameraPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var currentStepIndex by remember { mutableIntStateOf(0) }
    val steps = remember {
        listOf(
            StepInstruction(1, "Connect Pin 1 (3.3V) to resistor", listOf(1)),
            StepInstruction(2, "Connect Pin 6 (GND) to the ground rail", listOf(6)),
            StepInstruction(3, "Verify the resistor is seated firmly", listOf(1, 6)),
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    DisposableEffect(hasCameraPermission) {
        if (!hasCameraPermission) {
            onDispose { }
        } else {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            val mainExecutor = ContextCompat.getMainExecutor(context)
            cameraProviderFuture.addListener(
                {
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                    )
                },
                mainExecutor,
            )
            onDispose {
                ProcessCameraProvider.getInstance(context).get().unbindAll()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        if (hasCameraPermission) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize(),
            )
            GpioArOverlay(
                detectedBox = BoundingBox(x = 0.24f, y = 0.34f, width = 0.52f, height = 0.26f),
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = "Camera permission is required",
                color = Color.White,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        TopStatusBar(
            boardFound = hasCameraPermission,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp),
        )

        BottomInstructionPanel(
            step = steps[currentStepIndex],
            onNextStep = { currentStepIndex = (currentStepIndex + 1) % steps.size },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 18.dp),
        )
    }
}

@Composable
private fun TopStatusBar(boardFound: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "NPU-Sensei",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
        )
        StatusChip(boardFound = boardFound)
    }
}

@Composable
private fun StatusChip(boardFound: Boolean) {
    val chipColor = if (boardFound) AccentGreen else Color(0xFFFFD54F)
    val label = if (boardFound) "Board Found" else "Detecting..."

    Surface(
        color = Color.Black.copy(alpha = 0.58f),
        shape = CircleShape,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Canvas(
                modifier = Modifier
                    .padding(end = 7.dp)
                    .size(10.dp),
            ) {
                drawCircle(color = chipColor, radius = 5.dp.toPx())
            }
            Text(
                text = label,
                color = chipColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun GpioArOverlay(detectedBox: BoundingBox, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val box = Rect(
            left = detectedBox.x * size.width,
            top = detectedBox.y * size.height,
            right = (detectedBox.x + detectedBox.width) * size.width,
            bottom = (detectedBox.y + detectedBox.height) * size.height,
        )
        val pin1 = Offset(box.left + box.width * 0.18f, box.top + box.height * 0.28f)
        val pin6 = Offset(box.left + box.width * 0.50f, box.top + box.height * 0.72f)

        drawRoundRect(
            color = AccentGreen.copy(alpha = 0.14f),
            topLeft = Offset(box.left, box.top),
            size = Size(box.width, box.height),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(18.dp.toPx(), 18.dp.toPx()),
        )
        drawRoundRect(
            color = AccentGreen,
            topLeft = Offset(box.left, box.top),
            size = Size(box.width, box.height),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(18.dp.toPx(), 18.dp.toPx()),
            style = Stroke(width = 3.dp.toPx()),
        )
        drawPinTarget(pin1, "Pin 1", Offset(pin1.x - 115.dp.toPx(), pin1.y - 92.dp.toPx()))
        drawPinTarget(pin6, "Pin 6", Offset(pin6.x + 110.dp.toPx(), pin6.y + 88.dp.toPx()))
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPinTarget(
    target: Offset,
    label: String,
    labelAnchor: Offset,
) {
    drawLine(
        color = AccentGreen,
        start = labelAnchor,
        end = target,
        strokeWidth = 4.dp.toPx(),
        cap = StrokeCap.Round,
    )
    val angle = atan2(target.y - labelAnchor.y, target.x - labelAnchor.x)
    val arrowLength = 18.dp.toPx()
    val arrowAngle = 0.62f
    drawLine(
        color = AccentGreen,
        start = target,
        end = Offset(target.x - arrowLength * cos(angle - arrowAngle), target.y - arrowLength * sin(angle - arrowAngle)),
        strokeWidth = 4.dp.toPx(),
        cap = StrokeCap.Round,
    )
    drawLine(
        color = AccentGreen,
        start = target,
        end = Offset(target.x - arrowLength * cos(angle + arrowAngle), target.y - arrowLength * sin(angle + arrowAngle)),
        strokeWidth = 4.dp.toPx(),
        cap = StrokeCap.Round,
    )
    drawCircle(color = AccentGreen, radius = 8.dp.toPx(), center = target)
    drawCircle(color = Color.Black.copy(alpha = 0.65f), radius = 28.dp.toPx(), center = labelAnchor)
}

@Composable
private fun BottomInstructionPanel(
    step: StepInstruction,
    onNextStep: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color.Black.copy(alpha = 0.72f),
        shape = RoundedCornerShape(28.dp),
        shadowElevation = 12.dp,
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    slideInVertically(animationSpec = tween(260)) { it } togetherWith
                        slideOutVertically(animationSpec = tween(220)) { -it }
                },
                label = "instruction-step",
            ) { currentStep ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Step ${currentStep.stepNumber}: ${currentStep.instruction}",
                        color = Color.White,
                        fontSize = 19.sp,
                        lineHeight = 24.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Tap when done",
                        color = Color.White.copy(alpha = 0.72f),
                        fontSize = 15.sp,
                    )
                }
            }
            Button(
                onClick = onNextStep,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentGreen,
                    contentColor = Color.Black,
                ),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "Next Step",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
            }
        }
    }
}

private val AccentGreen = Color(0xFF00FF88)
