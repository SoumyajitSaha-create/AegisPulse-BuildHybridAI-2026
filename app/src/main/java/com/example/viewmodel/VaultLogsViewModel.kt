package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.HybridAIRouter
import com.example.ai.OnDeviceUnavailableException
import com.example.core.authStateFlow
import com.example.data.EngineSource
import com.example.data.IceMedicalProfile
import com.example.data.IncidentCategory
import com.example.data.IncidentListItem
import com.example.data.IncidentRepository
import com.example.data.InferenceTelemetry
import com.example.data.LocalVaultStore
import com.example.data.UrgencyLevel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val VAULT_TAG = "VaultLogsVM"

sealed interface IncidentTimelineState {
    data object SignedOut : IncidentTimelineState
    data object Loading : IncidentTimelineState
    data class Ready(val items: List<IncidentListItem>) : IncidentTimelineState
    data class Error(val message: String) : IncidentTimelineState
}

data class VaultLogsUiState(
    val selectedSegment: Int = 0, // 0 = Private ICE Vault, 1 = Incident Logs
    val iceProfile: IceMedicalProfile = IceMedicalProfile(),
    val isProfileDirty: Boolean = false,
    val profileSavedMessage: String? = null,
    val isGeneratingCard: Boolean = false,
    val cardTelemetry: InferenceTelemetry? = null,
    val vaultError: String? = null,
    val showCloudConsentDialog: Boolean = false,
    val isGeneratingBrief: Boolean = false,
    val responderBriefText: String = "",
    val briefTelemetry: InferenceTelemetry? = null,
    val briefError: String? = null,
    val authError: String? = null
)

