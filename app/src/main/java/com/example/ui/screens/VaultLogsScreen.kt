package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.GppGood
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.signOutUser
import com.example.data.EngineSource
import com.example.data.IncidentCategory
import com.example.data.IncidentListItem
import com.example.data.UrgencyLevel
import com.example.ui.components.DisclaimerFooter
import com.example.ui.components.GoogleSignInCard
import com.example.ui.components.LiveEngineBadge
import com.example.ui.theme.AegisMonoStyles
import com.example.ui.theme.AegisTheme
import com.example.viewmodel.IncidentTimelineState
import com.example.viewmodel.VaultLogsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val BLOOD_TYPES = listOf("O+", "O-", "A+", "A-", "B+", "B-", "AB+", "AB-")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun VaultLogsScreen(
    viewModel: VaultLogsViewModel,
    disclaimerText: String,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val timelineState by viewModel.timelineState.collectAsStateWithLifecycle()
    val semantic = AegisTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Vault & Incident Logs",
                    style = MaterialTheme.typography.headlineMedium
                )
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("vault_logs_segmented_control")
                ) {
                    SegmentedButton(
                        selected = state.selectedSegment == 0,
                        onClick = { viewModel.selectSegment(0) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        icon = {
                            Icon(
                                imageVector = Icons.Rounded.GppGood,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        modifier = Modifier.testTag("segment_private_vault")
                    ) {
                        Text("Private ICE Vault")
                    }
                    SegmentedButton(
                        selected = state.selectedSegment == 1,
                        onClick = { viewModel.selectSegment(1) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        icon = {
                            Icon(
                                imageVector = Icons.Rounded.History,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        modifier = Modifier.testTag("segment_incident_logs")
                    ) {
                        Text("Incident Logs")
                    }
                }
            }
        }

        if (state.selectedSegment == 0) {
            // ================= SUB-VIEW 1: PRIVATE ICE VAULT (ONLY_ON_DEVICE) =================
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = semantic.onDeviceContainer,
                    border = BorderStroke(1.dp, semantic.onDevice.copy(alpha = 0.55f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("privacy_shield_card")
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Shield,
                            contentDescription = "Strict privacy shield",
                            tint = semantic.onDevice,
                            modifier = Modifier.size(28.dp)
                        )
                        Column {
                            Text(
                                text = "PRIVACY SHIELD • ONLY_ON_DEVICE ENFORCED",
                                style = AegisMonoStyles.badge.copy(fontWeight = FontWeight.Bold),
                                color = semantic.onDevice
                            )
                            Text(
                                text = "Stored strictly on-device in local DataStore. Cloud transmission is blocked by default unless you explicitly consent.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

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
                        Text(
                            text = "Personal Emergency Profile",
                            style = MaterialTheme.typography.titleLarge
                        )

                        OutlinedTextField(
                            value = state.iceProfile.fullName,
                            onValueChange = { v -> viewModel.updateProfile { it.copy(fullName = v) } },
                            label = { Text("Full Name") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("ice_name_input")
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Blood Type",
                                style = MaterialTheme.typography.titleSmall
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                BLOOD_TYPES.forEach { bt ->
                                    FilterChip(
                                        selected = state.iceProfile.bloodType.equals(bt, ignoreCase = true),
                                        onClick = { viewModel.updateProfile { it.copy(bloodType = bt) } },
                                        label = { Text(bt, style = AegisMonoStyles.badge) }
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = state.iceProfile.allergies,
                            onValueChange = { v -> viewModel.updateProfile { it.copy(allergies = v) } },
                            label = { Text("Severe Allergies (Drugs, Food, Insects)") },
                            placeholder = { Text("e.g., Penicillin (anaphylaxis), peanuts") },
                            minLines = 2,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("ice_allergies_input")
                        )

                        OutlinedTextField(
                            value = state.iceProfile.medications,
                            onValueChange = { v -> viewModel.updateProfile { it.copy(medications = v) } },
                            label = { Text("Current Medications & Dosages") },
                            placeholder = { Text("e.g., Albuterol inhaler PRN, EpiPen 0.3mg") },
                            minLines = 2,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = state.iceProfile.medicalConditions,
                            onValueChange = { v -> viewModel.updateProfile { it.copy(medicalConditions = v) } },
                            label = { Text("Medical Conditions") },
                            placeholder = { Text("e.g., Type 1 Diabetes, Asthma") },
                            minLines = 2,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = state.iceProfile.emergencyContacts,
                            onValueChange = { v -> viewModel.updateProfile { it.copy(emergencyContacts = v) } },
                            label = { Text("Emergency Contacts (Name + Phone)") },
                            placeholder = { Text("e.g., Maya Chen (Spouse) +1-555-0192") },
                            minLines = 2,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.saveIceProfileLocally() },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("save_ice_local_button")
                            ) {
                                Icon(Icons.Outlined.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Save")
                            }

                            Button(
                                onClick = { viewModel.generatePrivateResponderCard(allowExplicitCloudConsent = false) },
                                enabled = !state.isGeneratingCard,
                                colors = ButtonDefaults.buttonColors(containerColor = semantic.onDevice),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("generate_ice_card_button")
                            ) {
                                if (state.isGeneratingCard) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Generate ICE Card")
                                }
                            }
                        }

                        if (!state.profileSavedMessage.isNullOrBlank()) {
                            Text(
                                text = state.profileSavedMessage!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = semantic.onDevice
                            )
                        }

                        if (!state.vaultError.isNullOrBlank()) {
                            Text(
                                text = state.vaultError!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = semantic.warning
                            )
                        }
                    }
                }
            }

            // Generated High-Contrast Emergency Lock-Screen Responder Card
            if (state.iceProfile.generatedCardText.isNotBlank()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFF10161F),
                        border = BorderStroke(1.5.dp, semantic.emergency.copy(alpha = 0.7f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("generated_ice_responder_card")
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.LocalHospital,
                                        contentDescription = "Emergency ICE card",
                                        tint = semantic.emergency
                                    )
                                    Text(
                                        text = "EMERGENCY ICE RESPONDER CARD",
                                        style = AegisMonoStyles.badge.copy(fontWeight = FontWeight.Bold),
                                        color = semantic.emergency
                                    )
                                }
                                if (state.iceProfile.bloodType.isNotBlank()) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = semantic.emergency
                                    ) {
                                        Text(
                                            text = state.iceProfile.bloodType,
                                            style = AegisMonoStyles.badge.copy(fontWeight = FontWeight.Bold),
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }

                            state.cardTelemetry?.let { LiveEngineBadge(telemetry = it) }

                            Text(
                                text = state.iceProfile.generatedCardText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFFF0F6FC)
                            )
                        }
                    }
                }
            }
        } else {
            // ================= SUB-VIEW 2: INCIDENT TIMELINE & RESPONDER BRIEF =================
            item {
                val user = currentUser
                if (user == null) {
                    GoogleSignInCard(
                        title = "Cloud Incident Sync & Responder Briefs",
                        subtitle = "Sign in with Google to save Triage & Scene Scan logs with offline Firestore queueing and generate multi-incident Cloud handoff reports.",
                        errorMessage = state.authError,
                        onAuthSuccess = { viewModel.setAuthError(null) },
                        onAuthError = { msg -> viewModel.setAuthError(msg) }
                    )
                } else {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, semantic.cardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Cloud Sync Active",
                                    style = AegisMonoStyles.badge,
                                    color = semantic.onDevice
                                )
                                Text(
                                    text = user.displayName ?: user.email ?: "Signed in with Google",
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                user.email?.let { email ->
                                    Text(
                                        text = email,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            OutlinedButton(
                                onClick = {
                                    signOutUser(
                                        credentialManager = credentialManager,
                                        scope = scope
                                    )
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .height(44.dp)
                                    .testTag("sign_out_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.Logout,
                                    contentDescription = stringResource(R.string.sign_out),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.sign_out))
                            }
                        }
                    }
                }
            }

            when (val ts = timelineState) {
                is IncidentTimelineState.SignedOut -> {
                    // Sign-in card already shown above
                }
                is IncidentTimelineState.Loading -> {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 28.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
                is IncidentTimelineState.Error -> {
                    item {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = semantic.emergencyContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Rounded.Warning, contentDescription = null, tint = semantic.emergency)
                                Text(text = ts.message, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
                is IncidentTimelineState.Ready -> {
                    // Multi-Incident Responder Brief CTA (ONLY_IN_CLOUD)
                    item {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, semantic.cloud.copy(alpha = 0.45f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "Multi-Incident Responder Brief",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "Uses ONLY_IN_CLOUD reasoning to synthesize ${ts.items.size} logged incident(s) into a structured EMS/rescue handoff report.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Button(
                                    onClick = { viewModel.generateMultiIncidentResponderBrief(ts.items) },
                                    enabled = !state.isGeneratingBrief && ts.items.isNotEmpty(),
                                    colors = ButtonDefaults.buttonColors(containerColor = semantic.cloud),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("generate_responder_brief_button")
                                ) {
                                    if (state.isGeneratingBrief) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            color = Color.White,
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text("Synthesizing Handoff Brief...")
                                    } else {
                                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("Generate Responder Brief (ONLY_IN_CLOUD)")
                                    }
                                }

                                if (!state.briefError.isNullOrBlank()) {
                                    Text(
                                        text = state.briefError!!,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = semantic.emergency
                                    )
                                }
                            }
                        }
                    }

                    // Rendered Responder Brief Card + Share Sheet Export
                    if (state.responderBriefText.isNotBlank()) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = semantic.cloudContainer,
                                border = BorderStroke(1.dp, semantic.cloud.copy(alpha = 0.5f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("responder_brief_card")
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = "OFFICIAL RESPONDER HANDOFF REPORT",
                                        style = AegisMonoStyles.badge.copy(fontWeight = FontWeight.Bold),
                                        color = semantic.cloud
                                    )
                                    state.briefTelemetry?.let { LiveEngineBadge(telemetry = it) }
                                    Text(
                                        text = state.responderBriefText,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    OutlinedButton(
                                        onClick = {
                                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_SUBJECT, "AegisPulse Responder Handoff Brief")
                                                putExtra(Intent.EXTRA_TEXT, state.responderBriefText)
                                            }
                                            context.startActivity(
                                                Intent.createChooser(shareIntent, "Share Responder Brief")
                                            )
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .testTag("share_responder_brief_button")
                                    ) {
                                        Icon(Icons.Outlined.Share, contentDescription = "Share handoff brief", modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("Export via Android Share Sheet")
                                    }
                                }
                            }
                        }
                    }

                    // Incident Timeline List
                    if (ts.items.isEmpty()) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, semantic.cardBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.History,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Text(
                                        text = "No Saved Incidents Yet",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = "Run a Triage or Scene Scan and tap 'Save to Log'. Entries queue offline automatically and sync to Firestore when connected.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        items(ts.items, key = { it.incident.id.ifBlank { it.hashCode().toString() } }) { item ->
                            IncidentTimelineCard(
                                item = item,
                                onDelete = { viewModel.deleteIncident(item.incident.id) }
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

    // Transparent Privacy Consent Dialog when ONLY_ON_DEVICE is unavailable
    if (state.showCloudConsentDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissCloudConsentDialog() },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.PrivacyTip,
                    contentDescription = "Privacy consent",
                    tint = semantic.warning
                )
            },
            title = {
                Text("On-Device AI Unavailable")
            },
            text = {
                Text(
                    text = "AegisPulse enforced ONLY_ON_DEVICE mode and blocked transmission of your private medical profile because Gemini Nano is unavailable on this device.\n\nAllow a one-time encrypted Cloud Gemini fallback to format your ICE Responder Card?",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.generatePrivateResponderCard(allowExplicitCloudConsent = true) },
                    colors = ButtonDefaults.buttonColors(containerColor = semantic.cloud),
                    modifier = Modifier.testTag("confirm_one_time_cloud_button")
                ) {
                    Text("Allow One-Time Cloud")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.dismissCloudConsentDialog() },
                    modifier = Modifier.testTag("keep_strictly_local_button")
                ) {
                    Text("Keep Strictly Local")
                }
            }
        )
    }
}

