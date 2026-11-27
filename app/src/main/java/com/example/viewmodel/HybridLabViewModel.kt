package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.HybridAIRouter
import com.example.ai.HybridTextResult
import com.example.core.ConnectivityObserver
import com.example.data.HybridOverrideMode
import com.example.data.OnDeviceStatus
import com.example.data.RemotePolicyConfig
import com.example.data.SessionTelemetry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BenchmarkComparison(
    val shortResult: HybridTextResult? = null,
    val shortError: String? = null,
    val longResult: HybridTextResult? = null,
    val longError: String? = null
)

data class HybridLabUiState(
    val isRefreshingPolicy: Boolean = false,
    val policyMessage: String? = null,
    val isRunningBenchmark: Boolean = false,
    val benchmark: BenchmarkComparison? = null,
    val downloadMessage: String? = null
)

class HybridLabViewModel(
    private val router: HybridAIRouter,
    private val connectivityObserver: ConnectivityObserver
) : ViewModel() {

    private val _uiState = MutableStateFlow(HybridLabUiState())
    val uiState: StateFlow<HybridLabUiState> = _uiState.asStateFlow()

    val onDeviceStatus: StateFlow<OnDeviceStatus> = router.onDeviceStatus
    val onDeviceDetail: StateFlow<String> = router.onDeviceDetail
    val downloadProgress: StateFlow<Float?> = router.downloadProgress
    val isOnline: StateFlow<Boolean> = connectivityObserver.isOnline
    val remotePolicy: StateFlow<RemotePolicyConfig> = router.remotePolicy
    val overrideMode: StateFlow<HybridOverrideMode> = router.overrideMode
    val telemetry: StateFlow<SessionTelemetry> = router.telemetry

    fun checkStatus() {
        viewModelScope.launch {
            _uiState.update { it.copy(downloadMessage = null) }
            router.checkOnDeviceAvailability()
        }
    }

    fun downloadOnDeviceModel() {
        viewModelScope.launch {
            _uiState.update { it.copy(downloadMessage = null) }
            val result = router.downloadOnDeviceModel()
            result.onFailure { err ->
                _uiState.update {
                    it.copy(downloadMessage = err.localizedMessage ?: "Download unavailable on this hardware.")
                }
            }
        }
    }

    fun refreshRemotePolicy() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshingPolicy = true, policyMessage = null) }
            val result = router.refreshRemotePolicy(silent = false)
            _uiState.update {
                it.copy(
                    isRefreshingPolicy = false,
                    policyMessage = result.fold(
                        onSuccess = { cfg -> "Policy synced (${cfg.cloudFallbackModel}, threshold ${cfg.longContextThresholdChars} chars)." },
                        onFailure = { "Using cached/default Remote Config policy." }
                    )
                )
            }
        }
    }

    fun selectOverride(mode: HybridOverrideMode) {
        router.setRoutingOverride(mode)
    }

    fun resetMetrics() {
        router.resetSessionTelemetry()
    }

    /**
     * Runs a Short Prompt (~75 chars) vs. a Long Context Field Report (> 1,350 chars)
     * to demonstrate live Firebase AI Logic hybrid routing, threshold escalation, and telemetry.
     */
    fun runLiveHybridRoutingTest() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isRunningBenchmark = true,
                    benchmark = BenchmarkComparison()
                )
            }

            val shortPrompt = "Quick check: 3 immediate steps for a minor forearm burn in 25 words."
            val longPrompt = buildString {
                append("Comprehensive Multi-Factor Field Emergency Assessment Request: ")
                append("A team of four backcountry hikers is sheltered near a ridge during an unexpected severe thunderstorm with rapid temperature drop from 18C to 4C, wet clothing, and intermittent cellular signal. ")
                append("Member A slipped on wet shale 35 minutes ago, sustaining a swollen right ankle unable to bear weight and a 5cm shallow abrasion on the left shin that has stopped active bleeding after direct pressure. ")
                append("Member B shows early shivering, mild fatigue, and damp base layers but remains alert and oriented. ")
                append("Member C has mild seasonal asthma and carries an albuterol rescue inhaler with roughly 40 doses remaining. ")
                append("Member D is uninjured and carrying a 2-person emergency bivy, thermal mylar blankets, 3 liters of filtered water, a headlamp, whistle, basic gauze/elastic bandage roll, and two energy bars. ")
                append("The nearest gravel forest service road is 4.2 kilometers downhill along a muddy switchback trail with one swollen creek crossing that was calf-deep two hours ago. ")
                append("Sunset occurs in 70 minutes and lightning strikes are currently heard approximately 12 seconds after flashes. ")
                append("Please provide a concise prioritized 4-point stabilization and evacuation vs. shelter-in-place decision plan covering hypothermia prevention, ankle splinting, lightning safety, and rescue signaling. ")
                append("Keep the response concise under 120 words so it can be read rapidly on a phone screen with low battery.")
            }

            var shortRes: HybridTextResult? = null
            var shortErr: String? = null
            try {
                shortRes = router.runHybridLabBenchmarkPrompt("Lab Benchmark (Short)", shortPrompt)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                shortErr = e.localizedMessage ?: "Short test failed."
            }

            _uiState.update {
                it.copy(benchmark = BenchmarkComparison(shortResult = shortRes, shortError = shortErr))
            }

            var longRes: HybridTextResult? = null
            var longErr: String? = null
            try {
                longRes = router.runHybridLabBenchmarkPrompt("Lab Benchmark (Long)", longPrompt)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                longErr = e.localizedMessage ?: "Long test failed."
            }

            _uiState.update {
                it.copy(
                    isRunningBenchmark = false,
                    benchmark = BenchmarkComparison(
                        shortResult = shortRes,
                        shortError = shortErr,
                        longResult = longRes,
                        longError = longErr
                    )
                )
            }
        }
    }
}
