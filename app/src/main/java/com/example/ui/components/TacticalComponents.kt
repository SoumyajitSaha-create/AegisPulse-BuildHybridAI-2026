package com.example.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.GppGood
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.core.onGoogleSignInClicked
import com.example.data.EngineSource
import com.example.data.InferenceTelemetry
import com.example.data.OnDeviceStatus
import com.example.data.UrgencyLevel
import com.example.ui.theme.AegisMonoStyles
import com.example.ui.theme.AegisTheme

@Composable
fun GlobalStatusTopBar(
    isOnline: Boolean,
    onDeviceStatus: OnDeviceStatus,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    modifier: Modifier = Modifier
) {
    val semantic = AegisTheme.colors
    val aiStatusText = when (onDeviceStatus) {
        OnDeviceStatus.AVAILABLE -> stringResource(R.string.status_nano_ready)
        OnDeviceStatus.DOWNLOADING -> stringResource(R.string.status_downloading)
        OnDeviceStatus.DOWNLOADABLE -> "Nano Downloadable"
        OnDeviceStatus.CHECKING -> "Probing AI..."
        OnDeviceStatus.UNAVAILABLE -> stringResource(R.string.status_cloud_fallback)
    }
    val aiAccent = when (onDeviceStatus) {
        OnDeviceStatus.AVAILABLE -> semantic.onDevice
        OnDeviceStatus.DOWNLOADING, OnDeviceStatus.DOWNLOADABLE -> semantic.warning
        else -> semantic.cloud
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.statusBarsPadding()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(semantic.onDeviceContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.HealthAndSafety,
                            contentDescription = "AegisPulse logo",
                            tint = semantic.onDevice,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "AegisPulse",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        // Global Status Pill: Connectivity + AI Status
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .testTag("global_status_pill")
                        ) {
                            StatusChipDot(
                                label = if (isOnline) stringResource(R.string.status_online)
                                else stringResource(R.string.status_offline),
                                dotColor = if (isOnline) semantic.onDevice else semantic.warning,
                                textColor = if (isOnline) semantic.onDevice else semantic.warning
                            )
                            Text(
                                text = "•",
                                style = AegisMonoStyles.badge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            StatusChipDot(
                                label = aiStatusText,
                                dotColor = aiAccent,
                                textColor = aiAccent
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onToggleTheme,
                    modifier = Modifier.testTag("theme_toggle_button")
                ) {
                    Icon(
                        imageVector = if (isDarkTheme) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                        contentDescription = stringResource(R.string.cd_toggle_theme),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(semantic.cardBorder)
            )
        }
    }
}

@Composable
private fun StatusChipDot(
    label: String,
    dotColor: Color,
    textColor: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Text(
            text = label,
            style = AegisMonoStyles.badge,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Pinned telemetry provenance badge displayed on every AI output card.
 * Tapping reveals the exact routing explanation tooltip dialog.
 */
@Composable
fun LiveEngineBadge(
    telemetry: InferenceTelemetry,
    modifier: Modifier = Modifier
) {
    val semantic = AegisTheme.colors
    val isOnDevice = telemetry.actualSource == EngineSource.ON_DEVICE
    val accent = if (isOnDevice) semantic.onDevice else semantic.cloud
    val container = if (isOnDevice) semantic.onDeviceContainer else semantic.cloudContainer

    var showTooltipDialog by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = container,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable { showTooltipDialog = true }
            .testTag("live_engine_badge")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = if (isOnDevice) Icons.Rounded.Bolt else Icons.Outlined.Cloud,
                contentDescription = if (isOnDevice) "On-device inference" else "Cloud inference",
                tint = accent,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = buildString {
                    append(if (isOnDevice) "ON-DEVICE" else "CLOUD FALLBACK")
                    append(" • ")
                    append(telemetry.modelLabel)
                    append(" • ")
                    append("${telemetry.latencyMs}ms")
                    if (isOnDevice) append(" • 0 bytes sent")
                },
                style = AegisMonoStyles.badge,
                color = accent,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = "Explain routing decision",
                tint = accent.copy(alpha = 0.8f),
                modifier = Modifier.size(14.dp)
            )
        }
    }

    if (showTooltipDialog) {
        AlertDialog(
            onDismissRequest = { showTooltipDialog = false },
            icon = {
                Icon(
                    imageVector = if (isOnDevice) Icons.Rounded.GppGood else Icons.Rounded.CloudDone,
                    contentDescription = null,
                    tint = accent
                )
            },
            title = {
                Text(
                    text = "Hybrid Routing Provenance",
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TelemetryKeyValue("Requested Mode", telemetry.requestedMode)
                    TelemetryKeyValue("Actual Engine", telemetry.actualSource.name)
                    TelemetryKeyValue("Active Model", telemetry.modelLabel)
                    TelemetryKeyValue("Measured Latency", "${telemetry.latencyMs} ms")
                    TelemetryKeyValue("Prompt Length", "${telemetry.promptChars} chars")
                    TelemetryKeyValue("Data Egress", telemetry.bytesSentHint)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = telemetry.routingReason,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showTooltipDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun TelemetryKeyValue(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = AegisMonoStyles.badge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun UrgencyBanner(
    urgency: UrgencyLevel,
    modifier: Modifier = Modifier,
    showDialButton: Boolean = false
) {
    val semantic = AegisTheme.colors
    val context = LocalContext.current
    val (accent, container) = when (urgency) {
        UrgencyLevel.CRITICAL -> semantic.emergency to semantic.emergencyContainer
        UrgencyLevel.HIGH -> semantic.warning to semantic.warningContainer
        UrgencyLevel.MODERATE -> semantic.warning to semantic.warningContainer
        UrgencyLevel.INFO -> semantic.onDevice to semantic.onDeviceContainer
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = container,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.5f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = if (urgency == UrgencyLevel.CRITICAL || urgency == UrgencyLevel.HIGH) {
                        Icons.Rounded.Warning
                    } else {
                        Icons.Rounded.Shield
                    },
                    contentDescription = urgency.label,
                    tint = accent,
                    modifier = Modifier.size(20.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = urgency.label,
                        style = AegisMonoStyles.badge.copy(fontWeight = FontWeight.Bold),
                        color = accent
                    )
                    if (urgency == UrgencyLevel.CRITICAL || urgency == UrgencyLevel.HIGH) {
                        Text(
                            text = "Contact local emergency services (911 / 112) immediately if life-threatening.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            if (showDialButton) {
                Button(
                    onClick = {
                        val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:112"))
                        runCatching { context.startActivity(dialIntent) }
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = semantic.emergency,
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier.testTag("dial_emergency_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Call,
                        contentDescription = stringResource(R.string.cd_dial_emergency),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.dial_emergency_button),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

@Composable
fun StreamingPulseBanner(
    partialSource: EngineSource?,
    modifier: Modifier = Modifier
) {
    val semantic = AegisTheme.colors
    val transition = rememberInfiniteTransition(label = "stream_pulse")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
        label = "pulse_alpha"
    )
    val accent = if (partialSource == EngineSource.ON_DEVICE) semantic.onDevice else semantic.cloud

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .alpha(alpha)
                .clip(CircleShape)
                .background(accent)
        )
        Text(
            text = when (partialSource) {
                EngineSource.ON_DEVICE -> "Streaming live from On-Device Gemini Nano..."
                EngineSource.IN_CLOUD -> "Streaming live from Cloud Gemini fallback..."
                null -> "Routing via Firebase AI Logic Hybrid SDK..."
            },
            style = AegisMonoStyles.badge,
            color = accent
        )
    }
}

@Composable
fun DisclaimerFooter(
    disclaimerText: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = "Safety disclaimer",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = disclaimerText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun OfflineBannerCard(
    message: String,
    modifier: Modifier = Modifier
) {
    val semantic = AegisTheme.colors
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = semantic.warningContainer,
        border = BorderStroke(1.dp, semantic.warning.copy(alpha = 0.5f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.WifiOff,
                contentDescription = "Offline status",
                tint = semantic.warning,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun GoogleSignInCard(
    title: String,
    subtitle: String,
    errorMessage: String?,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    var isLoading by remember { mutableStateOf(false) }
    val semantic = AegisTheme.colors

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, semantic.cardBorder),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(semantic.cloudContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CloudDone,
                        contentDescription = null,
                        tint = semantic.cloud
                    )
                }
                Column {
                    Text(text = title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (!errorMessage.isNullOrBlank()) {
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = semantic.emergency
                )
            }

            Button(
                onClick = {
                    isLoading = true
                    onGoogleSignInClicked(
                        context = context,
                        credentialManager = credentialManager,
                        scope = scope,
                        onAuthSuccess = {
                            isLoading = false
                            onAuthSuccess()
                        },
                        onAuthError = { msg ->
                            isLoading = false
                            onAuthError(msg)
                        },
                        onAuthCancelled = {
                            isLoading = false
                        }
                    )
                },
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = semantic.cloud),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("google_sign_in_button")
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = stringResource(R.string.sign_in_google),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}
