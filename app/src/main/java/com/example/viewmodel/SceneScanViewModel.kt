package com.example.viewmodel

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.HybridAIRouter
import com.example.core.ConnectivityObserver
import com.example.core.TtsSpeaker
import com.example.data.EngineSource
import com.example.data.IncidentCategory
import com.example.data.IncidentRepository
import com.example.data.InferenceTelemetry
import com.example.data.TriageGuidance
import com.example.data.TriageParser
import com.example.data.UrgencyLevel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ScenePreset(val title: String, val defaultPrompt: String) {
    HAZARD_SAFETY(
        title = "Hazard & Safety Check",
        defaultPrompt = "Inspect this scene for immediate physical, electrical, structural, flood, fire, or chemical hazards. Explain what is dangerous and immediate safe actions."
    ),
    MEDICINE_LABEL(
        title = "Medicine / First-Aid Label",
        defaultPrompt = "Read this medication bottle, blister pack, or first-aid kit label. Explain active ingredients, primary purpose, general usage instructions, and key warnings in plain language."
    ),
    CUSTOM_VISUAL(
        title = "Custom Visual Question",
        defaultPrompt = ""
    )
}

data class SceneScanUiState(
    val selectedBitmap: Bitmap? = null,
    val selectedPreset: ScenePreset = ScenePreset.HAZARD_SAFETY,
    val customQuestion: String = "",
    val offlineDescription: String = "",
    val showOfflineFallbackCard: Boolean = false,
    val isAnalyzing: Boolean = false,
    val guidance: TriageGuidance? = null,
    val telemetry: InferenceTelemetry? = null,
    val errorMessage: String? = null,
    val saveBannerMessage: String? = null,
    val isSaving: Boolean = false
)

class SceneScanViewModel(
    private val router: HybridAIRouter,
    private val connectivityObserver: ConnectivityObserver,
    private val incidentRepository: IncidentRepository,
    val ttsSpeaker: TtsSpeaker
) : ViewModel() {

    private val _uiState = MutableStateFlow(SceneScanUiState())
    val uiState: StateFlow<SceneScanUiState> = _uiState.asStateFlow()

    val isOnline: StateFlow<Boolean> = connectivityObserver.isOnline
    val isSpeaking: StateFlow<Boolean> = ttsSpeaker.isSpeaking

    fun onBitmapReady(bitmap: Bitmap?) {
        if (bitmap == null) {
            _uiState.update { it.copy(errorMessage = "Could not load selected image. Please try another photo.") }
            return
        }
        _uiState.update {
            it.copy(
                selectedBitmap = bitmap,
                guidance = null,
                telemetry = null,
                errorMessage = null,
                saveBannerMessage = null,
                showOfflineFallbackCard = !connectivityObserver.isOnline.value
            )
        }
    }

    fun selectPreset(preset: ScenePreset) {
        _uiState.update { it.copy(selectedPreset = preset, errorMessage = null) }
    }

    fun onCustomQuestionChange(value: String) {
        _uiState.update { it.copy(customQuestion = value) }
    }

    fun onOfflineDescriptionChange(value: String) {
        _uiState.update { it.copy(offlineDescription = value) }
    }

    fun clearImage() {
        ttsSpeaker.stop()
        _uiState.update {
            it.copy(
                selectedBitmap = null,
                guidance = null,
                telemetry = null,
                errorMessage = null,
                saveBannerMessage = null,
                showOfflineFallbackCard = false
            )
        }
    }

    fun analyzeScene() {
        val state = _uiState.value
        if (state.isAnalyzing) return
        val bitmap = state.selectedBitmap
        if (bitmap == null) {
            _uiState.update { it.copy(errorMessage = "Capture or choose a photo first to run multimodal analysis.") }
            return
        }

        val prompt = when (state.selectedPreset) {
            ScenePreset.CUSTOM_VISUAL -> state.customQuestion.trim().ifBlank {
                "Analyze this scene for safety hazards or emergency first-aid considerations."
            }
            else -> {
                val extra = state.customQuestion.trim()
                if (extra.isNotBlank()) "${state.selectedPreset.defaultPrompt} Additional context: $extra"
                else state.selectedPreset.defaultPrompt
            }
        }

        if (!connectivityObserver.isOnline.value) {
            _uiState.update {
                it.copy(
                    showOfflineFallbackCard = true,
                    errorMessage = null
                )
            }
            return
        }

        ttsSpeaker.stop()
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isAnalyzing = true,
                    errorMessage = null,
                    saveBannerMessage = null,
                    showOfflineFallbackCard = false
                )
            }
            try {
                val result = router.analyzeSceneImage(bitmap, prompt)
                val parsed = TriageParser.parse(result.text, UrgencyLevel.MODERATE)
                _uiState.update {
                    it.copy(
                        guidance = parsed,
                        telemetry = result.telemetry
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        showOfflineFallbackCard = !connectivityObserver.isOnline.value,
                        errorMessage = e.localizedMessage ?: "Multimodal analysis failed. You can run an On-Device Text-Guided Assessment below."
                    )
                }
            } finally {
                _uiState.update { it.copy(isAnalyzing = false) }
            }
        }
    }

    fun runOfflineTextAssessment() {
        if (_uiState.value.isAnalyzing) return
        val desc = _uiState.value.offlineDescription.trim().ifBlank {
            _uiState.value.customQuestion.trim().ifBlank {
                _uiState.value.selectedPreset.title
            }
        }
        ttsSpeaker.stop()
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isAnalyzing = true,
                    errorMessage = null,
                    saveBannerMessage = null
                )
            }
            try {
                val result = router.runOfflineSceneTextFallback(desc)
                val parsed = TriageParser.parse(result.text, UrgencyLevel.MODERATE)
                _uiState.update {
                    it.copy(
                        guidance = parsed,
                        telemetry = result.telemetry
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        errorMessage = e.localizedMessage ?: "Offline text assessment failed."
                    )
                }
            } finally {
                _uiState.update { it.copy(isAnalyzing = false) }
            }
        }
    }

    fun toggleSpeakScan() {
        if (ttsSpeaker.isSpeaking.value) {
            ttsSpeaker.stop()
            return
        }
        val g = _uiState.value.guidance ?: return
        val text = buildString {
            append("${g.urgency.label}. ${g.summary}. ")
            g.steps.forEachIndexed { i, step -> append("Step ${i + 1}: $step. ") }
            if (g.doNotDo.isNotBlank()) append("Warning: ${g.doNotDo}.")
        }
        ttsSpeaker.speak(text)
    }

    fun saveAssessmentToLog(onRequireSignIn: () -> Unit) {
        val state = _uiState.value
        val guidance = state.guidance ?: return
        val telemetry = state.telemetry
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, saveBannerMessage = null) }
            val result = incidentRepository.saveIncident(
                category = IncidentCategory.SCENE_SCAN,
                title = "Scene Scan: ${state.selectedPreset.title}",
                promptSummary = state.customQuestion.ifBlank { state.selectedPreset.defaultPrompt }.take(400),
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
                            saveBannerMessage = "Scene assessment saved to Incident Log."
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isSaving = false) }
                    if (err is IllegalStateException && err.message?.contains("sign in", ignoreCase = true) == true) {
                        onRequireSignIn()
                    } else {
                        _uiState.update {
                            it.copy(saveBannerMessage = err.localizedMessage ?: "Failed to save scene assessment.")
                        }
                    }
                }
            )
        }
    }
}
