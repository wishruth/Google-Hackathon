package com.npusensei.app.ui.coach

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.npusensei.app.circuit.StepStatus
import com.npusensei.app.gemma.CoachResponse
import com.npusensei.app.viewmodel.CoachUiState

@Composable
fun CoachPanel(
    state: CoachUiState,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onAsk: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bp = state.blueprint
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        tonalElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (bp == null) {
                Text(
                    text = "Loading blueprint…",
                    style = MaterialTheme.typography.bodyLarge,
                )
                return@Surface
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = bp.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                StatusChip(state.status)
                state.coachSource?.let { SourceChip(it) }
            }

            val progress = if (bp.steps.isEmpty()) 0f
            else (state.stepIndex + 1).toFloat() / bp.steps.size.toFloat()
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Text(
                "Step ${state.stepIndex + 1} of ${bp.steps.size}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            state.currentStep?.let { step ->
                Text(
                    text = step.instruction,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(12.dp),
                    )
                    .padding(12.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    AnimatedVisibility(
                        state.coachThinking,
                        enter = fadeIn(),
                        exit = fadeOut(),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                    Text(
                        text = state.coachText.ifBlank { "Watching your workspace…" },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onPrev, enabled = state.stepIndex > 0) {
                    Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous step")
                }
                FilledIconButton(onClick = onAsk) {
                    Icon(Icons.Filled.Mic, contentDescription = "Ask coach")
                }
                Box(Modifier.weight(1f))
                IconButton(
                    onClick = onNext,
                    enabled = state.stepIndex < (bp.steps.size - 1),
                ) {
                    Icon(Icons.Filled.ChevronRight, contentDescription = "Next step")
                }
            }
        }
    }
}

@Composable
private fun StatusChip(status: StepStatus) {
    val (label, color) = when (status) {
        is StepStatus.WaitingFor -> "Waiting" to Color(0xFFB7B7BC)
        is StepStatus.Misplaced -> "Adjust" to Color(0xFFFF8A65)
        is StepStatus.WrongValue -> "Wrong value" to Color(0xFFFF4D4D)
        StepStatus.Ready -> "Ready" to Color(0xFFFFD400)
        StepStatus.Complete -> "Done" to Color(0xFF4DCEA0)
    }
    AssistChip(
        onClick = {},
        label = { Text(label) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = color.copy(alpha = 0.2f),
            labelColor = color,
        ),
    )
}

@Composable
private fun SourceChip(source: CoachResponse.Source) {
    val label = when (source) {
        CoachResponse.Source.ON_DEVICE -> "Gemma 3n"
        CoachResponse.Source.OFFLINE_TEMPLATE -> "Offline"
    }
    AssistChip(
        onClick = {},
        label = { Text(label) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
            labelColor = MaterialTheme.colorScheme.primary,
        ),
    )
}
