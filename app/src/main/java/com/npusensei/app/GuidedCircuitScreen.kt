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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
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
private val WarningYellow = Color(0xFFFFD54F)
private val AskBlue = Color(0xFF64B5F6)

private sealed class VerifyState {
    data object Idle : VerifyState()
    data object Capturing : VerifyState()
    data object Analyzing : VerifyState()
    data class Result(val outcome: VerificationOutcome) : VerifyState()
}

private sealed class AskState {
    data object Hidden : AskState()
    data object Listening : AskState()
    data object Thinking : AskState()
    data class Answer(val question: String, val response: String) : AskState()
    data class Error(val message: String) : AskState()
}

@Composable
fun GuidedCircuitScreen(
    engine: GemmaReasoningEngine,
    steps: List<CircuitStep> = CircuitProjects.LED_BASIC,
    onFinished: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val verifier = remember { StepVerifier(engine) }
    var currentStepIndex by remember { mutableIntStateOf(0) }
    var verifyState: VerifyState by remember { mutableStateOf(VerifyState.Idle) }
    var askState: AskState by remember { mutableStateOf(AskState.Hidden) }
    var typedQuestion by remember { mutableStateOf("") }

    val currentStep = steps[currentStepIndex]
    val isLastStep = currentStepIndex == steps.size - 1

    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }
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

    // Speech recognition launcher
    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull() ?: ""
            if (spoken.isNotBlank()) {
                askState = AskState.Thinking
                captureAndAsk(
                    question = spoken,
                    imageCapture = imageCapture,
                    captureExecutor = captureExecutor,
                    context = context,
                    currentStep = currentStep,
                    engine = engine,
                    scope = scope,
                    onStateChange = { askState = it },
                )
            } else {
                askState = AskState.Hidden
            }
        } else {
            askState = AskState.Hidden
        }
    }

    fun launchVoiceInput() {
        askState = AskState.Listening
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Ask about your circuit...")
        }
        speechLauncher.launch(intent)
    }

    fun submitTypedQuestion() {
        if (typedQuestion.isBlank()) return
        val question = typedQuestion.trim()
        typedQuestion = ""
        askState = AskState.Thinking
        captureAndAsk(
            question = question,
            imageCapture = imageCapture,
            captureExecutor = captureExecutor,
            context = context,
            currentStep = currentStep,
            engine = engine,
            scope = scope,
            onStateChange = { askState = it },
        )
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(hasCameraPermission) {
        if (!hasCameraPermission) return@LaunchedEffect
        val cameraProvider = ProcessCameraProvider.getInstance(context).get()
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }
        cameraProvider.unbindAll()
        cameraProvider.bindToLifecycle(
            lifecycleOwner,
            CameraSelector.DEFAULT_BACK_CAMERA,
            preview,
            imageCapture,
        )
    }

    fun captureAndVerify() {
        verifyState = VerifyState.Capturing
        val outputFile = File(context.cacheDir, "verify_step_${currentStep.id}.jpg")

        val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()
        imageCapture.takePicture(
            outputOptions,
            captureExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    verifyState = VerifyState.Analyzing
                    scope.launch {
                        val outcome = verifier.verify(currentStep, outputFile.absolutePath)
                        verifyState = VerifyState.Result(outcome)
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    verifyState = VerifyState.Result(
                        VerificationOutcome(VerificationResult.ERROR, "Capture failed: ${exception.message}")
                    )
                }
            },
        )
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
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Camera permission is required", color = Color.White, fontSize = 16.sp)
            }
        }

        // Top bar
        TopBar(
            stepIndex = currentStepIndex,
            totalSteps = steps.size,
            onBack = onBack,
            onAsk = { if (askState is AskState.Hidden) launchVoiceInput() },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        )

        // Ask overlay (shows when asking a question)
        AnimatedVisibility(
            visible = askState !is AskState.Hidden,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200)),
            modifier = Modifier.align(Alignment.Center),
        ) {
            AskOverlay(
                askState = askState,
                typedQuestion = typedQuestion,
                onTypedQuestionChange = { typedQuestion = it },
                onSubmitTyped = { submitTypedQuestion() },
                onDismiss = { askState = AskState.Hidden },
                onRetryVoice = { launchVoiceInput() },
            )
        }

        // Bottom panel (hidden while asking)
        AnimatedVisibility(
            visible = askState is AskState.Hidden,
            enter = slideInVertically(tween(200)) { it },
            exit = slideOutVertically(tween(200)) { it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            BottomPanel(
                step = currentStep,
                verifyState = verifyState,
                isLastStep = isLastStep,
                onCheck = { captureAndVerify() },
                onNext = {
                    verifyState = VerifyState.Idle
                    if (isLastStep) {
                        onFinished()
                    } else {
                        currentStepIndex++
                    }
                },
                onRetry = { verifyState = VerifyState.Idle },
            )
        }
    }
}

