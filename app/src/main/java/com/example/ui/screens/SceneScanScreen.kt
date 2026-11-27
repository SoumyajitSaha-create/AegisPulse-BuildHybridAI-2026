package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.MediaHelper
import com.example.ui.components.DisclaimerFooter
import com.example.ui.components.LiveEngineBadge
import com.example.ui.components.OfflineBannerCard
import com.example.ui.components.UrgencyBanner
import com.example.ui.theme.AegisMonoStyles
import com.example.ui.theme.AegisTheme
import com.example.viewmodel.ScenePreset
import com.example.viewmodel.SceneScanViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SceneScanScreen(
    viewModel: SceneScanViewModel,
    disclaimerText: String,
    onRequestSignInForSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val isSpeaking by viewModel.isSpeaking.collectAsStateWithLifecycle()
    val semantic = AegisTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var permissionNotice by remember { mutableStateOf<String?>(null) }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingCameraUri
        if (success && uri != null) {
            scope.launch {
                val bmp = withContext(Dispatchers.IO) {
                    MediaHelper.decodeScaledBitmap(context, uri)
                }
                viewModel.onBitmapReady(bmp)
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            permissionNotice = null
            runCatching {
                val uri = MediaHelper.createCameraCaptureUri(context)
                pendingCameraUri = uri
                takePictureLauncher.launch(uri)
            }.onFailure {
                permissionNotice = "Camera app unavailable on this device. Use Choose Photo instead."
            }
        } else {
            permissionNotice = "Camera permission denied. You can still pick an image via Choose Photo."
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            permissionNotice = null
            scope.launch {
                val bmp = withContext(Dispatchers.IO) {
                    MediaHelper.decodeScaledBitmap(context, uri)
                }
                viewModel.onBitmapReady(bmp)
            }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column {
                Text(
                    text = "Multimodal Scene Scan",
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = "Hazard inspection & medicine label explainer • PREFER_IN_CLOUD",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (!isOnline) {
            item {
                OfflineBannerCard(
                    message = "You are offline. Multimodal cloud vision is paused — use the On-Device Text-Guided Assessment below so you are never stranded."
                )
            }
        }

        // Photo Capture / Picker Card
        item {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, semantic.cardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val bmp = state.selectedBitmap
                    if (bmp != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(210.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(semantic.elevatedSurface)
                        ) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Selected scene photo for analysis",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            IconButton(
                                onClick = { viewModel.clearImage() },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.Black.copy(alpha = 0.65f))
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Clear selected photo",
                                    tint = Color.White
                                )
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(136.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(semantic.elevatedSurface),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DocumentScanner,
                                    contentDescription = null,
                                    tint = semantic.cloud,
                                    modifier = Modifier.size(34.dp)
                                )
                                Text(
                                    text = "Capture a hazard or medicine label",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = "Images are safely scaled before analysis",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val granted = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.CAMERA
                                ) == PackageManager.PERMISSION_GRANTED
                                if (granted) {
                                    runCatching {
                                        val uri = MediaHelper.createCameraCaptureUri(context)
                                        pendingCameraUri = uri
                                        takePictureLauncher.launch(uri)
                                    }.onFailure {
                                        permissionNotice = "Camera app unavailable on this device. Use Choose Photo instead."
                                    }
                                } else {
                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("scene_take_photo_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CameraAlt,
                                contentDescription = "Take photo",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Take Photo")
                        }

                        OutlinedButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("scene_pick_photo_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.PhotoLibrary,
                                contentDescription = "Choose photo from gallery",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Choose Photo")
                        }
                    }

                    if (!permissionNotice.isNullOrBlank()) {
                        Text(
                            text = permissionNotice!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = semantic.warning
                        )
                    }

                    // 3 Quick-Test Prompt Presets
                    Text(
                        text = "Analysis Focus",
                        style = MaterialTheme.typography.titleSmall
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ScenePreset.entries.forEach { preset ->
                            FilterChip(
                                selected = state.selectedPreset == preset,
                                onClick = { viewModel.selectPreset(preset) },
                                label = { Text(preset.title) },
                                modifier = Modifier.testTag("scene_preset_${preset.name.lowercase()}")
                            )
                        }
                    }

                    OutlinedTextField(
                        value = state.customQuestion,
                        onValueChange = viewModel::onCustomQuestionChange,
                        label = {
                            Text(
                                if (state.selectedPreset == ScenePreset.CUSTOM_VISUAL) {
                                    "Your visual question"
                                } else {
                                    "Optional extra context"
                                }
                            )
                        },
                        placeholder = {
                            Text("e.g., Is this downed wire dangerous? Or what dosage warnings apply?")
                        },
                        minLines = 2,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("scene_question_input")
                    )

                    Button(
                        onClick = { viewModel.analyzeScene() },
                        enabled = !state.isAnalyzing,
                        colors = ButtonDefaults.buttonColors(containerColor = semantic.cloud),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("analyze_scene_button")
                    ) {
                        if (state.isAnalyzing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(10.dp))
                            Text("Analyzing Scene...")
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Analyze Scene (PREFER_IN_CLOUD)")
                        }
                    }
                }
            }
        }

        // Graceful Offline Fallback Card -> On-Device Text-Guided Assessment
        if (!isOnline || state.showOfflineFallbackCard) {
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = semantic.warningContainer,
                    border = BorderStroke(1.dp, semantic.warning.copy(alpha = 0.55f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Offline On-Device Text-Guided Assessment",
                            style = MaterialTheme.typography.titleMedium,
                            color = semantic.warning
                        )
                        Text(
                            text = "Describe what you see (chemical symbol, wire hazard, pill imprint, water discoloration) for immediate PREFER_ON_DEVICE safety guidance.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        OutlinedTextField(
                            value = state.offlineDescription,
                            onValueChange = viewModel::onOfflineDescriptionChange,
                            placeholder = { Text("Describe the hazard or label text...") },
                            minLines = 2,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = { viewModel.runOfflineTextAssessment() },
                            enabled = !state.isAnalyzing,
                            colors = ButtonDefaults.buttonColors(containerColor = semantic.onDevice),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("offline_scene_fallback_button")
                        ) {
                            Text("Run On-Device Text Assessment")
                        }
                    }
                }
            }
        }

        // Error Box
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
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Warning, contentDescription = "Error", tint = semantic.emergency)
                        Text(
                            text = state.errorMessage!!,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Multimodal Analysis Output Card
        val guidance = state.guidance
        if (guidance != null) {
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, semantic.cardBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("scene_result_card")
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "Scene Assessment Report",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        state.telemetry?.let { LiveEngineBadge(telemetry = it) }
                        UrgencyBanner(urgency = guidance.urgency)

                        if (guidance.summary.isNotBlank()) {
                            Text(
                                text = guidance.summary,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }

                        if (guidance.steps.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "RECOMMENDED ACTIONS",
                                    style = AegisMonoStyles.badge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                guidance.steps.forEachIndexed { idx, step ->
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = semantic.elevatedSurface,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "${idx + 1}. $step",
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.padding(12.dp)
                                        )
                                    }
                                }
                            }
                        }

                        if (guidance.doNotDo.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = semantic.emergencyContainer,
                                border = BorderStroke(1.dp, semantic.emergency.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "SAFETY WARNINGS",
                                        style = AegisMonoStyles.badge.copy(fontWeight = FontWeight.Bold),
                                        color = semantic.emergency
                                    )
                                    Text(
                                        text = guidance.doNotDo,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = viewModel::toggleSpeakScan,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.height(48.dp)
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
                                Text(if (isSpeaking) "Stop" else "Read")
                            }

                            Button(
                                onClick = { viewModel.saveAssessmentToLog(onRequestSignInForSave) },
                                enabled = !state.isSaving,
                                colors = ButtonDefaults.buttonColors(containerColor = semantic.onDevice),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("save_scene_assessment_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.BookmarkAdd,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Save Assessment")
                            }
                        }

                        if (!state.saveBannerMessage.isNullOrBlank()) {
                            Text(
                                text = state.saveBannerMessage!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = semantic.onDevice
                            )
                        }
                    }
                }
            }
        }

        item {
            DisclaimerFooter(disclaimerText = disclaimerText)
            Spacer(Modifier.height(12.dp))
        }
    }
}
