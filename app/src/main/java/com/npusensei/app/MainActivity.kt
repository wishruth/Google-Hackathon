package com.npusensei.app

import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.npusensei.app.ui.camera.CoachCameraScreen
import com.npusensei.app.ui.home.CoachHomeScreen
import com.npusensei.app.ui.theme.NPUSenseiTheme
import com.npusensei.app.viewmodel.CoachViewModel
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        (application as NpuSenseiApplication).initGemmaAsync()

        enableEdgeToEdge()
        setContent {
            NPUSenseiTheme {
                var route by remember { mutableStateOf<AppRoute>(AppRoute.Splash) }
                val coachVm: CoachViewModel = viewModel(factory = CoachViewModel.Factory)

                LaunchedEffect(Unit) {
                    delay(1650)
                    route = AppRoute.CoachHome
                }

                when (route) {
                    AppRoute.Splash -> StartupLogoScreen()
                    AppRoute.CoachHome -> CoachHomeScreen(onPick = { bp ->
                        coachVm.selectBlueprint(bp)
                        route = AppRoute.CoachCamera
                    })
                    AppRoute.CoachCamera -> CoachCameraScreen(
                        viewModel = coachVm,
                        onComplete = { route = AppRoute.Celebration },
                    )
                    AppRoute.Celebration -> {
                        val celebState by coachVm.uiState.collectAsStateWithLifecycle()
                        CelebrationScreen(
                            title = celebState.blueprint?.title ?: "Circuit",
                            steps = celebState.blueprint?.steps?.size ?: 0,
                            elapsedMs = if (celebState.startedAtMs > 0)
                                System.currentTimeMillis() - celebState.startedAtMs else 0,
                            onHome = { route = AppRoute.CoachHome },
                        )
                    }
                }
            }
        }
    }
}

private sealed interface AppRoute {
    data object Splash : AppRoute
    data object CoachHome : AppRoute
    data object CoachCamera : AppRoute
    data object Celebration : AppRoute
}

@Composable
private fun StartupLogoScreen() {
    var showTextLogo by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(850)
        showTextLogo = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Box(
                modifier = Modifier
                    .height(180.dp)
                    .width(320.dp),
                contentAlignment = Alignment.Center,
            ) {
                Crossfade(
                    targetState = showTextLogo,
                    animationSpec = tween(durationMillis = 520),
                    label = "startup-logo-transition",
                ) { showFullLogo ->
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (showFullLogo) {
                            AssetLogo(
                                assetName = "logo-text.png",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(156.dp),
                            )
                        } else {
                            AssetLogo(
                                assetName = "logo-no-text.png",
                                modifier = Modifier.size(124.dp),
                            )
                        }
                    }
                }
            }
            Crossfade(
                targetState = showTextLogo,
                animationSpec = tween(durationMillis = 420),
                label = "startup-caption-transition",
            ) { showCaption ->
                if (showCaption) {
                    Text(
                        text = "Preparing your AR hardware mentor",
                        color = Color(0xFF6B7B75),
                        fontSize = 15.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun CelebrationScreen(
    title: String,
    steps: Int,
    elapsedMs: Long,
    onHome: () -> Unit,
) {
    val minutes = (elapsedMs / 60_000).toInt()
    val seconds = ((elapsedMs % 60_000) / 1000).toInt()
    val timeText = if (minutes > 0) "${minutes}m ${seconds}s" else "${seconds}s"

    val confettiColors = remember {
        listOf(
            Color(0xFF009E5E), Color(0xFF4FC3F7), Color(0xFFFFB74D),
            Color(0xFFE57373), Color(0xFF81C784), Color(0xFFBA68C8),
        )
    }

    data class Particle(
        val x: Float, val speed: Float, val size: Float,
        val color: Color, val angle: Float, val drift: Float,
    )

    val particles = remember {
        List(60) {
            Particle(
                x = Random.nextFloat(),
                speed = 0.15f + Random.nextFloat() * 0.35f,
                size = 4f + Random.nextFloat() * 8f,
                color = confettiColors.random(),
                angle = Random.nextFloat() * 360f,
                drift = (Random.nextFloat() - 0.5f) * 0.3f,
            )
        }
    }

    val transition = rememberInfiniteTransition(label = "confetti")
    val progress by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(4000, easing = LinearEasing), RepeatMode.Restart,
        ),
        label = "confetti-progress",
    )

    val fadeIn = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        fadeIn.animateTo(1f, tween(600))
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.White),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            for (p in particles) {
                val y = ((progress * p.speed + p.x) % 1f) * h * 1.2f
                val x = p.x * w + sin(y / 80f + p.angle) * p.drift * w
                drawCircle(
                    color = p.color.copy(alpha = (0.7f * fadeIn.value)),
                    radius = p.size,
                    center = Offset(x, y),
                )
            }
        }

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            AssetLogo(
                assetName = "logo-transparent.png",
                modifier = Modifier.size(48.dp),
            )

            Spacer(Modifier.height(24.dp))

            Text(
                "Circuit Complete",
                fontSize = 28.sp,
                fontWeight = FontWeight.W600,
                color = Color(0xFF1A1A1A),
                letterSpacing = (-0.5).sp,
            )

            Spacer(Modifier.height(8.dp))

            Text(
                title,
                fontSize = 16.sp,
                fontWeight = FontWeight.W400,
                color = Color(0xFF999999),
            )

            Spacer(Modifier.height(32.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatColumn(value = "$steps", label = "Steps")
                StatColumn(value = timeText, label = "Time")
            }

            Spacer(Modifier.height(48.dp))

            Button(
                onClick = onHome,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF009E5E),
                    contentColor = Color.White,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Text(
                    "Build Another",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.W500,
                )
            }
        }
    }
}

@Composable
private fun StatColumn(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            fontSize = 24.sp,
            fontWeight = FontWeight.W600,
            color = Color(0xFF009E5E),
        )
        Text(
            label,
            fontSize = 13.sp,
            fontWeight = FontWeight.W400,
            color = Color(0xFFAAAAAA),
        )
    }
}

@Composable
fun AssetLogo(assetName: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap: ImageBitmap? = remember(assetName) {
        try {
            context.assets.open(assetName).use { stream ->
                BitmapFactory.decodeStream(stream)?.asImageBitmap()
            }
        } catch (_: Exception) {
            null
        }
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = "Logo",
            modifier = modifier,
            contentScale = ContentScale.Fit,
        )
    }
}
