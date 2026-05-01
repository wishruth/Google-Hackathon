package com.npusensei.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors

private val AccentGreen = Color(0xFF00FF88)
private val ErrorRed = Color(0xFFFF6B6B)
private val MutedText = Color.White.copy(alpha = 0.62f)
private val AskBlue = Color(0xFF64B5F6)

// ── App State ────────────────────────────────────────────────────────────

private sealed class AppPhase {
    data object Camera : AppPhase()
    data object Planning : AppPhase()
    data class PlanReady(val plan: String, val steps: List<String>) : AppPhase()
    data class Guided(val steps: List<CircuitStep>) : AppPhase()
    data object Complete : AppPhase()
}

// ── Main Entry ───────────────────────────────────────────────────────────

@Composable
fun NpuSenseiApp(gemmaEngine: GemmaReasoningEngine) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var phase: AppPhase by remember { mutableStateOf(AppPhase.Camera) }
    var prompt by remember { mutableStateOf("") }
    var planText by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var cameraBindKey by remember { mutableStateOf(0) }
    var imageCapture by remember { mutableStateOf(ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()) }

    val captureExecutor = remember { Executors.newSingleThreadExecutor() }
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCameraPermission = granted
    }

    // Speech recognition
    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull() ?: ""
            if (spoken.isNotBlank()) {
                prompt = spoken
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(hasCameraPermission, cameraBindKey) {
        if (!hasCameraPermission) return@LaunchedEffect
        val cameraProvider = ProcessCameraProvider.getInstance(context).get()
        val newImageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }
        cameraProvider.unbindAll()
        cameraProvider.bindToLifecycle(
            lifecycleOwner,
            CameraSelector.DEFAULT_BACK_CAMERA,
            preview,
            newImageCapture,
        )
        imageCapture = newImageCapture
    }

    fun submitPrompt() {
        if (prompt.isBlank()) return
        val userPrompt = prompt.trim()
        phase = AppPhase.Planning
        planText = ""
        errorMsg = null

        val outputFile = File(context.cacheDir, "plan_capture.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()

        imageCapture.takePicture(
            outputOptions,
            captureExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    scope.launch {
                        val planPrompt = buildPlanPrompt(userPrompt)
                        val chunks = mutableListOf<String>()
                        try {
                            gemmaEngine.sendImageMessage(planPrompt, outputFile.absolutePath)
                                .catch { e -> chunks.add("Error: ${e.message}") }
                                .collect { chunk ->
                                    chunks.add(chunk)
                                    planText = chunks.joinToString("")
                                }
                            val fullPlan = chunks.joinToString("")
                            val steps = parsePlanSteps(fullPlan)
                            phase = AppPhase.PlanReady(fullPlan, steps)
                        } catch (e: Exception) {
                            errorMsg = e.message ?: "Failed to generate plan"
                            phase = AppPhase.Camera
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    errorMsg = "Camera capture failed: ${exception.message}"
                    phase = AppPhase.Camera
                }
            },
        )
    }

    fun launchVoice() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "What do you want to build?")
        }
        speechLauncher.launch(intent)
    }

    // ── UI ────────────────────────────────────────────────────────────────

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Camera visible only during non-guided phases
        if (phase !is AppPhase.Guided) {
            if (hasCameraPermission) {
                AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Camera permission required", color = Color.White)
                }
            }
        }

        when (phase) {
            AppPhase.Camera -> {
                // Top branding
                Surface(
                    color = Color.Black.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = 12.dp),
                ) {
                    Text(
                        text = "NPU-Sensei",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }

                // Bottom prompt input
                PromptInput(
                    prompt = prompt,
                    onPromptChange = { prompt = it },
                    onSubmit = { submitPrompt() },
                    onMic = { launchVoice() },
                    errorMsg = errorMsg,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                )
            }

            AppPhase.Planning -> {
                // Dimmed overlay while planning
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center,
                ) {
                    PlanningOverlay(planText = planText)
                }
            }

            is AppPhase.PlanReady -> {
                val planReady = phase as AppPhase.PlanReady
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.8f)),
                    contentAlignment = Alignment.Center,
                ) {
                    PlanReadyOverlay(
                        plan = planReady.plan,
                        onConfirm = {
                            val circuitSteps = planReady.steps.mapIndexed { idx, text ->
                                CircuitStep(
                                    id = idx + 1,
                                    instruction = text,
                                    detail = "",
                                    verificationPrompt = "Step: \"$text\". " +
                                        "Look at the image. Can you see the components or progress " +
                                        "related to this step? If the relevant parts are visible in " +
                                        "the image, answer YES. Only answer NO if nothing related " +
                                        "to this step is visible at all. " +
                                        "Answer YES or NO, then describe what you see in one sentence.",
                                )
                            }
                            phase = AppPhase.Guided(circuitSteps)
                        },
                        onCancel = {
                            phase = AppPhase.Camera
                        },
                    )
                }
            }

            is AppPhase.Guided -> {
                val guided = phase as AppPhase.Guided
                GuidedCircuitScreen(
                    engine = gemmaEngine,
                    steps = guided.steps,
                    onFinished = { phase = AppPhase.Complete; cameraBindKey++ },
                    onBack = { phase = AppPhase.Camera; prompt = ""; cameraBindKey++ },
                )
            }

            AppPhase.Complete -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.85f)),
                    contentAlignment = Alignment.Center,
                ) {
                    CompleteOverlay(onDone = { phase = AppPhase.Camera; prompt = ""; cameraBindKey++ })
                }
            }
        }
    }
}

