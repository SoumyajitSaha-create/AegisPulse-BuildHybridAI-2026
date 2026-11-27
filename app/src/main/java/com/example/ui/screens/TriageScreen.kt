package com.example.ui.screens

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Bloodtype
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ElectricBolt
import androidx.compose.material.icons.rounded.HomeWork
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.EmergencyScenario
import com.example.data.EmergencyScenarios
import com.example.data.TriageGuidance
import com.example.data.UrgencyLevel
import com.example.ui.components.DisclaimerFooter
import com.example.ui.components.LiveEngineBadge
import com.example.ui.components.StreamingPulseBanner
import com.example.ui.components.UrgencyBanner
import com.example.ui.theme.AegisMonoStyles
import com.example.ui.theme.AegisTheme
import com.example.viewmodel.TriageViewModel
import java.util.Locale

private val SOS_PHRASES = listOf(
    "I need a doctor immediately.",
    "Severe allergic reaction — need epinephrine.",
    "People are trapped inside — send rescue.",
    "No cell signal — please call emergency services.",
    "Heavy bleeding — need first-aid kit.",
    "I have diabetes / low blood sugar."
)

private val SOS_LANGUAGES = listOf(
    "Spanish", "French", "German", "Mandarin Chinese", "Japanese",
    "Arabic", "Hindi", "Portuguese", "Korean", "Italian", "Russian"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TriageScreen(
    viewModel: TriageViewModel,
    disclaimerText: String,
    onRequestSignInForSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isSpeaking by viewModel.isSpeaking.collectAsStateWithLifecycle()
    val semantic = AegisTheme.colors

    var showSosSheet by remember { mutableStateOf(false) }
    var showFullScreenFlashcard by remember { mutableStateOf(false) }
    var voiceStatusHint by remember { mutableStateOf<String?>(null) }

    val voiceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spoken = matches?.firstOrNull().orEmpty()
            if (spoken.isNotBlank()) {
                voiceStatusHint = null
                viewModel.applyVoiceTranscription(spoken)
            }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section Header + SOS Translator Trigger
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Field Triage Deck",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "One-tap hybrid guidance • PREFER_ON_DEVICE",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(
                    onClick = { showSosSheet = true },
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("open_sos_translator_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Translate,
                        contentDescription = stringResource(R.string.cd_sos_translator),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.sos_phrases_button),
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }

        // 6-Card Hero Emergency Action Grid (2 columns x 3 rows)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                EmergencyScenarios.all.chunked(2).forEach { rowPair ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        rowPair.forEach { scenario ->
                            EmergencyScenarioCard(
                                scenario = scenario,
                                selected = state.activeTitle == scenario.title,
                                enabled = !state.isStreaming,
                                onClick = { viewModel.runQuickScenario(scenario) },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            )
                        }
                    }
                }
            }
        }

        // Custom Situation Input + Voice Dictation
        item {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, semantic.cardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Describe Custom Situation",
                        style = MaterialTheme.typography.titleMedium
                    )
                    OutlinedTextField(
                        value = state.customInput,
                        onValueChange = viewModel::onCustomInputChange,
                        placeholder = {
                            Text(
                                text = "e.g., Ankle sprain on remote trail, no signal, 2 hrs from road",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        minLines = 2,
                        maxLines = 4,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("custom_triage_input")
                    )
                    if (!voiceStatusHint.isNullOrBlank()) {
                        Text(
                            text = voiceStatusHint!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = semantic.warning
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                    putExtra(
                                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                                    )
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                                    putExtra(RecognizerIntent.EXTRA_PROMPT, "Describe the emergency clearly")
                                }
                                runCatching {
                                    voiceLauncher.launch(intent)
                                }.onFailure {
                                    voiceStatusHint = "Voice dictation service unavailable on this device. Please type your scenario above."
                                }
                            },
                            enabled = !state.isStreaming,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("voice_input_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Mic,
                                contentDescription = stringResource(R.string.cd_voice_input),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Voice")
                        }

                        Button(
                            onClick = { viewModel.runCustomTriage() },
                            enabled = !state.isStreaming,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = semantic.cloud),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("submit_custom_triage_button")
                        ) {
                            if (state.isStreaming) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Generating...")
                            } else {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.Send,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Get Triage Guidance")
                            }
                        }
                    }
                }
            }
        }

        // Error Card
        if (!state.errorMessage.isNullOrBlank()) {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = semantic.emergencyContainer,
                    border = BorderStroke(1.dp, semantic.emergency.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Warning,
                            contentDescription = "Error",
                            tint = semantic.emergency
                        )
                        Text(
                            text = state.errorMessage!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Structured AI Triage Output Card
        val guidance = state.guidance
        if (state.isStreaming || guidance != null) {
            item {
                StructuredTriageCard(
                    title = state.activeTitle,
                    guidance = guidance ?: TriageGuidance(),
                    isStreaming = state.isStreaming,
                    partialSource = state.partialSource,
                    telemetry = state.telemetry,
                    checkedSteps = state.checkedSteps,
                    isSpeaking = isSpeaking,
                    isSaving = state.isSaving,
                    saveBannerMessage = state.saveBannerMessage,
                    onToggleStep = viewModel::toggleStep,
                    onToggleSpeak = viewModel::toggleSpeakCurrentGuidance,
                    onSaveToLog = { viewModel.saveCurrentTriageToLog(onRequestSignInForSave) }
                )
            }
        }

        // Persistent Safety Disclaimer
        item {
            DisclaimerFooter(disclaimerText = disclaimerText)
            Spacer(Modifier.height(12.dp))
        }
    }

    // Offline SOS Phrase Translator Bottom Sheet
    if (showSosSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showSosSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            SosTranslatorSheetContent(
                viewModel = viewModel,
                onOpenFlashcard = { showFullScreenFlashcard = true }
            )
        }
    }

    // Full-Screen High-Contrast SOS Flashcard Modal
    if (showFullScreenFlashcard && state.sosState.translatedOutput.isNotBlank()) {
        Dialog(
            onDismissRequest = { showFullScreenFlashcard = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                color = Color(0xFF0D1117),
                modifier = Modifier.fillMaxSize()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "EMERGENCY SOS • ${state.sosState.selectedLanguage.uppercase()}",
                            style = AegisMonoStyles.badge.copy(fontSize = 13.sp),
                            color = semantic.emergency
                        )
                        IconButton(onClick = { showFullScreenFlashcard = false }) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Close flashcard",
                                tint = Color.White
                            )
                        }
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 24.dp)
                    ) {
                        Text(
                            text = state.sosState.translatedOutput,
                            style = MaterialTheme.typography.displaySmall.copy(
                                fontSize = 30.sp,
                                lineHeight = 40.sp
                            ),
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Original: \"${state.sosState.selectedPhrase}\"",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color(0xFF9CA3AF),
                            textAlign = TextAlign.Center
                        )
                    }

                    Button(
                        onClick = { showFullScreenFlashcard = false },
                        colors = ButtonDefaults.buttonColors(containerColor = semantic.emergency),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Text("Exit Full-Screen Card", color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmergencyScenarioCard(
    scenario: EmergencyScenario,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val semantic = AegisTheme.colors
    val accent = when (scenario.defaultUrgency) {
        UrgencyLevel.CRITICAL -> semantic.emergency
        UrgencyLevel.HIGH -> semantic.warning
        else -> semantic.cloud
    }
    val icon: ImageVector = when (scenario.iconKey) {
        "bleeding" -> Icons.Rounded.Bloodtype
        "burns" -> Icons.Rounded.LocalFireDepartment
        "choking" -> Icons.Rounded.Air
        "earthquake" -> Icons.Rounded.HomeWork
        "heat" -> Icons.Rounded.Thermostat
        else -> Icons.Rounded.ElectricBolt
    }

    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) accent.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) accent else semantic.cardBorder
        ),
        modifier = modifier
            .heightIn(min = 112.dp)
            .testTag("scenario_card_${scenario.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(accent.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = scenario.title,
                        tint = accent,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text = if (scenario.defaultUrgency == UrgencyLevel.CRITICAL) "URGENT" else "READY",
                    style = AegisMonoStyles.badge.copy(fontSize = 9.sp),
                    color = accent
                )
            }
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = scenario.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = scenario.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }
        }
    }
}

