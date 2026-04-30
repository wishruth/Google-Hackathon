package com.npusensei.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart

private sealed class AppScreen {
    data object Loading : AppScreen()
    data object Home : AppScreen()
    data class Response(val prompt: String) : AppScreen()
}

@Composable
fun NpuSenseiApp(gemmaEngine: GemmaReasoningEngine) {
    var screen: AppScreen by remember { mutableStateOf(AppScreen.Loading) }

    LaunchedEffect(Unit) {
        delay(1450)
        screen = AppScreen.Home
    }

    when (val current = screen) {
        AppScreen.Loading -> NpuSenseiLoadingScreen()
        AppScreen.Home -> NpuSenseiHomeScreen(
            onSubmit = { prompt -> screen = AppScreen.Response(prompt) },
        )
        is AppScreen.Response -> GemmaResponseScreen(
            prompt = current.prompt,
            engine = gemmaEngine,
            onBack = { screen = AppScreen.Home },
        )
    }
}

// ── Gemma Response Screen ───────────────────────────────────────────────

@Composable
private fun GemmaResponseScreen(
    prompt: String,
    engine: GemmaReasoningEngine,
    onBack: () -> Unit,
) {
    var responseText by remember { mutableStateOf("") }
    var isStreaming by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val scrollState = rememberScrollState()

    LaunchedEffect(prompt) {
        if (!engine.initialized) {
            errorMsg = "Engine not ready yet — wait a moment and try again"
            return@LaunchedEffect
        }

        engine.sendMessage(prompt)
            .onStart { isStreaming = true; responseText = "" }
            .catch { e ->
                errorMsg = e.message
                isStreaming = false
            }
            .onCompletion { isStreaming = false }
            .collect { chunk ->
                responseText += chunk
            }
    }

    LaunchedEffect(responseText) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .premiumBackground(),
    ) {
        CircuitBackground(alpha = 0.05f)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("NPU-Sensei", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Button(
                    onClick = {
                        engine.resetConversation()
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.08f),
                        contentColor = Color.White,
                    ),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text("New Chat", fontSize = 13.sp)
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                color = AccentGreen.copy(alpha = 0.08f),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, AccentGreen.copy(alpha = 0.18f)),
            ) {
                Text(
                    text = prompt,
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(14.dp),
                )
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = 12.dp),
                color = SurfaceGlass,
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
            ) {
                Box(modifier = Modifier.padding(16.dp)) {
                    if (errorMsg != null) {
                        Text(
                            text = "Error: $errorMsg",
                            color = Color(0xFFFF6B6B),
                            fontSize = 14.sp,
                        )
                    } else if (responseText.isEmpty() && isStreaming) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = AccentGreen,
                                strokeWidth = 2.dp,
                            )
                            Text("Thinking...", color = MutedText, fontSize = 14.sp)
                        }
                    } else {
                        Column {
                            Text(
                                text = responseText,
                                color = Color.White.copy(alpha = 0.92f),
                                fontSize = 15.sp,
                                lineHeight = 22.sp,
                                modifier = Modifier.verticalScroll(scrollState),
                            )
                            if (isStreaming) {
                                Text("...", color = AccentGreen, fontSize = 15.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Loading Screen ──────────────────────────────────────────────────────

@Composable
private fun NpuSenseiLoadingScreen() {
    val pulse = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        pulse.animateTo(
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 950, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .premiumBackground(),
        contentAlignment = Alignment.Center,
    ) {
        CircuitBackground(alpha = 0.075f)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Surface(
                color = SurfaceGlass,
                shape = RoundedCornerShape(32.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                shadowElevation = 18.dp,
            ) {
                Canvas(
                    modifier = Modifier
                        .padding(24.dp)
                        .size(76.dp),
                ) {
                    drawCircle(
                        color = AccentGreen.copy(alpha = 0.09f + pulse.value * 0.10f),
                        radius = 31.dp.toPx() + pulse.value * 5.dp.toPx(),
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.88f),
                        radius = 25.dp.toPx(),
                        style = Stroke(width = 2.dp.toPx()),
                    )
                    drawLine(
                        color = AccentGreen,
                        start = Offset(size.width * 0.32f, size.height * 0.50f),
                        end = Offset(size.width * 0.68f, size.height * 0.50f),
                        strokeWidth = 3.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                    drawLine(
                        color = AccentGreen,
                        start = Offset(size.width * 0.50f, size.height * 0.32f),
                        end = Offset(size.width * 0.50f, size.height * 0.68f),
                        strokeWidth = 3.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
            }
            Text(
                text = "NPU-Sensei",
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Preparing your AR hardware mentor",
                color = MutedText,
                fontSize = 15.sp,
            )
        }
    }
}

// ── Home Screen ─────────────────────────────────────────────────────────

@Composable
private fun NpuSenseiHomeScreen(onSubmit: (String) -> Unit) {
    var prompt by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current
    val canStart = prompt.trim().isNotEmpty()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .premiumBackground(),
    ) {
        CircuitBackground(alpha = 0.085f)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 22.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "NPU-Sensei",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                )
                Surface(
                    color = Color.White.copy(alpha = 0.06f),
                    shape = CircleShape,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
                ) {
                    Text(
                        text = "Guided AR",
                        color = Color.White.copy(alpha = 0.86f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                CircuitHeroMark()
                Text(
                    text = "What are you building?",
                    color = Color.White,
                    fontSize = 34.sp,
                    lineHeight = 39.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "Describe your Raspberry Pi wiring task. I'll turn it into camera-guided steps.",
                    color = MutedText,
                    fontSize = 16.sp,
                    lineHeight = 23.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 14.dp),
                )
            }

            Surface(
                color = SurfaceGlass,
                shape = RoundedCornerShape(30.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.09f)),
                shadowElevation = 16.dp,
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = prompt,
                        onValueChange = { prompt = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Ask about a circuit, sensor, or GPIO wiring...") },
                        singleLine = true,
                        shape = RoundedCornerShape(20.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = AccentGreen,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.10f),
                            cursorColor = AccentGreen,
                            focusedPlaceholderColor = Color.White.copy(alpha = 0.38f),
                            unfocusedPlaceholderColor = Color.White.copy(alpha = 0.38f),
                            focusedContainerColor = Color.White.copy(alpha = 0.045f),
                            unfocusedContainerColor = Color.White.copy(alpha = 0.045f),
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(
                            onGo = {
                                if (canStart) {
                                    keyboardController?.hide()
                                    onSubmit(prompt.trim())
                                }
                            },
                        ),
                    )
                    Button(
                        onClick = {
                            keyboardController?.hide()
                            onSubmit(prompt.trim())
                        },
                        enabled = canStart,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentGreen,
                            contentColor = Color(0xFF001F12),
                            disabledContainerColor = Color.White.copy(alpha = 0.08f),
                            disabledContentColor = Color.White.copy(alpha = 0.32f),
                        ),
                    ) {
                        Text(
                            text = "Ask NPU Sensei",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                        )
                    }
                }
            }
        }
    }
}

// ── Decorative components ───────────────────────────────────────────────

@Composable
private fun CircuitHeroMark() {
    Box(
        modifier = Modifier
            .size(132.dp)
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(36.dp))
            .background(SurfaceGlass, RoundedCornerShape(36.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(84.dp)) {
            val stroke = 3.dp.toPx()
            val pad = 11.dp.toPx()
            drawRoundRect(
                color = Color.White.copy(alpha = 0.88f),
                topLeft = Offset(pad, pad),
                size = Size(size.width - pad * 2, size.height - pad * 2),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx(), 16.dp.toPx()),
                style = Stroke(width = stroke),
            )
            repeat(4) { index ->
                val y = pad + 16.dp.toPx() + index * 15.dp.toPx()
                drawLine(AccentGreen.copy(alpha = 0.82f), Offset(0f, y), Offset(pad, y), stroke, StrokeCap.Round)
                drawLine(AccentGreen.copy(alpha = 0.82f), Offset(size.width - pad, y), Offset(size.width, y), stroke, StrokeCap.Round)
            }
            repeat(3) { index ->
                val x = pad + 20.dp.toPx() + index * 17.dp.toPx()
                drawLine(AccentGreen.copy(alpha = 0.82f), Offset(x, 0f), Offset(x, pad), stroke, StrokeCap.Round)
                drawLine(AccentGreen.copy(alpha = 0.82f), Offset(x, size.height - pad), Offset(x, size.height), stroke, StrokeCap.Round)
            }
            drawCircle(AccentGreen, 6.dp.toPx(), Offset(size.width / 2f, size.height / 2f))
        }
    }
}