private fun captureAndAsk(
    question: String,
    imageCapture: ImageCapture,
    captureExecutor: java.util.concurrent.ExecutorService,
    context: android.content.Context,
    currentStep: CircuitStep,
    engine: GemmaReasoningEngine,
    scope: kotlinx.coroutines.CoroutineScope,
    onStateChange: (AskState) -> Unit,
) {
    val outputFile = File(context.cacheDir, "ask_frame.jpg")
    val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()

    imageCapture.takePicture(
        outputOptions,
        captureExecutor,
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                scope.launch {
                    val prompt = "The user is on step ${currentStep.id}: \"${currentStep.instruction}\". " +
                        "They are asking: $question\n\n" +
                        "Look at the image of their workspace and answer their question. " +
                        "Be concise and helpful. If you can see their circuit, reference what you observe."

                    val chunks = mutableListOf<String>()
                    try {
                        engine.sendImageMessage(prompt, outputFile.absolutePath)
                            .catch { e -> chunks.add("Error: ${e.message}") }
                            .collect { chunk -> chunks.add(chunk) }
                        onStateChange(AskState.Answer(question, chunks.joinToString("")))
                    } catch (e: Exception) {
                        onStateChange(AskState.Error(e.message ?: "Failed to get response"))
                    }
                }
            }

            override fun onError(exception: ImageCaptureException) {
                onStateChange(AskState.Error("Capture failed: ${exception.message}"))
            }
        },
    )
}