@Composable
private fun StructuredTriageCard(
    title: String,
    guidance: TriageGuidance,
    isStreaming: Boolean,
    partialSource: com.example.data.EngineSource?,
    telemetry: com.example.data.InferenceTelemetry?,
    checkedSteps: Set<Int>,
    isSpeaking: Boolean,
    isSaving: Boolean,
    saveBannerMessage: String?,
    onToggleStep: (Int) -> Unit,
    onToggleSpeak: () -> Unit,
    onSaveToLog: () -> Unit
) {
    val semantic = AegisTheme.colors

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, semantic.cardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("triage_output_card")
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Card Header + Live Engine Provenance Badge
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = title.ifBlank { "Active Triage Guidance" },
                    style = MaterialTheme.typography.headlineSmall
                )
                if (isStreaming) {
                    StreamingPulseBanner(partialSource = partialSource)
                } else if (telemetry != null) {
                    LiveEngineBadge(telemetry = telemetry)
                }
            }

            // Urgency Banner with direct ACTION_DIAL emergency utility
            UrgencyBanner(
                urgency = guidance.urgency,
                showDialButton = true
            )

            // Calm Summary
            if (guidance.summary.isNotBlank()) {
                Text(
                    text = guidance.summary,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Interactive Actionable Checklist
            if (guidance.steps.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "ACTION CHECKLIST (${checkedSteps.size}/${guidance.steps.size} DONE)",
                        style = AegisMonoStyles.badge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    guidance.steps.forEachIndexed { index, step ->
                        val isChecked = index in checkedSteps
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isChecked) {
                                semantic.onDeviceContainer.copy(alpha = 0.6f)
                            } else {
                                semantic.elevatedSurface
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onToggleStep(index) }
                                .testTag("triage_step_$index")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = if (isChecked) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                    contentDescription = if (isChecked) "Step completed" else "Mark step complete",
                                    tint = if (isChecked) semantic.onDevice else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp)
                                )
                                Text(
                                    text = "${index + 1}. $step",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        textDecoration = if (isChecked) TextDecoration.LineThrough else TextDecoration.None
                                    ),
                                    color = if (isChecked) {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            // "What NOT to do" Warning Box
            AnimatedVisibility(visible = guidance.doNotDo.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = semantic.emergencyContainer,
                    border = BorderStroke(1.dp, semantic.emergency.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Warning,
                                contentDescription = "What not to do",
                                tint = semantic.emergency,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "WHAT NOT TO DO",
                                style = AegisMonoStyles.badge.copy(fontWeight = FontWeight.Bold),
                                color = semantic.emergency
                            )
                        }
                        Text(
                            text = guidance.doNotDo,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Action Controls: Hands-Free TTS Readout + Save to Incident Log
            if (!isStreaming) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onToggleSpeak,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("triage_tts_button")
                    ) {
                        Icon(
                            imageVector = if (isSpeaking) Icons.Rounded.Stop else Icons.AutoMirrored.Rounded.VolumeUp,
                            contentDescription = if (isSpeaking) {
                                stringResource(R.string.cd_stop_speaking)
                            } else {
                                stringResource(R.string.cd_speak_guidance)
                            },
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(if (isSpeaking) "Stop Audio" else "Read Aloud")
                    }

                    Button(
                        onClick = onSaveToLog,
                        enabled = !isSaving,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = semantic.onDevice),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("save_triage_incident_button")
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.BookmarkAdd,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Save to Log")
                        }
                    }
                }
            }

            if (!saveBannerMessage.isNullOrBlank()) {
                Text(
                    text = saveBannerMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = semantic.onDevice
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SosTranslatorSheetContent(
    viewModel: TriageViewModel,
    onOpenFlashcard: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val sos = state.sosState
    val semantic = AegisTheme.colors
    val scrollState = rememberScrollState()

    // Automatically scroll translated result card or error banner into view once measured
    LaunchedEffect(sos.translatedOutput, sos.errorMessage, scrollState.maxValue) {
        if ((sos.translatedOutput.isNotBlank() || !sos.errorMessage.isNullOrBlank()) && scrollState.maxValue > 0) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Offline SOS Phrase Translator",
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = "Powered by PREFER_ON_DEVICE with automatic Cloud Gemini fallback.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(text = "Select Vital Phrase", style = MaterialTheme.typography.titleSmall)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SOS_PHRASES.forEach { phrase ->
                FilterChip(
                    selected = sos.selectedPhrase == phrase,
                    onClick = { viewModel.selectSosPhrase(phrase) },
                    label = { Text(phrase, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                )
            }
        }

        OutlinedTextField(
            value = sos.selectedPhrase,
            onValueChange = viewModel::selectSosPhrase,
            label = { Text("Custom SOS phrase") },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Text(text = "Target Language", style = MaterialTheme.typography.titleSmall)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SOS_LANGUAGES.forEach { lang ->
                FilterChip(
                    selected = sos.selectedLanguage == lang,
                    onClick = { viewModel.selectSosLanguage(lang) },
                    label = { Text(lang) }
                )
            }
        }

        Button(
            onClick = { viewModel.translateSosPhrase() },
            enabled = !sos.isTranslating,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = semantic.onDevice),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("translate_sos_button")
        ) {
            if (sos.isTranslating) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(10.dp))
                Text("Translating...", color = Color.White)
            } else {
                Text("Translate Phrase")
            }
        }

        if (!sos.errorMessage.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = semantic.emergencyContainer,
                border = BorderStroke(1.dp, semantic.emergency.copy(alpha = 0.65f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("sos_error_banner")
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Warning,
                        contentDescription = "Translation error",
                        tint = semantic.emergency,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = sos.errorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        if (sos.translatedOutput.isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = semantic.elevatedSurface,
                border = BorderStroke(1.dp, semantic.cardBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("sos_result_card")
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    sos.telemetry?.let { LiveEngineBadge(telemetry = it) }
                    Text(
                        text = sos.translatedOutput,
                        style = MaterialTheme.typography.titleLarge
                    )
                    OutlinedButton(
                        onClick = onOpenFlashcard,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("open_flashcard_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Fullscreen,
                            contentDescription = "Full-screen flashcard",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Display Full-Screen Flashcard")
                    }
                }
            }
        }
    }
}
