package com.example.ai

import android.graphics.Bitmap
import android.util.Log
import com.example.core.ConnectivityObserver
import com.example.data.EngineSource
import com.example.data.HybridOverrideMode
import com.example.data.InferenceTelemetry
import com.example.data.LocalVaultStore
import com.example.data.OnDeviceStatus
import com.example.data.RemotePolicyConfig
import com.example.data.RoutingDecisionLog
import com.example.data.SessionTelemetry
import com.example.data.IncidentRepository
import com.google.firebase.Firebase
import com.google.firebase.ai.DownloadStatus
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.InferenceMode
import com.google.firebase.ai.InferenceSource
import com.google.firebase.ai.OnDeviceConfig
import com.google.firebase.ai.OnDeviceModelStatus
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.content
import com.google.firebase.remoteconfig.remoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

private const val TAG = "HybridAIRouter"

private const val RC_CLOUD_FALLBACK_MODEL = "cloud_fallback_model"
private const val RC_HYBRID_DEFAULT_MODE = "hybrid_default_mode"
private const val RC_LONG_CONTEXT_THRESHOLD = "long_context_cloud_threshold_chars"
private const val RC_EMERGENCY_DISCLAIMER = "emergency_disclaimer_text"

private const val DEFAULT_CLOUD_MODEL = "gemini-3.5-flash-lite"
private const val RATE_LIMIT_FALLBACK_MODEL = "gemini-3.1-flash-lite"
private const val RATE_LIMIT_RETRY_DELAY_MS = 2_000L
private const val DEFAULT_HYBRID_MODE = "PREFER_ON_DEVICE"
private const val DEFAULT_LONG_CONTEXT_CHARS = 1200
private const val DEFAULT_DISCLAIMER =
    "AegisPulse is an AI first-aid & preparedness assistant. It does not replace professional medical advice or official emergency services (911 / 112)."

data class HybridTextResult(
    val text: String,
    val telemetry: InferenceTelemetry
)

sealed interface StreamEvent {
    data class Chunk(val accumulatedText: String, val partialSource: EngineSource?) : StreamEvent
    data class Complete(val fullText: String, val telemetry: InferenceTelemetry) : StreamEvent
}

class OnDeviceUnavailableException(message: String) : Exception(message)

/**
 * Central Hybrid Inference Engine for AegisPulse.
 *
 * Orchestrates Firebase AI Logic (`com.google.firebase:firebase-ai` + `firebase-ai-ondevice`)
 * across all four `InferenceMode` options (`PREFER_ON_DEVICE`, `ONLY_ON_DEVICE`,
 * `PREFER_IN_CLOUD`, `ONLY_IN_CLOUD`), checks on-device Gemini Nano availability safely,
 * collects on-device model downloads, applies Firebase Remote Config policies, and records
 * authentic wall-clock latency + `response.inferenceSource` telemetry on every call.
 */
