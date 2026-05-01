package com.npusensei.app.ui.benchmark

import android.graphics.Bitmap
import android.os.Debug
import android.util.Log
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.npusensei.app.GemmaReasoningEngine
import com.npusensei.app.NpuSenseiApplication
import com.npusensei.app.camera.CameraController
import com.npusensei.app.camera.FrameAnalyzer
import com.npusensei.app.gemma.PromptBuilder
import com.npusensei.app.ml.ObjectDetector
import com.npusensei.app.ui.camera.CoachDetectionOverlay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

private const val TAG = "BenchmarkScreen"
private val Accent = Color(0xFF009E5E)
private val Mono = FontFamily.Monospace

@Composable
fun BenchmarkScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as NpuSenseiApplication
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val analyzer = remember { FrameAnalyzer(app.detector) }
    val controller = remember { CameraController(context) }
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.PERFORMANCE
        }
    }

    DisposableEffect(Unit) {
        controller.bind(lifecycleOwner, previewView, analyzer)
        onDispose { controller.shutdown() }
    }

    val detections by analyzer.liveDetections.collectAsStateWithLifecycle()
    val frameSize by analyzer.frameSize.collectAsStateWithLifecycle()
    val yoloMs by analyzer.yoloLatencyMs.collectAsStateWithLifecycle()
    val yoloFps by analyzer.yoloFps.collectAsStateWithLifecycle()

    var running by remember { mutableStateOf(false) }

    var yoloDetectMs by remember { mutableLongStateOf(0L) }
    var gemmaTextMs by remember { mutableLongStateOf(0L) }
    var gemmaTextResponse by remember { mutableStateOf("") }

    var gemmaVisionMs by remember { mutableLongStateOf(0L) }
    var gemmaVisionResponse by remember { mutableStateOf("") }

    var memoryMb by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        while (true) {
            val mi = Debug.MemoryInfo()
            Debug.getMemoryInfo(mi)
            memoryMb = mi.totalPss / 1024f
            delay(1000)
        }
    }

    fun runBenchmark() {
        val engine = app.gemmaEngine
        if (!engine.initialized) return

        val bitmap = previewView.bitmap
        if (bitmap == null) {
            gemmaTextResponse = "(no camera frame available)"
            gemmaVisionResponse = "(no camera frame available)"
            return
        }

        running = true
        gemmaTextResponse = ""
        gemmaVisionResponse = ""
        gemmaTextMs = 0
        gemmaVisionMs = 0

        scope.launch(Dispatchers.IO) {
            try {

                // ── Run 1: YOLO + Gemma text ────────────────────────
                val t0 = System.currentTimeMillis()
                val dets = app.detector.detect(bitmap)
                yoloDetectMs = System.currentTimeMillis() - t0

                val visible = dets.joinToString(", ") { d ->
                    buildString {
                        append(d.label)
                        d.resistorOhms?.let { append(" (${it}Ω)") }
                        append(" @${(d.score * 100).toInt()}%")
                    }
                }.ifEmpty { "(nothing recognized)" }

                val textPrompt = "VISIBLE ON CAMERA: $visible\n" +
                    "Describe what circuit components you see and how they are connected."

                engine.resetConversation()
                engine.startConversation(PromptBuilder.SYSTEM_PROMPT)

                val textStart = System.currentTimeMillis()
                val textChunks = mutableListOf<String>()
                engine.sendMessage(textPrompt)
                    .catch { e -> textChunks.add("(error: ${e.message})") }
                    .collect { chunk ->
                        textChunks.add(chunk)
                        gemmaTextResponse = textChunks.joinToString("")
                    }
                gemmaTextMs = System.currentTimeMillis() - textStart

                // ── Run 2: Gemma vision (image) ─────────────────────
                val tmpFile = File(context.cacheDir, "bench_frame.jpg")
                FileOutputStream(tmpFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }

                engine.resetConversation()
                engine.startConversation(PromptBuilder.SYSTEM_PROMPT)

                val visionPrompt = "Describe what circuit components you see and how they are connected."
                val visionStart = System.currentTimeMillis()
                val visionChunks = mutableListOf<String>()
                engine.sendImageMessage(visionPrompt, tmpFile.absolutePath)
                    .catch { e -> visionChunks.add("(error: ${e.message})") }
                    .collect { chunk ->
                        visionChunks.add(chunk)
                        gemmaVisionResponse = visionChunks.joinToString("")
                    }
                gemmaVisionMs = System.currentTimeMillis() - visionStart

                bitmap.recycle()
                tmpFile.delete()
            } catch (t: Throwable) {
                Log.e(TAG, "Benchmark failed", t)
                gemmaTextResponse = "(error: ${t.message})"
                gemmaVisionResponse = "(error: ${t.message})"
            } finally {
                running = false
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize(),
        )

        CoachDetectionOverlay(
            detections = detections,
            frameSize = frameSize,
            highlightBox = null,
            highlightPx = null,
            nextStepLabel = null,
            modifier = Modifier.fillMaxSize(),
        )

        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(start = 8.dp, top = 8.dp),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color.White,
            )
        }

        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(12.dp),
            color = Color.White.copy(alpha = 0.94f),
            shape = RoundedCornerShape(24.dp),
            shadowElevation = 16.dp,
        ) {
            Column(Modifier.padding(18.dp)) {
                Text(
                    "INFERENCE BENCHMARK",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = Mono,
                    color = Accent,
                    letterSpacing = 1.sp,
                )

                Spacer(Modifier.height(12.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    MetricBlock("YOLO FPS", "${"%.1f".format(yoloFps)}", Color(0xFF4FC3F7))
                    MetricBlock("YOLO", "${yoloMs}ms", Color(0xFF4FC3F7))
                    MetricBlock("Memory", "${"%.0f".format(memoryMb)}MB", Color(0xFF999999))
                }

                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = Color(0xFFEEEEEE))
                Spacer(Modifier.height(14.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ResultColumn(
                        title = "YOLO + Gemma Text",
                        latency = if (gemmaTextMs > 0) "${gemmaTextMs}ms" else "—",
                        extraLabel = "YOLO",
                        extraValue = if (yoloDetectMs > 0) "${yoloDetectMs}ms" else "—",
                        response = gemmaTextResponse,
                        color = Color(0xFF4FC3F7),
                        modifier = Modifier.weight(1f),
                    )
                    ResultColumn(
                        title = "Gemma Vision",
                        latency = if (gemmaVisionMs > 0) "${gemmaVisionMs}ms" else "—",
                        extraLabel = null,
                        extraValue = null,
                        response = gemmaVisionResponse,
                        color = Color(0xFFFFB74D),
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = { runBenchmark() },
                    enabled = !running && app.gemmaReady,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Accent,
                        contentColor = Color.White,
                    ),
                ) {
                    if (running) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = Color.White,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Running…", fontSize = 14.sp)
                    } else {
                        Text(
                            if (app.gemmaReady) "Run Benchmark" else "Gemma loading…",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.W500,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricBlock(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.W600, color = color, fontFamily = Mono)
        Text(label, fontSize = 10.sp, color = Color(0xFFAAAAAA), fontFamily = Mono)
    }
}

@Composable
private fun ResultColumn(
    title: String,
    latency: String,
    extraLabel: String?,
    extraValue: String?,
    response: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(
            title,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = Mono,
            color = color,
            letterSpacing = 0.5.sp,
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Gemma ", fontSize = 10.sp, color = Color(0xFFAAAAAA), fontFamily = Mono)
            Text(latency, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color, fontFamily = Mono)
        }
        if (extraLabel != null && extraValue != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$extraLabel ", fontSize = 10.sp, color = Color(0xFFAAAAAA), fontFamily = Mono)
                Text(extraValue, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color, fontFamily = Mono)
            }
        }
        Spacer(Modifier.height(6.dp))
        Surface(
            color = Color(0xFFF5F5F5),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = response.ifBlank { "Tap Run to start" },
                fontSize = 11.sp,
                color = if (response.isBlank()) Color(0xFFBBBBBB) else Color(0xFF333333),
                modifier = Modifier.padding(8.dp),
                maxLines = 4,
                lineHeight = 15.sp,
            )
        }
    }
}
