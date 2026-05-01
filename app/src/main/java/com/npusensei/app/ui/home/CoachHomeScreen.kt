package com.npusensei.app.ui.home

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.npusensei.app.AssetLogo
import com.npusensei.app.circuit.BlueprintRepository
import com.npusensei.app.circuit.CircuitBlueprint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun CoachHomeScreen(onPick: (CircuitBlueprint) -> Unit) {
    val context = LocalContext.current
    var blueprints by remember { mutableStateOf<List<CircuitBlueprint>>(emptyList()) }

    LaunchedEffect(Unit) {
        blueprints = withContext(Dispatchers.IO) {
            BlueprintRepository(context).loadAll()
        }
    }

    Surface(
        Modifier.fillMaxSize(),
        color = Color.White,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(top = 48.dp),
        ) {
            AssetLogo(
                assetName = "logo-transparent.png",
                modifier = Modifier.size(36.dp),
            )

            Spacer(Modifier.height(28.dp))

            Text(
                "Choose a project",
                fontSize = 26.sp,
                fontWeight = FontWeight.W600,
                color = Color(0xFF1A1A1A),
                letterSpacing = (-0.4).sp,
            )

            Spacer(Modifier.height(6.dp))

            Text(
                "We'll guide you through it step by step.",
                fontSize = 15.sp,
                fontWeight = FontWeight.W400,
                color = Color(0xFF999999),
                letterSpacing = 0.sp,
            )

            Spacer(Modifier.height(32.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(blueprints) { bp ->
                    BlueprintCard(bp = bp, onClick = { onPick(bp) })
                }
                if (blueprints.isEmpty()) {
                    item {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 40.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "No blueprints found",
                                color = Color(0xFFBBBBBB),
                                fontSize = 14.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BlueprintCard(bp: CircuitBlueprint, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFECECEC)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    bp.title,
                    fontWeight = FontWeight.W500,
                    fontSize = 16.sp,
                    color = Color(0xFF1A1A1A),
                    letterSpacing = (-0.2).sp,
                )
                Text(
                    bp.summary,
                    fontSize = 13.sp,
                    color = Color(0xFF999999),
                    lineHeight = 18.sp,
                )
                Text(
                    "${bp.steps.size} steps  ·  ${bp.estimatedMinutes} min  ·  ${bp.difficulty}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.W500,
                    color = Color(0xFF34A853),
                    letterSpacing = 0.2.sp,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = Color(0xFFCCCCCC),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
