package com.npusensei.app.ui.coach

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.npusensei.app.circuit.StepStatus
import com.npusensei.app.viewmodel.CoachSource
import com.npusensei.app.viewmodel.CoachUiState

private val GlassWhite = Color.White.copy(alpha = 0.88f)
private val GlassStroke = Color.White.copy(alpha = 0.92f)
private val PrimaryText = Color(0xFF1A1A2E)
private val MutedText = Color(0xFF6B7B75)
private val Accent = Color(0xFF009E5E)

@Composable
fun CoachPanel(
    state: CoachUiState,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onAsk: () -> Unit,
    onAskQuestion: (String) -> Unit,
    onGrade: () -> Unit = {},
    onComplete: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val bp = state.blueprint
    var chatOpen by remember { mutableStateOf(false) }
    var chatText by remember { mutableStateOf("") }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(28.dp)),
        color = GlassWhite,
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, GlassStroke),
        shadowElevation = 16.dp,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (bp == null) {
                Text(
                    text = "Loading blueprint…",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MutedText,
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
                    color = PrimaryText,
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
                color = Accent,
                trackColor = Color(0xFFE6EFEA),
            )
            Text(
                "Step ${state.stepIndex + 1} of ${bp.steps.size}",
                style = MaterialTheme.typography.labelLarge,
                color = MutedText,
                fontSize = 13.sp,
            )

            state.currentStep?.let { step ->
                Text(
                    text = step.instruction,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = PrimaryText,
                )
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .background(
                        Color(0xFFF0F4F2),
                        RoundedCornerShape(14.dp),
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
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Accent,
                        )
                    }
                    Text(
                        text = state.coachText.ifBlank { "Tap ? or chat to ask Gemma…" },
                        style = MaterialTheme.typography.bodyLarge,
                        color = PrimaryText,
                        fontSize = 14.sp,
                    )
                }
            }

            AnimatedVisibility(
                visible = chatOpen,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = chatText,
                        onValueChange = { chatText = it },
                        placeholder = { Text("Ask Gemma…", fontSize = 13.sp, color = MutedText) },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 13.sp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = {
                            if (chatText.isNotBlank()) {
                                onAskQuestion(chatText.trim())
                                chatText = ""
                                chatOpen = false
                            }
                        }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Accent,
                            unfocusedBorderColor = Color(0xFFDDDDDD),
                            cursorColor = Accent,
                        ),
                    )
                    FilledIconButton(
                        onClick = {
                            if (chatText.isNotBlank()) {
                                onAskQuestion(chatText.trim())
                                chatText = ""
                                chatOpen = false
                            }
                        },
                        modifier = Modifier.size(40.dp),
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = Accent,
                            contentColor = Color.White,
                        ),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            if (state.isLastStep) {
                Button(
                    onClick = onComplete,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Accent,
                        contentColor = Color.White,
                    ),
                ) {
                    Icon(
                        Icons.Filled.Celebration,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "It lit up!",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.W500,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onPrev, enabled = state.stepIndex > 0) {
                    Icon(
                        Icons.Filled.ChevronLeft,
                        contentDescription = "Previous step",
                        tint = PrimaryText,
                    )
                }
                FilledIconButton(
                    onClick = onAsk,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color(0xFFE6EFEA),
                        contentColor = PrimaryText,
                    ),
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh coach")
                }
                FilledIconButton(
                    onClick = { chatOpen = !chatOpen },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (chatOpen) Accent.copy(alpha = 0.15f) else Color(0xFFE6EFEA),
                        contentColor = if (chatOpen) Accent else MutedText,
                    ),
                ) {
                    Icon(
                        Icons.Filled.ChatBubbleOutline,
                        contentDescription = "Chat with Gemma",
                        modifier = Modifier.size(20.dp),
                    )
                }
                Button(
                    onClick = onGrade,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Accent,
                        contentColor = Color.White,
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    modifier = Modifier.height(40.dp),
                ) {
                    Icon(
                        Icons.Filled.TaskAlt,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Grade", fontSize = 12.sp, fontWeight = FontWeight.W500)
                }
                Box(Modifier.weight(1f))
                IconButton(
                    onClick = onNext,
                    enabled = state.stepIndex < (bp.steps.size - 1),
                ) {
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = "Next step",
                        tint = PrimaryText,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusChip(status: StepStatus) {
    val (label, color) = when (status) {
        is StepStatus.WaitingFor -> "Waiting" to Color(0xFF9CA3AF)
        is StepStatus.Misplaced -> "Adjust" to Color(0xFFFF8A65)
        is StepStatus.WrongValue -> "Wrong value" to Color(0xFFEF4444)
        StepStatus.Ready -> "Ready" to Color(0xFFF59E0B)
        StepStatus.Complete -> "Done" to Color(0xFF009E5E)
    }
    AssistChip(
        onClick = {},
        label = { Text(label, fontSize = 12.sp) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = color.copy(alpha = 0.12f),
            labelColor = color,
        ),
    )
}

@Composable
private fun SourceChip(source: CoachSource) {
    val label = when (source) {
        CoachSource.LITERT_LM -> "Gemma 4"
    }
    AssistChip(
        onClick = {},
        label = { Text(label, fontSize = 12.sp) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = Color(0xFF009E5E).copy(alpha = 0.1f),
            labelColor = Color(0xFF009E5E),
        ),
    )
}