@Composable
private fun IncidentTimelineCard(
    item: IncidentListItem,
    onDelete: () -> Unit
) {
    val semantic = AegisTheme.colors
    val inc = item.incident
    val urgency = UrgencyLevel.fromRaw(inc.urgencyLevel)
    val category = IncidentCategory.fromRaw(inc.category)
    val source = EngineSource.fromRaw(inc.inferenceSource)
    val dateStr = remember(inc.createdAt) {
        val date = inc.createdAt?.toDate() ?: Date()
        SimpleDateFormat("MMM dd, HH:mm", Locale.US).format(date)
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, semantic.cardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    val syncColor = if (item.pendingOfflineSync) semantic.warning else semantic.onDevice
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (item.pendingOfflineSync) semantic.warningContainer else semantic.onDeviceContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (item.pendingOfflineSync) Icons.Rounded.CloudUpload else Icons.Rounded.CloudDone,
                                contentDescription = null,
                                tint = syncColor,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = if (item.pendingOfflineSync) "Pending Offline Sync" else "Synced",
                                style = AegisMonoStyles.badge,
                                color = syncColor
                            )
                        }
                    }

                    Text(
                        text = "${category.label} • $dateStr",
                        style = AegisMonoStyles.logLine,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = "Delete incident log",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Text(
                text = inc.title,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = inc.guidanceSummary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val engineColor = if (source == EngineSource.ON_DEVICE) semantic.onDevice else semantic.cloud
                Text(
                    text = "${source.label} • ${inc.latencyMs}ms",
                    style = AegisMonoStyles.badge,
                    color = engineColor
                )
                Text(
                    text = "•",
                    style = AegisMonoStyles.badge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = urgency.name,
                    style = AegisMonoStyles.badge,
                    color = when (urgency) {
                        UrgencyLevel.CRITICAL -> semantic.emergency
                        UrgencyLevel.HIGH, UrgencyLevel.MODERATE -> semantic.warning
                        UrgencyLevel.INFO -> semantic.onDevice
                    }
                )
            }
        }
    }
}