class VaultLogsViewModel(
    private val localVaultStore: LocalVaultStore,
    private val incidentRepository: IncidentRepository,
    private val router: HybridAIRouter
) : ViewModel() {

    private val _uiState = MutableStateFlow(VaultLogsUiState())
    val uiState: StateFlow<VaultLogsUiState> = _uiState.asStateFlow()

    val currentUser: StateFlow<FirebaseUser?> = Firebase.auth.authStateFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = Firebase.auth.currentUser
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val timelineState: StateFlow<IncidentTimelineState> = currentUser
        .flatMapLatest { user ->
            if (user == null || user.uid.isBlank()) {
                flowOf(IncidentTimelineState.SignedOut)
            } else {
                incidentRepository.observeIncidents(user.uid)
                    .map<List<IncidentListItem>, IncidentTimelineState> { IncidentTimelineState.Ready(it) }
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        val rootError = firestoreError ?: error
                        Log.w(VAULT_TAG, "Error observing incidents", rootError)
                        emit(
                            IncidentTimelineState.Error(
                                rootError.message ?: "Unable to sync incident timeline."
                            )
                        )
                    }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = if (Firebase.auth.currentUser == null) IncidentTimelineState.SignedOut else IncidentTimelineState.Loading
        )

    init {
        viewModelScope.launch {
            localVaultStore.iceProfile.collect { stored ->
                _uiState.update { current ->
                    if (current.isProfileDirty) current
                    else current.copy(iceProfile = stored)
                }
            }
        }
    }

    fun selectSegment(index: Int) {
        _uiState.update { it.copy(selectedSegment = index) }
    }

    fun updateProfile(transform: (IceMedicalProfile) -> IceMedicalProfile) {
        _uiState.update {
            it.copy(
                iceProfile = transform(it.iceProfile),
                isProfileDirty = true,
                profileSavedMessage = null,
                vaultError = null
            )
        }
    }

    fun saveIceProfileLocally() {
        val profile = _uiState.value.iceProfile
        viewModelScope.launch {
            localVaultStore.saveIceProfile(profile)
            _uiState.update {
                it.copy(
                    isProfileDirty = false,
                    profileSavedMessage = "Saved privately on this device (0 bytes uploaded)."
                )
            }
        }
    }

    fun generatePrivateResponderCard(allowExplicitCloudConsent: Boolean = false) {
        val profile = _uiState.value.iceProfile
        val hasAnyData = listOf(
            profile.fullName,
            profile.bloodType,
            profile.allergies,
            profile.medications,
            profile.medicalConditions,
            profile.emergencyContacts
        ).any { it.isNotBlank() }

        if (!hasAnyData) {
            _uiState.update {
                it.copy(vaultError = "Enter at least one medical or emergency contact detail first.")
            }
            return
        }

        val profileSummary = buildString {
            appendLine("Name: ${profile.fullName.ifBlank { "Not specified" }}")
            appendLine("Blood Type: ${profile.bloodType.ifBlank { "Unknown" }}")
            appendLine("Allergies: ${profile.allergies.ifBlank { "None listed" }}")
            appendLine("Current Medications: ${profile.medications.ifBlank { "None listed" }}")
            appendLine("Medical Conditions: ${profile.medicalConditions.ifBlank { "None listed" }}")
            appendLine("Emergency Contacts: ${profile.emergencyContacts.ifBlank { "None listed" }}")
        }

        viewModelScope.launch {
            localVaultStore.saveIceProfile(profile)
            _uiState.update {
                it.copy(
                    isProfileDirty = false,
                    isGeneratingCard = true,
                    vaultError = null,
                    showCloudConsentDialog = false
                )
            }
            try {
                val res = router.generatePrivateIceCard(
                    profilePrompt = profileSummary,
                    allowExplicitCloudConsent = allowExplicitCloudConsent
                )
                localVaultStore.saveGeneratedIceCard(
                    cardText = res.text,
                    source = res.telemetry.actualSource,
                    latencyMs = res.telemetry.latencyMs
                )
                _uiState.update {
                    it.copy(
                        isGeneratingCard = false,
                        iceProfile = it.iceProfile.copy(
                            generatedCardText = res.text,
                            generatedSource = res.telemetry.actualSource.name,
                            generatedLatencyMs = res.telemetry.latencyMs
                        ),
                        cardTelemetry = res.telemetry
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: OnDeviceUnavailableException) {
                // Strictly blocked cloud transmission until user explicitly consents
                _uiState.update {
                    it.copy(
                        isGeneratingCard = false,
                        showCloudConsentDialog = true,
                        vaultError = e.message
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isGeneratingCard = false,
                        vaultError = e.localizedMessage ?: "Failed to generate ICE card."
                    )
                }
            }
        }
    }

    fun dismissCloudConsentDialog() {
        _uiState.update { it.copy(showCloudConsentDialog = false) }
    }

    fun setAuthError(message: String?) {
        _uiState.update { it.copy(authError = message) }
    }

    fun deleteIncident(incidentId: String) {
        viewModelScope.launch {
            val result = incidentRepository.deleteIncident(incidentId)
            result.onFailure { err ->
                _uiState.update {
                    it.copy(briefError = err.localizedMessage ?: "Unable to delete incident.")
                }
            }
        }
    }

    fun generateMultiIncidentResponderBrief(items: List<IncidentListItem>) {
        if (items.isEmpty()) {
            _uiState.update {
                it.copy(briefError = "Save at least one Triage or Scene Scan incident before generating a Responder Brief.")
            }
            return
        }

        val formatter = SimpleDateFormat("MMM dd, HH:mm", Locale.US)
        val compiled = items.take(15).mapIndexed { index, item ->
            val inc = item.incident
            val ts = inc.createdAt?.toDate() ?: Date()
            buildString {
                appendLine("Incident ${index + 1} [${formatter.format(ts)}] (${inc.category} | ${inc.urgencyLevel}): ${inc.title}")
                appendLine("Summary: ${inc.guidanceSummary.take(400)}")
                if (inc.checklistSteps.isNotEmpty()) {
                    appendLine("Steps: ${inc.checklistSteps.take(4).joinToString("; ")}")
                }
                if (!inc.doNotDoNotes.isNullOrBlank()) {
                    appendLine("Warnings: ${inc.doNotDoNotes.take(200)}")
                }
            }
        }.joinToString("\n")

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isGeneratingBrief = true,
                    briefError = null
                )
            }
            try {
                val res = router.generateMultiIncidentResponderBrief(compiled)
                _uiState.update {
                    it.copy(
                        isGeneratingBrief = false,
                        responderBriefText = res.text,
                        briefTelemetry = res.telemetry
                    )
                }
                // Persist synthesized handoff brief into Firestore timeline too
                incidentRepository.saveIncident(
                    category = IncidentCategory.RESPONDER_BRIEF,
                    title = "Multi-Incident Responder Brief (${items.size} events)",
                    promptSummary = "Synthesized from ${items.size} logged incidents.",
                    urgency = UrgencyLevel.HIGH,
                    guidanceSummary = res.text,
                    checklistSteps = emptyList(),
                    doNotDoNotes = null,
                    inferenceSource = res.telemetry.actualSource,
                    latencyMs = res.telemetry.latencyMs
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isGeneratingBrief = false,
                        briefError = e.localizedMessage ?: "Failed to generate Responder Brief."
                    )
                }
            }
        }
    }
}