// ── Prompt Input ─────────────────────────────────────────────────────────

@Composable
private fun PromptInput(
    prompt: String,
    onPromptChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onMic: () -> Unit,
    errorMsg: String?,
    modifier: Modifier = Modifier,
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color.Black.copy(alpha = 0.78f),
        shape = RoundedCornerShape(24.dp),
        shadowElevation = 12.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "What do you want to build?",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Point the camera at your components, then tell me your goal.",
                color = MutedText,
                fontSize = 13.sp,
            )

            if (errorMsg != null) {
                Text(text = errorMsg, color = ErrorRed, fontSize = 12.sp)
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = prompt,
                    onValueChange = onPromptChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("e.g. Build an LED circuit with a resistor") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = AccentGreen,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                        cursorColor = AccentGreen,
                        focusedPlaceholderColor = Color.White.copy(alpha = 0.35f),
                        unfocusedPlaceholderColor = Color.White.copy(alpha = 0.35f),
                        focusedContainerColor = Color.White.copy(alpha = 0.04f),
                        unfocusedContainerColor = Color.White.copy(alpha = 0.04f),
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(
                        onGo = {
                            keyboardController?.hide()
                            onSubmit()
                        },
                    ),
                )
                Button(
                    onClick = onMic,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AskBlue.copy(alpha = 0.8f),
                        contentColor = Color.White,
                    ),
                    shape = CircleShape,
                    modifier = Modifier.size(48.dp),
                ) {
                    Text("🎤", fontSize = 18.sp)
                }
            }

            Button(
                onClick = {
                    keyboardController?.hide()
                    onSubmit()
                },
                enabled = prompt.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentGreen,
                    contentColor = Color(0xFF001F12),
                    disabledContainerColor = Color.White.copy(alpha = 0.08f),
                    disabledContentColor = Color.White.copy(alpha = 0.3f),
                ),
            ) {
                Text("Plan My Build", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}

// ── Planning Overlay (streaming) ─────────────────────────────────────────

@Composable
private fun PlanningOverlay(planText: String) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        color = Color(0xF0101418),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = AccentGreen,
                    strokeWidth = 2.5.dp,
                )
                Text(
                    "Creating your build plan...",
                    color = AccentGreen,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            if (planText.isNotEmpty()) {
                Text(
                    text = planText,
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .height(300.dp),
                )
            } else {
                Text(
                    "Analyzing your workspace and components...",
                    color = MutedText,
                    fontSize = 13.sp,
                )
            }
        }
    }
}

// ── Plan Ready Overlay ───────────────────────────────────────────────────

@Composable
private fun PlanReadyOverlay(
    plan: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        color = Color(0xF0101418),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Your Build Plan", color = AccentGreen, fontSize = 20.sp, fontWeight = FontWeight.Bold)

            Surface(
                color = Color.White.copy(alpha = 0.04f),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(
                    text = plan,
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    modifier = Modifier
                        .padding(14.dp)
                        .verticalScroll(rememberScrollState())
                        .height(280.dp),
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onCancel,
                    modifier = Modifier.weight(0.5f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.1f),
                        contentColor = Color.White,
                    ),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text("Redo", modifier = Modifier.padding(vertical = 4.dp))
                }
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentGreen,
                        contentColor = Color(0xFF001F12),
                    ),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text("Start Building", fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 4.dp))
                }
            }
        }
    }
}

// ── Complete Overlay ─────────────────────────────────────────────────────

@Composable
private fun CompleteOverlay(onDone: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        color = Color(0xF0101418),
        shape = RoundedCornerShape(28.dp),
    ) {
        Column(
            modifier = Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Done!", fontSize = 48.sp)
            Text(
                "Your circuit is built. Connect power and test it out.",
                color = Color.White,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
            )
            Button(
                onClick = onDone,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentGreen,
                    contentColor = Color(0xFF001F12),
                ),
            ) {
                Text("Build Something Else", fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ── Helpers ──────────────────────────────────────────────────────────────

private fun buildPlanPrompt(userGoal: String): String =
    "The user wants to: $userGoal\n\n" +
        "Look at the image of their workspace. Identify visible components.\n" +
        "Then create a numbered step-by-step build plan using those components.\n\n" +
        "Rules:\n" +
        "- Do NOT state how many steps there are. Just list them.\n" +
        "- One sentence per step.\n" +
        "- Be direct and specific.\n" +
        "- NEVER say 'refer to a manual' or 'consult a datasheet'.\n\n" +
        "Format:\n" +
        "COMPONENTS: [list what you see]\n" +
        "PLAN:\n" +
        "1. [step]\n" +
        "2. [step]\n" +
        "..."

private fun parsePlanSteps(plan: String): List<String> {
    val lines = plan.lines()
    val steps = mutableListOf<String>()
    val stepPattern = Regex("""^\s*(\d+)[.)]\s*(.+)""")
    for (line in lines) {
        val match = stepPattern.find(line)
        if (match != null) {
            steps.add(match.groupValues[2].trim())
        }
    }
    if (steps.isEmpty()) {
        steps.add("Follow the plan above")
    }
    return steps
}