@Composable
private fun TopBar(
    stepIndex: Int,
    totalSteps: Int,
    onBack: () -> Unit,
    onAsk: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            onClick = onBack,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Black.copy(alpha = 0.5f),
                contentColor = Color.White,
            ),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text("← Back", fontSize = 13.sp)
        }

        Surface(
            color = Color.Black.copy(alpha = 0.58f),
            shape = CircleShape,
        ) {
            Text(
                text = "Step ${stepIndex + 1} / $totalSteps",
                color = AccentGreen,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }

        Button(
            onClick = onAsk,
            colors = ButtonDefaults.buttonColors(
                containerColor = AskBlue.copy(alpha = 0.85f),
                contentColor = Color.White,
            ),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text("🎤 Ask", fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun AskOverlay(
    askState: AskState,
    typedQuestion: String,
    onTypedQuestionChange: (String) -> Unit,
    onSubmitTyped: () -> Unit,
    onDismiss: () -> Unit,
    onRetryVoice: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        color = Color(0xF0101418),
        shape = RoundedCornerShape(24.dp),
        shadowElevation = 16.dp,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            when (askState) {
                AskState.Hidden -> { /* shouldn't render */ }
                AskState.Listening -> {
                    Text("🎤 Listening...", color = AskBlue, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Speak your question about the circuit",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 14.sp,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Or type instead:", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = typedQuestion,
                            onValueChange = onTypedQuestionChange,
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Type a question...") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = AskBlue,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                cursorColor = AskBlue,
                                focusedPlaceholderColor = Color.White.copy(alpha = 0.4f),
                                unfocusedPlaceholderColor = Color.White.copy(alpha = 0.4f),
                                focusedContainerColor = Color.White.copy(alpha = 0.05f),
                                unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
                            ),
                        )
                        Button(
                            onClick = onSubmitTyped,
                            enabled = typedQuestion.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AskBlue,
                                contentColor = Color.White,
                            ),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text("→", fontWeight = FontWeight.Bold)
                        }
                    }
                }
                AskState.Thinking -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = AskBlue,
                            strokeWidth = 2.dp,
                        )
                        Text("Gemma is thinking...", color = AskBlue, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        "Analyzing your question with the camera view",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 13.sp,
                    )
                }
                is AskState.Answer -> {
                    Text("Q: ${askState.question}", color = AskBlue, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Surface(
                        color = Color.White.copy(alpha = 0.05f),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(
                            text = askState.response,
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            modifier = Modifier
                                .padding(12.dp)
                                .verticalScroll(rememberScrollState()),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentGreen,
                                contentColor = Color.Black,
                            ),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Text("Got it", fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = onRetryVoice,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AskBlue.copy(alpha = 0.2f),
                                contentColor = AskBlue,
                            ),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Text("🎤 Ask More")
                        }
                    }
                }
                is AskState.Error -> {
                    Text("Something went wrong", color = ErrorRed, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(askState.message, color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White.copy(alpha = 0.1f),
                                contentColor = Color.White,
                            ),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Text("Dismiss")
                        }
                        Button(
                            onClick = onRetryVoice,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AskBlue,
                                contentColor = Color.White,
                            ),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Text("🎤 Try Again")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomPanel(
    step: CircuitStep,
    verifyState: VerifyState,
    isLastStep: Boolean,
    onCheck: () -> Unit,
    onNext: () -> Unit,
    onRetry: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.Black.copy(alpha = 0.82f),
        shape = RoundedCornerShape(24.dp),
        shadowElevation = 12.dp,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Step instruction
            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    slideInVertically(tween(240)) { it } togetherWith
                        slideOutVertically(tween(200)) { -it }
                },
                label = "step-instruction",
            ) { currentStep ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Surface(
                            color = AccentGreen.copy(alpha = 0.15f),
                            shape = CircleShape,
                        ) {
                            Text(
                                text = stepActionIcon(currentStep.instruction),
                                fontSize = 20.sp,
                                modifier = Modifier.padding(8.dp),
                            )
                        }
                        Text(
                            text = currentStep.instruction,
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (currentStep.detail.isNotBlank()) {
                        Text(
                            text = currentStep.detail,
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                        )
                    }
                    if (currentStep.safetyWarning != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(
                            color = WarningYellow.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Text(
                                text = "⚠ ${currentStep.safetyWarning}",
                                color = WarningYellow,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
            }

            // Verification feedback
            when (val state = verifyState) {
                VerifyState.Idle -> { /* nothing */ }
                VerifyState.Capturing -> {
                    VerifyingIndicator("Capturing photo...")
                }
                VerifyState.Analyzing -> {
                    VerifyingIndicator("Analyzing with Gemma...")
                }
                is VerifyState.Result -> {
                    VerificationFeedback(outcome = state.outcome)
                }
            }

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                when (val state = verifyState) {
                    VerifyState.Idle -> {
                        Button(
                            onClick = onCheck,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentGreen,
                                contentColor = Color.Black,
                            ),
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            Text("Check My Work", fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 4.dp))
                        }
                        Button(
                            onClick = onNext,
                            modifier = Modifier.weight(0.6f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White.copy(alpha = 0.1f),
                                contentColor = Color.White,
                            ),
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            Text(if (isLastStep) "Finish" else "Skip →", fontSize = 13.sp, modifier = Modifier.padding(vertical = 4.dp))
                        }
                    }
                    VerifyState.Capturing, VerifyState.Analyzing -> {
                        Button(
                            onClick = {},
                            enabled = false,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                disabledContainerColor = Color.White.copy(alpha = 0.08f),
                                disabledContentColor = Color.White.copy(alpha = 0.4f),
                            ),
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            Text("Checking...", modifier = Modifier.padding(vertical = 4.dp))
                        }
                    }
                    is VerifyState.Result -> {
                        if (state.outcome.result == VerificationResult.COMPLETE) {
                            Button(
                                onClick = onNext,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AccentGreen,
                                    contentColor = Color.Black,
                                ),
                                shape = RoundedCornerShape(16.dp),
                            ) {
                                Text(
                                    if (isLastStep) "Complete! 🎉" else "Next Step →",
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(vertical = 4.dp),
                                )
                            }
                        } else {
                            Button(
                                onClick = onRetry,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AccentGreen,
                                    contentColor = Color.Black,
                                ),
                                shape = RoundedCornerShape(16.dp),
                            ) {
                                Text("Try Again", fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 4.dp))
                            }
                            Button(
                                onClick = onNext,
                                modifier = Modifier.weight(0.6f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White.copy(alpha = 0.1f),
                                    contentColor = Color.White,
                                ),
                                shape = RoundedCornerShape(16.dp),
                            ) {
                                Text(if (isLastStep) "Finish" else "Skip →", fontSize = 13.sp, modifier = Modifier.padding(vertical = 4.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VerifyingIndicator(message: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            color = AccentGreen,
            strokeWidth = 2.dp,
        )
        Text(message, color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
    }
}

private fun stepActionIcon(instruction: String): String {
    val lower = instruction.lowercase()
    return when {
        lower.contains("place") || lower.contains("put") || lower.contains("set") -> "📍"
        lower.contains("insert") || lower.contains("push") -> "⬇️"
        lower.contains("connect") || lower.contains("wire") || lower.contains("attach") -> "🔗"
        lower.contains("power") || lower.contains("battery") || lower.contains("supply") -> "⚡"
        lower.contains("verify") || lower.contains("check") || lower.contains("test") -> "✅"
        lower.contains("remove") || lower.contains("disconnect") -> "❌"
        lower.contains("solder") -> "🔥"
        else -> "🔧"
    }
}

@Composable
private fun VerificationFeedback(outcome: VerificationOutcome) {
    val bgColor by animateColorAsState(
        targetValue = when (outcome.result) {
            VerificationResult.COMPLETE -> AccentGreen.copy(alpha = 0.12f)
            VerificationResult.INCOMPLETE -> ErrorRed.copy(alpha = 0.12f)
            VerificationResult.UNCERTAIN -> WarningYellow.copy(alpha = 0.12f)
            VerificationResult.ERROR -> ErrorRed.copy(alpha = 0.12f)
        },
        label = "feedback-bg",
    )
    val textColor = when (outcome.result) {
        VerificationResult.COMPLETE -> AccentGreen
        VerificationResult.INCOMPLETE -> ErrorRed
        VerificationResult.UNCERTAIN -> WarningYellow
        VerificationResult.ERROR -> ErrorRed
    }
    val icon = when (outcome.result) {
        VerificationResult.COMPLETE -> "✓"
        VerificationResult.INCOMPLETE -> "✗"
        VerificationResult.UNCERTAIN -> "?"
        VerificationResult.ERROR -> "⚠"
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Text(icon, color = textColor, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = outcome.explanation,
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
        }
    }
}
