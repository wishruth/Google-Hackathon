package com.npusensei.app

import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.npusensei.app.ui.camera.CoachCameraScreen
import com.npusensei.app.ui.home.CoachHomeScreen
import com.npusensei.app.ui.theme.NPUSenseiTheme
import com.npusensei.app.viewmodel.CoachViewModel
import kotlinx.coroutines.delay

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
                    AppRoute.CoachCamera -> CoachCameraScreen(viewModel = coachVm)
                    AppRoute.LegacyGemma -> {
                        val app = application as NpuSenseiApplication
                        NpuSenseiApp(gemmaEngine = app.gemmaEngine)
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
    data object LegacyGemma : AppRoute
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
