package com.example.ui.screens

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
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ai.HybridTextResult
import com.example.core.AppCheckDiagnostics
import com.example.data.EngineSource
import com.example.data.HybridOverrideMode
import com.example.data.OnDeviceStatus
import com.example.data.RoutingDecisionLog
import com.example.ui.components.DisclaimerFooter
import com.example.ui.theme.AegisMonoStyles
import com.example.ui.theme.AegisTheme
import com.example.viewmodel.HybridLabViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HybridLabScreen(
    viewModel: HybridLabViewModel,
    disclaimerText: String,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val onDeviceStatus by viewModel.onDeviceStatus.collectAsStateWithLifecycle()
    val onDeviceDetail by viewModel.onDeviceDetail.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val remotePolicy by viewModel.remotePolicy.collectAsStateWithLifecycle()
    val overrideMode by viewModel.overrideMode.collectAsStateWithLifecycle()
    val telemetry by viewModel.telemetry.collectAsStateWithLifecycle()
    val semantic = AegisTheme.colors

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column {
                Text(
                    text = "Hybrid AI Inspector Lab",
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = "Live Firebase AI Logic SDK diagnostics, Remote Config & benchmark",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 1. LIVE SYSTEM DIAGNOSTICS CARD
        item {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, semantic.cardBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("hybrid_diagnostics_card")
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Rounded.Memory, contentDescription = null, tint = semantic.onDevice)
                        Text(
                            text = "Live System Diagnostics",
                            style = MaterialTheme.typography.titleLarge
                        )
                    }

                    val nanoColor = when (onDeviceStatus) {
                        OnDeviceStatus.AVAILABLE -> semantic.onDevice
                        OnDeviceStatus.DOWNLOADABLE, OnDeviceStatus.DOWNLOADING -> semantic.warning
                        else -> semantic.cloud
                    }
                    DiagnosticRow(
                        label = "On-Device Gemini Nano",
                        value = onDeviceStatus.label,
                        valueColor = nanoColor
                    )
                    Text(
                        text = onDeviceDetail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (downloadProgress != null) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            LinearProgressIndicator(
                                progress = { downloadProgress!! },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = semantic.onDevice
                            )
                            Text(
                                text = "Download progress: ${(downloadProgress!! * 100).toInt()}%",
                                style = AegisMonoStyles.badge,
                                color = semantic.onDevice
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.checkStatus() },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("check_nano_status_button")
                        ) {
                            Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Check Status")
                        }

                        Button(
                            onClick = { viewModel.downloadOnDeviceModel() },
                            enabled = onDeviceStatus == OnDeviceStatus.DOWNLOADABLE || onDeviceStatus == OnDeviceStatus.UNAVAILABLE,
                            colors = ButtonDefaults.buttonColors(containerColor = semantic.onDevice),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("download_nano_button")
                        ) {
                            Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Download Nano")
                        }
                    }

                    if (!state.downloadMessage.isNullOrBlank()) {
                        Text(
                            text = state.downloadMessage!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = semantic.warning
                        )
                    }

                    Spacer(Modifier.height(4.dp))
                    DiagnosticRow(
                        label = "Network Connectivity",
                        value = if (isOnline) "ONLINE" else "OFFLINE",
                        valueColor = if (isOnline) semantic.onDevice else semantic.warning
                    )
                    DiagnosticRow(
                        label = "Firebase App Check",
                        value = AppCheckDiagnostics.statusLabel,
                        valueColor = semantic.onDevice
                    )
                    DiagnosticRow(
                        label = "Firestore Offline Cache",
                        value = "ENABLED (Persistent 50MB)",
                        valueColor = semantic.onDevice
                    )
                    DiagnosticRow(
                        label = "Remote Cloud Model",
                        value = remotePolicy.cloudFallbackModel,
                        valueColor = semantic.cloud
                    )
                    DiagnosticRow(
                        label = "Long-Context Threshold",
                        value = "${remotePolicy.longContextThresholdChars} chars",
                        valueColor = semantic.cloud
                    )
                    DiagnosticRow(
                        label = "Policy Source",
                        value = remotePolicy.sourceLabel,
                        valueColor = MaterialTheme.colorScheme.onSurface
                    )

                    OutlinedButton(
                        onClick = { viewModel.refreshRemotePolicy() },
                        enabled = !state.isRefreshingPolicy,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("refresh_remote_policy_button")
                    ) {
                        if (state.isRefreshingPolicy) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Fetching Remote Config...")
                        } else {
                            Icon(Icons.Outlined.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Refresh Remote Policy")
                        }
                    }

                    if (!state.policyMessage.isNullOrBlank()) {
                        Text(
                            text = state.policyMessage!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = semantic.cloud
                        )
                    }
                }
            }
        }

        // 2. INTERACTIVE HYBRID OVERRIDE & BENCHMARK RUNNER
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Rounded.Science, contentDescription = null, tint = semantic.cloud)
                        Text(
                            text = "Routing Override & Live Benchmark",
                            style = MaterialTheme.typography.titleLarge
                        )
                    }

                    Text(
                        text = "Override global InferenceMode across the app or leave on Auto Smart Route:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        HybridOverrideMode.entries.forEach { mode ->
                            FilterChip(
                                selected = overrideMode == mode,
                                onClick = { viewModel.selectOverride(mode) },
                                label = { Text(mode.label, style = AegisMonoStyles.badge) },
                                modifier = Modifier.testTag("override_${mode.name.lowercase()}")
                            )
                        }
                    }

                    Button(
                        onClick = { viewModel.runLiveHybridRoutingTest() },
                        enabled = !state.isRunningBenchmark,
                        colors = ButtonDefaults.buttonColors(containerColor = semantic.cloud),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("run_hybrid_benchmark_button")
                    ) {
                        if (state.isRunningBenchmark) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Running Short vs. Long Routing Test...")
                        } else {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Run Live Hybrid Routing Test")
                        }
                    }

                    // Side-by-Side Benchmark Comparison
                    val benchmark = state.benchmark
                    if (benchmark != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            BenchmarkResultTile(
                                title = "SHORT PROMPT",
                                result = benchmark.shortResult,
                                error = benchmark.shortError,
                                isRunning = state.isRunningBenchmark && benchmark.shortResult == null && benchmark.shortError == null,
                                modifier = Modifier.weight(1f)
                            )
                            BenchmarkResultTile(
                                title = "LONG PROMPT (>THRESHOLD)",
                                result = benchmark.longResult,
                                error = benchmark.longError,
                                isRunning = state.isRunningBenchmark && benchmark.shortResult != null && benchmark.longResult == null && benchmark.longError == null,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // 3. ANALYTICS SUMMARY CARDS
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Session Telemetry",
                        style = MaterialTheme.typography.titleLarge
                    )
                    TextButton(onClick = { viewModel.resetMetrics() }) {
                        Icon(Icons.Outlined.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Reset")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricCard(
                        title = "ON-DEVICE",
                        value = "${telemetry.onDevicePercent}%",
                        subtitle = "${telemetry.onDeviceCount} calls • ${telemetry.avgOnDeviceMs}ms avg",
                        accent = semantic.onDevice,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = "CLOUD",
                        value = "${telemetry.cloudPercent}%",
                        subtitle = "${telemetry.cloudCount} calls • ${telemetry.avgCloudMs}ms avg",
                        accent = semantic.cloud,
                        modifier = Modifier.weight(1f)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = semantic.onDeviceContainer,
                    border = BorderStroke(1.dp, semantic.onDevice.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Estimated Cloud API Calls Saved",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "Total requests logged: ${telemetry.totalRequests}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "${telemetry.estimatedCloudCallsSaved}",
                            style = AegisMonoStyles.metric,
                            color = semantic.onDevice
                        )
                    }
                }
            }
        }

        // 4. CHRONOLOGICAL ROUTING DECISION LOG
        item {
            Text(
                text = "Routing Decision Log (${telemetry.recentDecisions.size})",
                style = MaterialTheme.typography.titleMedium
            )
        }

        if (telemetry.recentDecisions.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, semantic.cardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No AI inference calls recorded yet this session. Run a Triage scenario or tap 'Run Live Hybrid Routing Test' above to inspect live SDK routing telemetry.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            items(telemetry.recentDecisions, key = { it.id }) { entry ->
                RoutingLogRow(entry = entry)
            }
        }

        item {
            DisclaimerFooter(disclaimerText = disclaimerText)
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun DiagnosticRow(
    label: String,
    value: String,
    valueColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = AegisMonoStyles.badge,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun BenchmarkResultTile(
    title: String,
    result: HybridTextResult?,
    error: String?,
    isRunning: Boolean,
    modifier: Modifier = Modifier
) {
    val semantic = AegisTheme.colors
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = semantic.elevatedSurface,
        border = BorderStroke(1.dp, semantic.cardBorder),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                style = AegisMonoStyles.badge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            when {
                isRunning -> {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text("Executing...", style = MaterialTheme.typography.bodySmall)
                }
                error != null -> {
                    Text(error, style = MaterialTheme.typography.bodySmall, color = semantic.emergency)
                }
                result != null -> {
                    val t = result.telemetry
                    val accent = if (t.actualSource == EngineSource.ON_DEVICE) semantic.onDevice else semantic.cloud
                    Text(
                        text = t.actualSource.name,
                        style = AegisMonoStyles.badge.copy(fontWeight = FontWeight.Bold),
                        color = accent
                    )
                    Text(
                        text = "Mode: ${t.requestedMode}",
                        style = AegisMonoStyles.logLine
                    )
                    Text(
                        text = "Latency: ${t.latencyMs}ms • ${t.promptChars} ch",
                        style = AegisMonoStyles.logLine,
                        color = accent
                    )
                    Text(
                        text = t.routingReason,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                else -> {
                    Text("Pending...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    subtitle: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val semantic = AegisTheme.colors
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, semantic.cardBorder),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = AegisMonoStyles.badge,
                color = accent
            )
            Text(
                text = value,
                style = AegisMonoStyles.metric,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RoutingLogRow(entry: RoutingDecisionLog) {
    val semantic = AegisTheme.colors
    val isOnDevice = entry.actualSource == EngineSource.ON_DEVICE
    val accent = if (isOnDevice) semantic.onDevice else semantic.cloud
    val timeText = remember(entry.timestampMs) {
        SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(entry.timestampMs))
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, semantic.cardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (isOnDevice) Icons.Rounded.Bolt else Icons.Rounded.Cloud,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = entry.taskType,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = "$timeText • ${entry.latencyMs}ms",
                    style = AegisMonoStyles.badge,
                    color = accent
                )
            }
            Text(
                text = "Req: ${entry.requestedMode} → Actual: ${entry.actualSource.name} (${entry.promptChars} chars)",
                style = AegisMonoStyles.logLine,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = entry.routingReason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
