package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.HybridAIRouter
import com.example.ai.StreamEvent
import com.example.core.TtsSpeaker
import com.example.data.EmergencyScenario
import com.example.data.EngineSource
import com.example.data.IncidentCategory
import com.example.data.IncidentRepository
import com.example.data.InferenceTelemetry
import com.example.data.TriageGuidance
import com.example.data.TriageParser
import com.example.data.UrgencyLevel
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

data class SosTranslationState(
    val selectedPhrase: String = "I need a doctor immediately.",
    val selectedLanguage: String = "Spanish",
    val isTranslating: Boolean = false,
    val translatedOutput: String = "",
    val telemetry: InferenceTelemetry? = null,
    val errorMessage: String? = null
)

data class TriageUiState(
    val activeTitle: String = "",
    val activePrompt: String = "",
    val customInput: String = "",
    val isStreaming: Boolean = false,
    val partialSource: EngineSource? = null,
    val guidance: TriageGuidance? = null,
    val checkedSteps: Set<Int> = emptySet(),
    val telemetry: InferenceTelemetry? = null,
    val errorMessage: String? = null,
    val saveBannerMessage: String? = null,
    val isSaving: Boolean = false,
    val sosState: SosTranslationState = SosTranslationState()
)

class TriageViewModel(
    private val router: HybridAIRouter,
    private val incidentRepository: IncidentRepository,
    val ttsSpeaker: TtsSpeaker
) : ViewModel() {

    private val _uiState = MutableStateFlow(TriageUiState())
    val uiState: StateFlow<TriageUiState> = _uiState.asStateFlow()

    val isSpeaking: StateFlow<Boolean> = ttsSpeaker.isSpeaking

    private var activeStreamJob: Job? = null
    private var activeSosJob: Job? = null

    fun onCustomInputChange(value: String) {
        _uiState.update { it.copy(customInput = value) }
    }

    fun runQuickScenario(scenario: EmergencyScenario) {
        if (_uiState.value.isStreaming) return
        startStreamingTriage(
            title = scenario.title,
            prompt = scenario.prompt,
            fallbackUrgency = scenario.defaultUrgency,
            taskType = "Triage: ${scenario.title}"
        )
    }

    fun runCustomTriage() {
        if (_uiState.value.isStreaming) return
        val query = _uiState.value.customInput.trim()
        if (query.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Describe your situation or tap a quick emergency card above.") }
            return
        }
        startStreamingTriage(
            title = query.take(48).let { if (query.length > 48) "$it..." else it },
            prompt = query,
            fallbackUrgency = UrgencyLevel.HIGH,
            taskType = "Custom Emergency Triage"
        )
    }

    fun applyVoiceTranscription(spokenText: String) {
        if (spokenText.isBlank() || _uiState.value.isStreaming) return
        _uiState.update { it.copy(customInput = spokenText) }
        startStreamingTriage(
            title = spokenText.take(48).let { if (spokenText.length > 48) "$it..." else it },
            prompt = spokenText,
            fallbackUrgency = UrgencyLevel.HIGH,
            taskType = "Voice Emergency Triage"
        )
    }

    private fun startStreamingTriage(
        title: String,
        prompt: String,
        fallbackUrgency: UrgencyLevel,
        taskType: String
    ) {
        ttsSpeaker.stop()
        activeStreamJob?.cancel()
        activeStreamJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    activeTitle = title,
                    activePrompt = prompt,
                    isStreaming = true,
                    partialSource = null,
                    guidance = TriageGuidance(urgency = fallbackUrgency),
                    checkedSteps = emptySet(),
                    telemetry = null,
                    errorMessage = null,
                    saveBannerMessage = null
                )
            }
            try {
                router.streamTriageGuidance(prompt, taskType).collect { event ->
                    when (event) {
                        is StreamEvent.Chunk -> {
                            val parsed = TriageParser.parse(event.accumulatedText, fallbackUrgency)
                            _uiState.update {
                                it.copy(
                                    guidance = parsed,
                                    partialSource = event.partialSource ?: it.partialSource
                                )
                            }
                        }
                        is StreamEvent.Complete -> {
                            val parsed = TriageParser.parse(event.fullText, fallbackUrgency)
                            _uiState.update {
                                it.copy(
                                    isStreaming = false,
                                    guidance = parsed,
                                    telemetry = event.telemetry
                                )
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isStreaming = false,
                        errorMessage = e.localizedMessage ?: "Unable to generate triage guidance."
                    )
                }
            }
        }
    }

    fun toggleStep(index: Int) {
        _uiState.update { state ->
            val updated = state.checkedSteps.toMutableSet()
            if (!updated.add(index)) updated.remove(index)
            state.copy(checkedSteps = updated)
        }
    }

    fun toggleSpeakCurrentGuidance() {
        if (ttsSpeaker.isSpeaking.value) {
            ttsSpeaker.stop()
            return
        }
        val g = _uiState.value.guidance ?: return
        val spoken = buildString {
            append("${g.urgency.label}. ")
            append("${g.summary}. ")
            g.steps.forEachIndexed { idx, step ->
                append("Step ${idx + 1}: $step. ")
            }
            if (g.doNotDo.isNotBlank()) {
                append("Important warning, do not do the following: ${g.doNotDo}.")
            }
        }
        ttsSpeaker.speak(spoken)
    }

    fun clearSaveBanner() {
        _uiState.update { it.copy(saveBannerMessage = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun saveCurrentTriageToLog(onRequireSignIn: () -> Unit) {
        val state = _uiState.value
        val guidance = state.guidance ?: return
        val telemetry = state.telemetry
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, saveBannerMessage = null) }
            val result = incidentRepository.saveIncident(
                category = IncidentCategory.TRIAGE,
                title = state.activeTitle.ifBlank { "Emergency Triage" },
                promptSummary = state.activePrompt.ifBlank { state.activeTitle },
                urgency = guidance.urgency,
                guidanceSummary = guidance.summary.ifBlank { guidance.rawText },
                checklistSteps = guidance.steps,
                doNotDoNotes = guidance.doNotDo.ifBlank { null },
                inferenceSource = telemetry?.actualSource ?: EngineSource.IN_CLOUD,
                latencyMs = telemetry?.latencyMs ?: 0L
            )
            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            saveBannerMessage = "Saved to Incident Log (auto-syncs to Cloud Firestore)."
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isSaving = false) }
                    if (err is IllegalStateException && err.message?.contains("sign in", ignoreCase = true) == true) {
                        onRequireSignIn()
                    } else {
                        _uiState.update {
                            it.copy(saveBannerMessage = err.localizedMessage ?: "Failed to save incident.")
                        }
                    }
                }
            )
        }
    }

    // --- Offline SOS Phrase Translator ---

    fun selectSosPhrase(phrase: String) {
        _uiState.update { it.copy(sosState = it.sosState.copy(selectedPhrase = phrase, errorMessage = null)) }
    }

    fun selectSosLanguage(language: String) {
        _uiState.update { it.copy(sosState = it.sosState.copy(selectedLanguage = language, errorMessage = null)) }
    }

    fun translateSosPhrase() {
        val current = _uiState.value.sosState
        if (current.isTranslating) return
        if (current.selectedPhrase.isBlank()) {
            _uiState.update {
                it.copy(
                    sosState = it.sosState.copy(
                        errorMessage = "Enter or select an emergency phrase to translate."
                    )
                )
            }
            return
        }
        activeSosJob?.cancel()
        activeSosJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    sosState = it.sosState.copy(
                        isTranslating = true,
                        translatedOutput = "",
                        telemetry = null,
                        errorMessage = null
                    )
                )
            }
            try {
                val res = withTimeout(20_000L) {
                    withContext(Dispatchers.IO) {
                        router.translateSosPhrase(current.selectedPhrase, current.selectedLanguage)
                    }
                }
                _uiState.update {
                    it.copy(
                        sosState = it.sosState.copy(
                            translatedOutput = res.text,
                            telemetry = res.telemetry,
                            errorMessage = null
                        )
                    )
                }
            } catch (e: TimeoutCancellationException) {
                Log.e("TriageViewModel", "SOS Phrase Translation timed out after 20s", e)
                _uiState.update {
                    it.copy(
                        sosState = it.sosState.copy(
                            errorMessage = "Translation timed out after 20 seconds. Check your connection and try again."
                        )
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Log.e("TriageViewModel", "SOS Phrase Translation failed", t)
                _uiState.update {
                    it.copy(
                        sosState = it.sosState.copy(
                            errorMessage = t.localizedMessage?.ifBlank { null }
                                ?: "Translation failed: ${t::class.simpleName}"
                        )
                    )
                }
            } finally {
                _uiState.update {
                    it.copy(
                        sosState = it.sosState.copy(isTranslating = false)
                    )
                }
            }
        }
    }
}