@OptIn(PublicPreviewAPI::class)
class HybridAIRouter(
    private val connectivityObserver: ConnectivityObserver,
    private val localVaultStore: LocalVaultStore,
    private val incidentRepository: IncidentRepository
) {
    private val routerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _onDeviceStatus = MutableStateFlow(OnDeviceStatus.CHECKING)
    val onDeviceStatus: StateFlow<OnDeviceStatus> = _onDeviceStatus.asStateFlow()

    private val _onDeviceDetail = MutableStateFlow("Probing AICore / Gemini Nano status...")
    val onDeviceDetail: StateFlow<String> = _onDeviceDetail.asStateFlow()

    private val _downloadProgress = MutableStateFlow<Float?>(null)
    val downloadProgress: StateFlow<Float?> = _downloadProgress.asStateFlow()

    private val _remotePolicy = MutableStateFlow(RemotePolicyConfig())
    val remotePolicy: StateFlow<RemotePolicyConfig> = _remotePolicy.asStateFlow()

    private val _overrideMode = MutableStateFlow(HybridOverrideMode.AUTO)
    val overrideMode: StateFlow<HybridOverrideMode> = _overrideMode.asStateFlow()

    private val _telemetry = MutableStateFlow(SessionTelemetry())
    val telemetry: StateFlow<SessionTelemetry> = _telemetry.asStateFlow()

    private val initialProbeDeferred = CompletableDeferred<OnDeviceStatus>()

    private val remoteConfig by lazy {
        Firebase.remoteConfig.apply {
            val settings = remoteConfigSettings {
                minimumFetchIntervalInSeconds = 60L
            }
            setConfigSettingsAsync(settings)
            setDefaultsAsync(
                mapOf(
                    RC_CLOUD_FALLBACK_MODEL to DEFAULT_CLOUD_MODEL,
                    RC_HYBRID_DEFAULT_MODE to DEFAULT_HYBRID_MODE,
                    RC_LONG_CONTEXT_THRESHOLD to DEFAULT_LONG_CONTEXT_CHARS.toLong(),
                    RC_EMERGENCY_DISCLAIMER to DEFAULT_DISCLAIMER
                )
            )
        }
    }

    init {
        routerScope.launch {
            localVaultStore.routingOverride.collect { saved ->
                _overrideMode.value = saved
            }
        }
        routerScope.launch {
            localVaultStore.persistedTelemetryCounts.collect { persisted ->
                _telemetry.update { current ->
                    current.copy(
                        totalRequests = maxOf(current.totalRequests, persisted.totalRequests),
                        onDeviceCount = maxOf(current.onDeviceCount, persisted.onDeviceCount),
                        cloudCount = maxOf(current.cloudCount, persisted.cloudCount),
                        totalOnDeviceLatencyMs = maxOf(current.totalOnDeviceLatencyMs, persisted.totalOnDeviceLatencyMs),
                        totalCloudLatencyMs = maxOf(current.totalCloudLatencyMs, persisted.totalCloudLatencyMs)
                    )
                }
            }
        }
        routerScope.launch {
            checkOnDeviceAvailability()
        }
        routerScope.launch {
            refreshRemotePolicy(silent = true)
        }
    }

    fun setRoutingOverride(mode: HybridOverrideMode) {
        _overrideMode.value = mode
        routerScope.launch {
            localVaultStore.setRoutingOverride(mode)
        }
    }

    fun resetSessionTelemetry() {
        _telemetry.value = SessionTelemetry()
        routerScope.launch {
            localVaultStore.resetTelemetry()
        }
    }

    /**
     * Safely probes on-device Gemini Nano availability via Firebase AI On-Device SDK.
     * Wrapped in comprehensive try/catch + short 3.5s timeout so devices/emulators
     * without AICore smoothly report UNAVAILABLE (Cloud Fallback Active) without hanging.
     */
    suspend fun checkOnDeviceAvailability(): OnDeviceStatus = withContext(Dispatchers.IO) {
        _onDeviceStatus.value = OnDeviceStatus.CHECKING
        val resolved = try {
            val extension = buildModel(InferenceMode.PREFER_ON_DEVICE).onDeviceExtension
            if (extension == null) {
                _onDeviceDetail.value = "AICore / Gemini Nano unavailable on this device — Cloud Fallback Active."
                OnDeviceStatus.UNAVAILABLE
            } else {
                val status = withTimeout(3_500L) {
                    extension.checkStatus()
                }
                when (status) {
                    OnDeviceModelStatus.AVAILABLE -> {
                        _onDeviceDetail.value = "Gemini Nano ready on-device via AICore."
                        OnDeviceStatus.AVAILABLE
                    }
                    OnDeviceModelStatus.DOWNLOADABLE -> {
                        _onDeviceDetail.value = "Gemini Nano supported and ready to download."
                        OnDeviceStatus.DOWNLOADABLE
                    }
                    OnDeviceModelStatus.DOWNLOADING -> {
                        _onDeviceDetail.value = "Gemini Nano download currently in progress."
                        OnDeviceStatus.DOWNLOADING
                    }
                    else -> {
                        _onDeviceDetail.value = "AICore / Gemini Nano unavailable on this device — Cloud Fallback Active."
                        OnDeviceStatus.UNAVAILABLE
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "AICore status check timed out after 3.5s; marking UNAVAILABLE (Cloud Fallback Active)", e)
            _onDeviceDetail.value = "AICore did not respond on this device/emulator — Cloud Fallback Active."
            OnDeviceStatus.UNAVAILABLE
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Log.w(TAG, "On-device AICore probe unavailable; defaulting to Cloud Fallback", t)
            _onDeviceDetail.value = "AICore not detected on this device/emulator — Cloud Fallback Active."
            OnDeviceStatus.UNAVAILABLE
        }
        _onDeviceStatus.value = resolved
        if (!initialProbeDeferred.isCompleted) {
            initialProbeDeferred.complete(resolved)
        }
        resolved
    }

    /**
     * Waits briefly for the initial AICore availability probe if still in flight,
     * then determines the concrete SDK InferenceMode to execute so devices/emulators
     * without AICore never hang inside ML Kit GenAI Prompt IPC.
     */
    private suspend fun resolveExecutableSdkMode(requestedMode: InferenceMode): InferenceMode {
        val deviceStatus = if (_onDeviceStatus.value == OnDeviceStatus.CHECKING) {
            withTimeoutOrNull(3_500L) { initialProbeDeferred.await() } ?: OnDeviceStatus.UNAVAILABLE
        } else {
            _onDeviceStatus.value
        }

        return when (requestedMode) {
            InferenceMode.PREFER_ON_DEVICE -> {
                if (deviceStatus == OnDeviceStatus.AVAILABLE) {
                    InferenceMode.PREFER_ON_DEVICE
                } else {
                    InferenceMode.ONLY_IN_CLOUD
                }
            }
            InferenceMode.PREFER_IN_CLOUD -> {
                if (connectivityObserver.isOnline.value) {
                    InferenceMode.PREFER_IN_CLOUD
                } else if (deviceStatus == OnDeviceStatus.AVAILABLE) {
                    InferenceMode.PREFER_IN_CLOUD
                } else {
                    throw Exception("Device is offline and Gemini Nano is unavailable on this hardware. Reconnect to reach Cloud Gemini fallback.")
                }
            }
            InferenceMode.ONLY_ON_DEVICE -> {
                if (deviceStatus != OnDeviceStatus.AVAILABLE) {
                    throw OnDeviceUnavailableException(
                        "On-device Gemini Nano is unavailable on this device. Switch override to Auto or PREFER_ON_DEVICE to enable Cloud fallback."
                    )
                }
                InferenceMode.ONLY_ON_DEVICE
            }
            else -> requestedMode
        }
    }

    /**
     * Collects the Firebase AI On-Device download Flow and updates progress in real time.
     */
    suspend fun downloadOnDeviceModel(): Result<Unit> {
        return try {
            val extension = buildModel(InferenceMode.PREFER_ON_DEVICE).onDeviceExtension
                ?: run {
                    _downloadProgress.value = null
                    _onDeviceStatus.value = OnDeviceStatus.UNAVAILABLE
                    _onDeviceDetail.value = "AICore on-device extension unavailable on this device."
                    return Result.failure(IllegalStateException("OnDeviceExtension unavailable"))
                }
            _onDeviceStatus.value = OnDeviceStatus.DOWNLOADING
            _downloadProgress.value = 0f
            _onDeviceDetail.value = "Starting Gemini Nano download..."
            var totalBytes = 0L

            extension.download()
                .catch { error ->
                    Log.e(TAG, "On-device model download error", error)
                    _downloadProgress.value = null
                    _onDeviceStatus.value = OnDeviceStatus.UNAVAILABLE
                    _onDeviceDetail.value = "Download unavailable: ${error.localizedMessage ?: "AICore error"}"
                    throw error
                }
                .collect { status ->
                    when (status) {
                        is DownloadStatus.DownloadStarted -> {
                            totalBytes = status.bytesToDownload
                            _downloadProgress.value = 0.05f
                            _onDeviceDetail.value = "Downloading Gemini Nano..."
                        }
                        is DownloadStatus.DownloadInProgress -> {
                            val pct = if (totalBytes > 0L) {
                                (status.totalBytesDownloaded.toFloat() / totalBytes.toFloat()).coerceIn(0.05f, 0.99f)
                            } else {
                                0.5f
                            }
                            _downloadProgress.value = pct
                            _onDeviceDetail.value = "Downloading Gemini Nano (${(pct * 100).toInt()}%)..."
                        }
                        is DownloadStatus.DownloadCompleted -> {
                            _downloadProgress.value = 1f
                            _onDeviceStatus.value = OnDeviceStatus.AVAILABLE
                            _onDeviceDetail.value = "Gemini Nano downloaded and ready on-device."
                        }
                        is DownloadStatus.DownloadFailed -> {
                            _downloadProgress.value = null
                            _onDeviceStatus.value = OnDeviceStatus.UNAVAILABLE
                            _onDeviceDetail.value = "Model download failed: ${status.exception.localizedMessage ?: "AICore error"}"
                        }
                    }
                }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to download on-device model", t)
            _downloadProgress.value = null
            _onDeviceStatus.value = OnDeviceStatus.UNAVAILABLE
            _onDeviceDetail.value = "Download unavailable on this device: ${t.localizedMessage ?: "AICore unsupported"}"
            Result.failure(t)
        }
    }

    /**
     * Fetches and activates live Firebase Remote Config hybrid routing policies.
     */
    suspend fun refreshRemotePolicy(silent: Boolean = false): Result<RemotePolicyConfig> {
        return try {
            val activated = withTimeout(15_000L) {
                remoteConfig.fetchAndActivate().await()
            }
            val modelRaw = remoteConfig.getString(RC_CLOUD_FALLBACK_MODEL).trim()
            val safeModel = if (modelRaw.isBlank() || modelRaw.startsWith("gemini-1.") || modelRaw.startsWith("gemini-2.")) {
                DEFAULT_CLOUD_MODEL
            } else {
                modelRaw
            }
            val defaultMode = remoteConfig.getString(RC_HYBRID_DEFAULT_MODE).trim().ifBlank { DEFAULT_HYBRID_MODE }
            val threshold = remoteConfig.getLong(RC_LONG_CONTEXT_THRESHOLD).toInt().coerceIn(200, 10_000)
            val disclaimer = remoteConfig.getString(RC_EMERGENCY_DISCLAIMER).trim().ifBlank { DEFAULT_DISCLAIMER }

            val updated = RemotePolicyConfig(
                cloudFallbackModel = safeModel,
                hybridDefaultMode = defaultMode,
                longContextThresholdChars = threshold,
                emergencyDisclaimerText = disclaimer,
                lastFetchedMs = System.currentTimeMillis(),
                sourceLabel = if (activated) "Remote Config (Live)" else "Remote Config (Synced)"
            )
            _remotePolicy.value = updated
            Result.success(updated)
        } catch (e: TimeoutCancellationException) {
            if (!silent) {
                Log.w(TAG, "Remote Config refresh timed out; retaining defaults", e)
            }
            val fallback = _remotePolicy.value.copy(
                lastFetchedMs = System.currentTimeMillis(),
                sourceLabel = "Cached / Default Policy"
            )
            _remotePolicy.value = fallback
            Result.failure(e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!silent) {
                Log.w(TAG, "Remote Config refresh failed; retaining defaults", e)
            }
            val fallback = _remotePolicy.value.copy(
                lastFetchedMs = System.currentTimeMillis(),
                sourceLabel = "Cached / Default Policy"
            )
            _remotePolicy.value = fallback
            Result.failure(e)
        }
    }

    private fun currentCloudModelName(): String =
        _remotePolicy.value.cloudFallbackModel.ifBlank { DEFAULT_CLOUD_MODEL }

    private fun buildModel(mode: InferenceMode, modelOverride: String? = null): GenerativeModel {
        val cloudModelName = modelOverride?.ifBlank { null } ?: currentCloudModelName()
        return Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = cloudModelName,
            onDeviceConfig = OnDeviceConfig(mode = mode)
        )
    }

    private fun isRateLimitError(t: Throwable): Boolean {
        return generateSequence(t) { it.cause }.any { err ->
            val cls = err::class.java.simpleName
            val msg = err.message.orEmpty()
            cls.contains("QuotaExceeded", ignoreCase = true) ||
                msg.contains("429") ||
                msg.contains("RESOURCE_EXHAUSTED", ignoreCase = true) ||
                msg.contains("QuotaExceeded", ignoreCase = true) ||
                msg.contains("quota", ignoreCase = true) ||
                msg.contains("rate limit", ignoreCase = true) ||
                msg.contains("rate-limit", ignoreCase = true) ||
                msg.contains("too many requests", ignoreCase = true)
        }
    }

    private fun isModelNotFoundError(t: Throwable): Boolean {
        return generateSequence(t) { it.cause }.any { err ->
            val msg = err.message.orEmpty()
            msg.contains("404") ||
                msg.contains("NOT_FOUND", ignoreCase = true) ||
                msg.contains("is not found", ignoreCase = true) ||
                msg.contains("not supported", ignoreCase = true)
        }
    }

    private fun selectRateLimitRetryModel(primaryModelName: String): String {
        return if (!primaryModelName.equals(RATE_LIMIT_FALLBACK_MODEL, ignoreCase = true)) {
            RATE_LIMIT_FALLBACK_MODEL
        } else {
            DEFAULT_CLOUD_MODEL
        }
    }

    private data class EffectiveRoute(
        val mode: InferenceMode,
        val modeLabel: String,
        val reason: String
    )

    private fun resolveRoute(
        defaultMode: InferenceMode,
        promptChars: Int,
        strictPrivacyLocked: Boolean = false,
        ignoreGlobalOverride: Boolean = false
    ): EffectiveRoute {
        val online = connectivityObserver.isOnline.value
        val policy = _remotePolicy.value
        val override = _overrideMode.value

        if (strictPrivacyLocked) {
            return EffectiveRoute(
                mode = InferenceMode.ONLY_ON_DEVICE,
                modeLabel = "ONLY_ON_DEVICE",
                reason = "Strict Privacy Mode enforced in Private ICE Vault (zero cloud egress)."
            )
        }

        if (!ignoreGlobalOverride && override != HybridOverrideMode.AUTO) {
            val forced = when (override) {
                HybridOverrideMode.PREFER_ON_DEVICE -> InferenceMode.PREFER_ON_DEVICE
                HybridOverrideMode.ONLY_ON_DEVICE -> InferenceMode.ONLY_ON_DEVICE
                HybridOverrideMode.PREFER_IN_CLOUD -> InferenceMode.PREFER_IN_CLOUD
                HybridOverrideMode.ONLY_IN_CLOUD -> InferenceMode.ONLY_IN_CLOUD
                HybridOverrideMode.AUTO -> defaultMode
            }
            return EffectiveRoute(
                mode = forced,
                modeLabel = override.name,
                reason = "Manual override active from Hybrid Lab (${override.name})."
            )
        }

        // Auto Smart Route with Remote Config long-context threshold escalation
        if (defaultMode == InferenceMode.PREFER_ON_DEVICE &&
            promptChars > policy.longContextThresholdChars &&
            online
        ) {
            return EffectiveRoute(
                mode = InferenceMode.PREFER_IN_CLOUD,
                modeLabel = "PREFER_IN_CLOUD",
                reason = "Prompt ($promptChars chars) exceeded Remote Config threshold (${policy.longContextThresholdChars} chars); escalated to Cloud."
            )
        }

        val reason = when (defaultMode) {
            InferenceMode.PREFER_ON_DEVICE ->
                if (_onDeviceStatus.value == OnDeviceStatus.AVAILABLE) {
                    "Auto Smart Route: On-device Gemini Nano prioritized for low-latency offline triage."
                } else {
                    "Auto Smart Route: PREFER_ON_DEVICE requested; Gemini Nano unavailable so SDK routed to Cloud fallback."
                }
            InferenceMode.ONLY_ON_DEVICE ->
                "Strict on-device execution requested."
            InferenceMode.PREFER_IN_CLOUD ->
                if (online) {
                    "Auto Smart Route: PREFER_IN_CLOUD selected for vision/deep reasoning."
                } else {
                    "Device offline; attempting on-device fallback via PREFER_IN_CLOUD."
                }
            InferenceMode.ONLY_IN_CLOUD ->
                "ONLY_IN_CLOUD selected for multi-incident synthesis across synced logs."
            else ->
                "Hybrid inference mode selected by HybridAIRouter."
        }

        return EffectiveRoute(
            mode = defaultMode,
            modeLabel = modeToName(defaultMode),
            reason = reason
        )
    }

    /**
     * Streams emergency first-aid triage guidance word-by-word via `generateContentStream`.
     */
    fun streamTriageGuidance(
        scenarioPrompt: String,
        taskType: String = "Emergency Triage"
    ): Flow<StreamEvent> = flow {
        val fullPrompt = buildString {
            appendLine("You are AegisPulse, a calm, concise emergency first-aid & field preparedness assistant.")
            appendLine("Respond strictly using these labeled sections in plain text (no markdown symbols):")
            appendLine("URGENCY: <CRITICAL or HIGH or MODERATE or INFO>")
            appendLine("SUMMARY: <1-2 calm sentences on immediate priority>")
            appendLine("STEPS:")
            appendLine("1. <Clear short actionable step>")
            appendLine("2. <Clear short actionable step>")
            appendLine("(Provide 4 to 6 concise numbered steps)")
            appendLine("DO_NOT: <2-3 critical things NOT to do>")
            appendLine()
            appendLine("Scenario: $scenarioPrompt")
        }

        val route = resolveRoute(
            defaultMode = InferenceMode.PREFER_ON_DEVICE,
            promptChars = scenarioPrompt.length
        )
        val sdkMode = resolveExecutableSdkMode(route.mode)
        val primaryModelName = currentCloudModelName()
        var usedModelName = primaryModelName
        var rateLimitFallbackUsed = false
        val startMs = System.currentTimeMillis()
        val accumulated = StringBuilder()
        var detectedSource: InferenceSource? = null

        suspend fun collectStream(model: GenerativeModel) {
            model.generateContentStream(fullPrompt).collect { chunk ->
                detectedSource = chunk.inferenceSource
                val delta = chunk.text.orEmpty()
                if (delta.isNotEmpty()) {
                    accumulated.append(delta)
                    emit(
                        StreamEvent.Chunk(
                            accumulatedText = accumulated.toString(),
                            partialSource = chunk.inferenceSource.toEngineSource()
                        )
                    )
                }
            }
        }

        try {
            withTimeout(60_000L) {
                try {
                    collectStream(buildModel(sdkMode, primaryModelName))
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    if (isRateLimitError(e) && accumulated.isEmpty() && sdkMode != InferenceMode.ONLY_ON_DEVICE) {
                        val retryModel = selectRateLimitRetryModel(primaryModelName)
                        Log.w(TAG, "Streaming triage hit rate limit on $primaryModelName; backing off 2s and retrying on $retryModel", e)
                        delay(RATE_LIMIT_RETRY_DELAY_MS)
                        try {
                            usedModelName = retryModel
                            rateLimitFallbackUsed = true
                            collectStream(buildModel(InferenceMode.ONLY_IN_CLOUD, retryModel))
                        } catch (fallbackErr: Exception) {
                            if (fallbackErr is CancellationException) throw fallbackErr
                            if (isModelNotFoundError(fallbackErr) && retryModel != primaryModelName) {
                                Log.w(TAG, "Fallback model $retryModel unavailable; retrying $primaryModelName after backoff", fallbackErr)
                                usedModelName = primaryModelName
                                collectStream(buildModel(InferenceMode.ONLY_IN_CLOUD, primaryModelName))
                            } else {
                                throw fallbackErr
                            }
                        }
                    } else {
                        throw e
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Streaming triage timed out", e)
            throw Exception("Guidance request timed out after 60 seconds. Check signal and try again.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Streaming triage failed", e)
            throw mapActionableAiError(e, route.mode)
        }

        val elapsedMs = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
        val finalText = accumulated.toString().trim()
        if (finalText.isBlank()) {
            throw Exception("AI model returned an empty response. Please try again.")
        }

        val engineSource = (detectedSource ?: inferSourceFallback(sdkMode)).toEngineSource()
        val reason = if (rateLimitFallbackUsed && engineSource == EngineSource.IN_CLOUD) {
            "Rate-limit resilient fallback: retried after 2s backoff on $usedModelName. ${route.reason}"
        } else {
            finalizeRoutingReason(route.reason, engineSource)
        }
        val telemetry = buildAndRecordTelemetry(
            taskType = taskType,
            requestedMode = route.modeLabel,
            actualSource = engineSource,
            latencyMs = elapsedMs,
            promptChars = scenarioPrompt.length,
            routingReason = reason,
            modelNameOverride = usedModelName
        )
        emit(StreamEvent.Complete(finalText, telemetry))
    }.flowOn(Dispatchers.IO)

    /**
     * Translates vital SOS distress phrases via `PREFER_ON_DEVICE`.
     */
    suspend fun translateSosPhrase(
        phrase: String,
        targetLanguage: String
    ): HybridTextResult {
        val prompt = """
            Translate this urgent emergency SOS phrase into $targetLanguage.
            Provide:
            1) Clear native translation in $targetLanguage
            2) Simple phonetic pronunciation guide in parentheses
            Keep it concise, accurate, and ready to display full-screen to a bystander or medic.
            Phrase: "$phrase"
        """.trimIndent()

        return executeUnaryText(
            prompt = prompt,
            measuredChars = phrase.length,
            defaultMode = InferenceMode.PREFER_ON_DEVICE,
            taskType = "SOS Phrase Translation",
            timeoutMs = 20_000L
        )
    }

    /**
     * Runs multimodal Scene Scan image + prompt analysis via `PREFER_IN_CLOUD`.
     */
    suspend fun analyzeSceneImage(
        bitmap: Bitmap,
        userPrompt: String
    ): HybridTextResult = withContext(Dispatchers.IO) {
        val structuredPrompt = """
            You are AegisPulse Field Scene Scanner. Analyze this image carefully for safety, hazards, or first-aid/medication label clarity.
            Respond cleanly using these sections (no markdown stars):
            URGENCY: <CRITICAL or HIGH or MODERATE or INFO>
            SUMMARY: <2 crisp sentences describing what is visible and primary risk or purpose>
            STEPS:
            1. <Actionable safety or usage step>
            2. <Actionable safety or usage step>
            3. <Actionable safety or usage step>
            4. <Actionable safety or usage step>
            DO_NOT: <Key warnings or contraindications to avoid>

            User request: $userPrompt
        """.trimIndent()

        val route = resolveRoute(
            defaultMode = InferenceMode.PREFER_IN_CLOUD,
            promptChars = userPrompt.length
        )
        val sdkMode = resolveExecutableSdkMode(route.mode)
        val primaryModelName = currentCloudModelName()
        var usedModelName = primaryModelName
        var rateLimitFallbackUsed = false
        val startMs = System.currentTimeMillis()

        val inputContent: Content = content {
            image(bitmap)
            text(structuredPrompt)
        }

        val response = try {
            withTimeout(60_000L) {
                try {
                    buildModel(sdkMode, primaryModelName).generateContent(inputContent)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    if (isRateLimitError(e) && sdkMode != InferenceMode.ONLY_ON_DEVICE) {
                        val retryModel = selectRateLimitRetryModel(primaryModelName)
                        Log.w(TAG, "Scene scan hit rate limit on $primaryModelName; backing off 2s and retrying on $retryModel", e)
                        delay(RATE_LIMIT_RETRY_DELAY_MS)
                        try {
                            usedModelName = retryModel
                            rateLimitFallbackUsed = true
                            buildModel(InferenceMode.ONLY_IN_CLOUD, retryModel).generateContent(inputContent)
                        } catch (fallbackErr: Exception) {
                            if (fallbackErr is CancellationException) throw fallbackErr
                            if (isModelNotFoundError(fallbackErr) && retryModel != primaryModelName) {
                                Log.w(TAG, "Fallback model $retryModel unavailable; retrying $primaryModelName after backoff", fallbackErr)
                                usedModelName = primaryModelName
                                buildModel(InferenceMode.ONLY_IN_CLOUD, primaryModelName).generateContent(inputContent)
                            } else {
                                throw fallbackErr
                            }
                        }
                    } else {
                        throw e
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Scene scan timed out", e)
            throw Exception("Scene analysis timed out after 60 seconds. Please retry or use Offline Text Assessment.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Multimodal scene scan failed", e)
            throw mapActionableAiError(e, route.mode)
        }

        val elapsedMs = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
        val text = response.text?.trim().orEmpty()
        if (text.isBlank()) {
            throw Exception("Scene analyzer returned an empty response.")
        }

        val engineSource = response.inferenceSource.toEngineSource()
        val reason = if (rateLimitFallbackUsed && engineSource == EngineSource.IN_CLOUD) {
            "Rate-limit resilient fallback: retried after 2s backoff on $usedModelName. ${route.reason}"
        } else {
            finalizeRoutingReason(route.reason, engineSource)
        }
        val telemetry = buildAndRecordTelemetry(
            taskType = "Multimodal Scene Scan",
            requestedMode = route.modeLabel,
            actualSource = engineSource,
            latencyMs = elapsedMs,
            promptChars = userPrompt.length,
            routingReason = reason,
            modelNameOverride = usedModelName
        )
        HybridTextResult(text, telemetry)
    }

    /**
     * Offline text-guided fallback when Scene Scan cannot reach cloud vision.
     */
    suspend fun runOfflineSceneTextFallback(description: String): HybridTextResult {
        val prompt = """
            Offline Field Safety Assessment (no image available). Based on the user's description, give clear safety precautions:
            URGENCY: <CRITICAL or HIGH or MODERATE or INFO>
            SUMMARY: <Brief assessment>
            STEPS:
            1. <Step>
            2. <Step>
            3. <Step>
            DO_NOT: <Avoidance warning>

            Description: $description
        """.trimIndent()

        return executeUnaryText(
            prompt = prompt,
            measuredChars = description.length,
            defaultMode = InferenceMode.PREFER_ON_DEVICE,
            taskType = "Offline Scene Text Fallback"
        )
    }

    /**
     * Generates a Private ICE Responder Lock-Screen Card.
     * Enforces `ONLY_ON_DEVICE` by default so zero medical bytes leave the device.
     * Throws `OnDeviceUnavailableException` when Gemini Nano is unavailable and
     * `allowExplicitCloudConsent` is false, so UI can present an explicit consent dialog.
     */
    suspend fun generatePrivateIceCard(
        profilePrompt: String,
        allowExplicitCloudConsent: Boolean
    ): HybridTextResult = withContext(Dispatchers.IO) {
        val prompt = """
            Distill the following personal medical profile into an ultra-scannable Emergency Responder ICE Card for paramedics.
            Format cleanly with short uppercase labels (BLOOD TYPE, CRITICAL ALLERGIES, MEDICATIONS, CONDITIONS, EMERGENCY CONTACTS, RESPONDER NOTE).
            Keep it concise (under 140 words), accurate, and free of markdown formatting.

            Profile:
            $profilePrompt
        """.trimIndent()

        if (!allowExplicitCloudConsent) {
            val route = resolveRoute(
                defaultMode = InferenceMode.ONLY_ON_DEVICE,
                promptChars = profilePrompt.length,
                strictPrivacyLocked = true
            )
            // Fail fast without hanging on AICore IPC when Gemini Nano is confirmed unavailable
            val deviceStatus = if (_onDeviceStatus.value == OnDeviceStatus.CHECKING) {
                withTimeoutOrNull(3_500L) { initialProbeDeferred.await() } ?: OnDeviceStatus.UNAVAILABLE
            } else {
                _onDeviceStatus.value
            }
            if (deviceStatus != OnDeviceStatus.AVAILABLE) {
                throw OnDeviceUnavailableException(
                    "On-device Gemini Nano is unavailable on this device. Your medical data was NOT sent to the cloud."
                )
            }

            val model = buildModel(InferenceMode.ONLY_ON_DEVICE)
            val startMs = System.currentTimeMillis()
            val response = try {
                withTimeout(45_000L) {
                    model.generateContent(prompt)
                }
            } catch (e: TimeoutCancellationException) {
                Log.w(TAG, "ONLY_ON_DEVICE ICE generation timed out; blocking cloud transmission pending consent", e)
                throw OnDeviceUnavailableException(
                    "On-device Gemini Nano did not respond in time. Your medical data was NOT sent to the cloud."
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "ONLY_ON_DEVICE ICE generation unavailable; blocking cloud transmission pending consent", e)
                throw OnDeviceUnavailableException(
                    "On-device Gemini Nano is unavailable on this device. Your medical data was NOT sent to the cloud."
                )
            }

            val elapsedMs = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
            val text = response.text?.trim().orEmpty()
            if (text.isBlank()) {
                throw OnDeviceUnavailableException("On-device model returned empty output.")
            }
            val engineSource = response.inferenceSource.toEngineSource()
            val telemetry = buildAndRecordTelemetry(
                taskType = "Private ICE Vault Card",
                requestedMode = "ONLY_ON_DEVICE",
                actualSource = engineSource,
                latencyMs = elapsedMs,
                promptChars = profilePrompt.length,
                routingReason = route.reason
            )
            HybridTextResult(text, telemetry)
        } else {
            // Explicit one-time user consent granted for encrypted cloud fallback
            executeUnaryText(
                prompt = prompt,
                measuredChars = profilePrompt.length,
                defaultMode = InferenceMode.PREFER_ON_DEVICE,
                taskType = "Private ICE Card (Consented Fallback)",
                ignoreGlobalOverride = true,
                customReasonOverride = "User explicitly approved one-time encrypted Cloud fallback after ONLY_ON_DEVICE block."
            )
        }
    }

    /**
     * Synthesizes multiple Firestore incident logs into a professional responder handoff report
     * strictly using `InferenceMode.ONLY_IN_CLOUD`.
     */
    suspend fun generateMultiIncidentResponderBrief(incidentsSummary: String): HybridTextResult {
        val prompt = """
            You are AegisPulse Incident Command Synthesizer. Combine these logged field incidents into a concise, professional Responder Handoff Report.
            Use these exact sections in plain text (no markdown stars):
            SITUATION: <Overall scene status and highest severity>
            TIMELINE: <Bullet summary of logged events in chronological order>
            ACTIONS TAKEN: <Key first-aid or safety actions already completed>
            PRIORITY NEEDS: <Immediate medical, rescue, or supply needs for incoming responders>

            Logged Incidents:
            $incidentsSummary
        """.trimIndent()

        return executeUnaryText(
            prompt = prompt,
            measuredChars = incidentsSummary.length,
            defaultMode = InferenceMode.ONLY_IN_CLOUD,
            taskType = "Multi-Incident Responder Brief",
            ignoreGlobalOverride = false
        )
    }

    /**
     * Executes a live benchmark prompt for the Hybrid Lab Inspector.
     */
    suspend fun runHybridLabBenchmarkPrompt(
        label: String,
        promptText: String
    ): HybridTextResult {
        return executeUnaryText(
            prompt = promptText,
            measuredChars = promptText.length,
            defaultMode = InferenceMode.PREFER_ON_DEVICE,
            taskType = label
        )
    }

    private suspend fun executeUnaryText(
        prompt: String,
        measuredChars: Int,
        defaultMode: InferenceMode,
        taskType: String,
        ignoreGlobalOverride: Boolean = false,
        customReasonOverride: String? = null,
        timeoutMs: Long = 60_000L
    ): HybridTextResult = withContext(Dispatchers.IO) {
        val route = resolveRoute(
            defaultMode = defaultMode,
            promptChars = measuredChars,
            ignoreGlobalOverride = ignoreGlobalOverride
        )
        val primaryModelName = currentCloudModelName()
        var usedModelName = primaryModelName
        var rateLimitFallbackUsed = false
        val startMs = System.currentTimeMillis()

        suspend fun runCloudWithRateLimitFallback(modeForCloud: InferenceMode): com.google.firebase.ai.type.GenerateContentResponse {
            return try {
                buildModel(modeForCloud, primaryModelName).generateContent(prompt)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (isRateLimitError(e)) {
                    val retryModel = selectRateLimitRetryModel(primaryModelName)
                    Log.w(TAG, "$taskType hit rate limit on $primaryModelName; backing off 2s and retrying on $retryModel", e)
                    delay(RATE_LIMIT_RETRY_DELAY_MS)
                    try {
                        usedModelName = retryModel
                        rateLimitFallbackUsed = true
                        buildModel(InferenceMode.ONLY_IN_CLOUD, retryModel).generateContent(prompt)
                    } catch (fallbackErr: Exception) {
                        if (fallbackErr is CancellationException) throw fallbackErr
                        if (isModelNotFoundError(fallbackErr) && retryModel != primaryModelName) {
                            Log.w(TAG, "Fallback model $retryModel unavailable; retrying $primaryModelName after backoff", fallbackErr)
                            usedModelName = primaryModelName
                            buildModel(InferenceMode.ONLY_IN_CLOUD, primaryModelName).generateContent(prompt)
                        } else {
                            throw fallbackErr
                        }
                    }
                } else {
                    throw e
                }
            }
        }

        val response = try {
            withTimeout(timeoutMs) {
                val sdkMode = resolveExecutableSdkMode(route.mode)
                if (sdkMode == InferenceMode.PREFER_ON_DEVICE && connectivityObserver.isOnline.value) {
                    try {
                        withTimeout((timeoutMs / 2L).coerceAtLeast(8_000L)) {
                            buildModel(sdkMode, primaryModelName).generateContent(prompt)
                        }
                    } catch (e: CancellationException) {
                        if (e !is TimeoutCancellationException) throw e
                        Log.w(TAG, "$taskType on-device attempt timed out; falling back to Cloud Gemini", e)
                        runCloudWithRateLimitFallback(InferenceMode.ONLY_IN_CLOUD)
                    } catch (e: Exception) {
                        Log.w(TAG, "$taskType on-device attempt failed; falling back to Cloud Gemini", e)
                        runCloudWithRateLimitFallback(InferenceMode.ONLY_IN_CLOUD)
                    }
                } else if (sdkMode == InferenceMode.ONLY_ON_DEVICE) {
                    buildModel(sdkMode, primaryModelName).generateContent(prompt)
                } else {
                    runCloudWithRateLimitFallback(sdkMode)
                }
            }
        } catch (e: TimeoutCancellationException) {
            val seconds = (timeoutMs / 1_000L).coerceAtLeast(1L)
            Log.e(TAG, "$taskType timed out after ${seconds}s", e)
            throw Exception("$taskType timed out after $seconds seconds. Check connectivity and try again.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "$taskType failed", e)
            throw mapActionableAiError(e, route.mode)
        }

        val elapsedMs = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
        val text = response.text?.trim().orEmpty()
        if (text.isBlank()) {
            throw Exception("Model returned an empty response for $taskType.")
        }

        val engineSource = response.inferenceSource.toEngineSource()
        val baseReason = customReasonOverride ?: finalizeRoutingReason(route.reason, engineSource)
        val reason = if (rateLimitFallbackUsed && engineSource == EngineSource.IN_CLOUD) {
            "Rate-limit resilient fallback: retried after 2s backoff on $usedModelName. $baseReason"
        } else {
            baseReason
        }
        val telemetry = buildAndRecordTelemetry(
            taskType = taskType,
            requestedMode = route.modeLabel,
            actualSource = engineSource,
            latencyMs = elapsedMs,
            promptChars = measuredChars,
            routingReason = reason,
            modelNameOverride = usedModelName
        )
        HybridTextResult(text, telemetry)
    }

    private fun buildAndRecordTelemetry(
        taskType: String,
        requestedMode: String,
        actualSource: EngineSource,
        latencyMs: Long,
        promptChars: Int,
        routingReason: String,
        modelNameOverride: String? = null
    ): InferenceTelemetry {
        val cloudModel = modelNameOverride?.ifBlank { null } ?: currentCloudModelName()
        val modelLabel = if (actualSource == EngineSource.ON_DEVICE) {
            "Gemini Nano"
        } else {
            formatModelLabel(cloudModel)
        }
        val bytesHint = if (actualSource == EngineSource.ON_DEVICE) {
            "0 bytes sent"
        } else {
            "Encrypted Cloud"
        }

        val decision = RoutingDecisionLog(
            id = UUID.randomUUID().toString(),
            timestampMs = System.currentTimeMillis(),
            taskType = taskType,
            requestedMode = requestedMode,
            actualSource = actualSource,
            latencyMs = latencyMs,
            promptChars = promptChars,
            routingReason = routingReason
        )

        _telemetry.update { current ->
            current.copy(
                totalRequests = current.totalRequests + 1,
                onDeviceCount = current.onDeviceCount + if (actualSource == EngineSource.ON_DEVICE) 1 else 0,
                cloudCount = current.cloudCount + if (actualSource == EngineSource.IN_CLOUD) 1 else 0,
                totalOnDeviceLatencyMs = current.totalOnDeviceLatencyMs + if (actualSource == EngineSource.ON_DEVICE) latencyMs else 0L,
                totalCloudLatencyMs = current.totalCloudLatencyMs + if (actualSource == EngineSource.IN_CLOUD) latencyMs else 0L,
                recentDecisions = (listOf(decision) + current.recentDecisions).take(40)
            )
        }

        routerScope.launch {
            localVaultStore.recordTelemetry(actualSource, latencyMs)
            incidentRepository.syncTelemetryEntry(decision)
        }

        return InferenceTelemetry(
            requestedMode = requestedMode,
            actualSource = actualSource,
            modelLabel = modelLabel,
            latencyMs = latencyMs,
            promptChars = promptChars,
            routingReason = routingReason,
            bytesSentHint = bytesHint
        )
    }

    private fun finalizeRoutingReason(baseReason: String, actual: EngineSource): String {
        return if (actual == EngineSource.ON_DEVICE) {
            "Executed locally on Gemini Nano (0 bytes sent). $baseReason"
        } else {
            baseReason
        }
    }

    private fun inferSourceFallback(mode: InferenceMode): InferenceSource {
        return if (mode == InferenceMode.ONLY_ON_DEVICE) {
            InferenceSource.ON_DEVICE
        } else {
            InferenceSource.IN_CLOUD
        }
    }

    private fun InferenceSource.toEngineSource(): EngineSource = when (this) {
        InferenceSource.ON_DEVICE -> EngineSource.ON_DEVICE
        InferenceSource.IN_CLOUD -> EngineSource.IN_CLOUD
        else -> EngineSource.IN_CLOUD
    }

    private fun modeToName(mode: InferenceMode): String = when (mode) {
        InferenceMode.PREFER_ON_DEVICE -> "PREFER_ON_DEVICE"
        InferenceMode.ONLY_ON_DEVICE -> "ONLY_ON_DEVICE"
        InferenceMode.PREFER_IN_CLOUD -> "PREFER_IN_CLOUD"
        InferenceMode.ONLY_IN_CLOUD -> "ONLY_IN_CLOUD"
        else -> "PREFER_ON_DEVICE"
    }

    private fun formatModelLabel(raw: String): String {
        return raw.split("-").joinToString(" ") { part ->
            part.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
    }

    private fun mapActionableAiError(e: Exception, mode: InferenceMode): Exception {
        val msg = e.localizedMessage.orEmpty()
        val friendly = when {
            mode == InferenceMode.ONLY_ON_DEVICE ->
                "On-device Gemini Nano is unavailable on this device. Switch override to Auto or PREFER_ON_DEVICE to enable Cloud fallback."
            !connectivityObserver.isOnline.value ->
                "Device is offline and Gemini Nano is not available on this hardware. Reconnect to reach Cloud Gemini fallback."
            msg.contains("403") || msg.contains("App Check", ignoreCase = true) ->
                "Firebase App Check attestation rejected (HTTP 403). Verify Firebase AI Logic provisioning and debug token."
            msg.contains("quota", ignoreCase = true) || msg.contains("429") ->
                "Gemini API rate limit reached. Wait a moment and retry."
            else ->
                msg.ifBlank { "Unable to complete hybrid AI inference. Please check connectivity and retry." }
        }
        return Exception(friendly, e)
    }
}