@Composable
private fun CircuitBackground(alpha: Float) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val lineColor = AccentGreen.copy(alpha = alpha)
        val nodeColor = AccentGreen.copy(alpha = alpha + 0.05f)
        val nodes = listOf(
            Offset(size.width * 0.18f, size.height * 0.18f),
            Offset(size.width * 0.82f, size.height * 0.24f),
            Offset(size.width * 0.26f, size.height * 0.72f),
            Offset(size.width * 0.74f, size.height * 0.78f),
        )
        nodes.forEach { node ->
            drawCircle(nodeColor, 3.5.dp.toPx(), node)
            drawCircle(lineColor, 11.dp.toPx(), node, style = Stroke(width = 1.dp.toPx()))
        }
        drawLine(lineColor, nodes[0], nodes[1], 1.2.dp.toPx(), StrokeCap.Round)
        drawLine(lineColor, nodes[2], nodes[3], 1.2.dp.toPx(), StrokeCap.Round)
        drawLine(lineColor.copy(alpha = alpha * 0.65f), nodes[0], nodes[2], 1.dp.toPx(), StrokeCap.Round)
        drawLine(lineColor.copy(alpha = alpha * 0.65f), nodes[1], nodes[3], 1.dp.toPx(), StrokeCap.Round)
        drawCircle(
            color = AccentGreen.copy(alpha = 0.06f),
            radius = size.minDimension * 0.42f,
            center = Offset(size.width * 0.50f, size.height * 0.42f),
        )
    }
}

private fun Modifier.premiumBackground(): Modifier {
    return background(
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFF0B0F12),
                Color(0xFF050708),
                Color(0xFF020303),
            ),
        ),
    )
}

private val SurfaceGlass = Color(0xFF11181C).copy(alpha = 0.72f)
private val MutedText = Color.White.copy(alpha = 0.62f)
private val AccentGreen = Color(0xFF00FF88)
