package com.example.data

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class UrgencyLevel(val label: String) {
    CRITICAL("CRITICAL — CALL 911 / 112"),
    HIGH("HIGH PRIORITY"),
    MODERATE("MODERATE CARE"),
    INFO("PREPAREDNESS GUIDANCE");

    companion object {
        fun fromRaw(raw: String?): UrgencyLevel {
            val upper = raw?.trim()?.uppercase().orEmpty()
            return when {
                upper.contains("CRITICAL") || upper.contains("RED") || upper.contains("EMERGENCY") -> CRITICAL
                upper.contains("HIGH") || upper.contains("URGENT") -> HIGH
                upper.contains("MODERATE") || upper.contains("AMBER") || upper.contains("MEDIUM") -> MODERATE
                else -> entries.firstOrNull { it.name == upper } ?: INFO
            }
        }
    }
}

enum class IncidentCategory(val label: String) {
    TRIAGE("Triage"),
    SCENE_SCAN("Scene Scan"),
    RESPONDER_BRIEF("Responder Brief");

    companion object {
        fun fromRaw(raw: String?): IncidentCategory =
            entries.firstOrNull { it.name == raw?.uppercase() } ?: TRIAGE
    }
}

enum class EngineSource(val label: String) {
    ON_DEVICE("ON-DEVICE"),
    IN_CLOUD("CLOUD FALLBACK");

    companion object {
        fun fromRaw(raw: String?): EngineSource =
            entries.firstOrNull { it.name == raw?.uppercase() } ?: IN_CLOUD
    }
}

enum class OnDeviceStatus(val label: String) {
    CHECKING("Checking AICore..."),
    AVAILABLE("AVAILABLE"),
    DOWNLOADABLE("DOWNLOADABLE"),
    DOWNLOADING("DOWNLOADING"),
    UNAVAILABLE("UNAVAILABLE (Cloud Fallback Active)")
}

enum class HybridOverrideMode(val label: String, val shortLabel: String) {
    AUTO("Auto Smart Route", "Auto"),
    PREFER_ON_DEVICE("PREFER_ON_DEVICE", "Prefer Nano"),
    ONLY_ON_DEVICE("ONLY_ON_DEVICE", "Nano Only"),
    PREFER_IN_CLOUD("PREFER_IN_CLOUD", "Prefer Cloud"),
    ONLY_IN_CLOUD("ONLY_IN_CLOUD", "Cloud Only");

    companion object {
        fun fromRaw(raw: String?): HybridOverrideMode =
            entries.firstOrNull { it.name == raw?.uppercase() } ?: AUTO
    }
}

data class EmergencyScenario(
    val id: String,
    val title: String,
    val subtitle: String,
    val iconKey: String,
    val defaultUrgency: UrgencyLevel,
    val prompt: String
)

object EmergencyScenarios {
    val all: List<EmergencyScenario> = listOf(
        EmergencyScenario(
            id = "bleeding",
            title = "Severe Bleeding",
            subtitle = "Direct pressure & care",
            iconKey = "bleeding",
            defaultUrgency = UrgencyLevel.CRITICAL,
            prompt = "Severe arterial or deep wound bleeding in the field. Give immediate life-saving hemorrhage control steps, safe bandaging guidance, signs of shock, and what NOT to do."
        ),
        EmergencyScenario(
            id = "burns",
            title = "Burns & Scalds",
            subtitle = "Cool, protect & wrap",
            iconKey = "burns",
            defaultUrgency = UrgencyLevel.HIGH,
            prompt = "Thermal burn or hot liquid scald first aid. Provide immediate cooling steps, sterile covering guidance, red flags requiring urgent hospital care, and what NOT to apply."
        ),
        EmergencyScenario(
            id = "choking",
            title = "Choking / Airway",
            subtitle = "Airway & choking steps",
            iconKey = "choking",
            defaultUrgency = UrgencyLevel.CRITICAL,
            prompt = "Conscious person actively choking and unable to cough or speak. Provide immediate back blows and abdominal thrust steps, when to call emergency services, and what NOT to do."
        ),
        EmergencyScenario(
            id = "earthquake",
            title = "Earthquake Shelter",
            subtitle = "Drop, cover & hold on",
            iconKey = "earthquake",
            defaultUrgency = UrgencyLevel.CRITICAL,
            prompt = "Immediate post-earthquake safety and structural shelter checklist. Cover drop-cover-hold, exiting safely, gas/electrical hazards, trapped signaling, and what NOT to do."
        ),
        EmergencyScenario(
            id = "heat",
            title = "Extreme Heat",
            subtitle = "Cooling & hydration",
            iconKey = "heat",
            defaultUrgency = UrgencyLevel.HIGH,
            prompt = "Suspected heat exhaustion or heatstroke during extreme heat or dehydration. Give rapid cooling steps, safe rehydration rules, mental status red flags, and what NOT to do."
        ),
        EmergencyScenario(
            id = "flood",
            title = "Power & Flood",
            subtitle = "Outage & water safety",
            iconKey = "flood",
            defaultUrgency = UrgencyLevel.MODERATE,
            prompt = "Severe power outage and flash flood survival steps. Cover safe high-ground shelter, water safety, generator carbon monoxide prevention, food/medication preservation, and what NOT to do."
        )
    )
}

data class TriageGuidance(
    val urgency: UrgencyLevel = UrgencyLevel.HIGH,
    val summary: String = "",
    val steps: List<String> = emptyList(),
    val doNotDo: String = "",
    val rawText: String = ""
)

object TriageParser {
    fun parse(rawText: String, fallbackUrgency: UrgencyLevel = UrgencyLevel.HIGH): TriageGuidance {
        if (rawText.isBlank()) return TriageGuidance(urgency = fallbackUrgency)

        var urgency = fallbackUrgency
        val summaryLines = mutableListOf<String>()
        val steps = mutableListOf<String>()
        val doNotLines = mutableListOf<String>()

        var section = ""
        rawText.lines().forEach { rawLine ->
            val line = rawLine.trim().replace("**", "")
            if (line.isBlank()) return@forEach

            val upper = line.uppercase()
            when {
                upper.startsWith("URGENCY:") -> {
                    urgency = UrgencyLevel.fromRaw(line.substringAfter(":"))
                    section = "URGENCY"
                }
                upper.startsWith("SUMMARY:") -> {
                    val rest = line.substringAfter(":").trim()
                    if (rest.isNotBlank()) summaryLines.add(rest)
                    section = "SUMMARY"
                }
                upper.startsWith("STEPS:") || upper.startsWith("ACTION STEPS:") -> {
                    section = "STEPS"
                }
                upper.startsWith("DO_NOT:") || upper.startsWith("DO NOT:") || upper.startsWith("WHAT NOT TO DO:") -> {
                    val rest = line.substringAfter(":").trim()
                    if (rest.isNotBlank()) doNotLines.add(rest)
                    section = "DO_NOT"
                }
                else -> {
                    when (section) {
                        "SUMMARY" -> summaryLines.add(line)
                        "DO_NOT" -> doNotLines.add(line.removePrefix("-").removePrefix("•").trim())
                        "STEPS" -> {
                            val cleaned = line
                                .replace(Regex("^\\d+[.)\\-:]\\s*"), "")
                                .removePrefix("-")
                                .removePrefix("•")
                                .trim()
                            if (cleaned.isNotBlank()) steps.add(cleaned)
                        }
                        else -> {
                            if (line.matches(Regex("^\\d+[.)\\-:]\\s+.+"))) {
                                steps.add(line.replace(Regex("^\\d+[.)\\-:]\\s*"), "").trim())
                            } else {
                                summaryLines.add(line)
                            }
                        }
                    }
                }
            }
        }

        val finalSteps = if (steps.isEmpty() && rawText.isNotBlank()) {
            rawText.lines()
                .map { it.trim().replace("**", "") }
                .filter { it.length > 6 && !it.uppercase().startsWith("URGENCY") }
                .take(6)
        } else {
            steps.take(12)
        }

        return TriageGuidance(
            urgency = urgency,
            summary = summaryLines.joinToString(" ").ifBlank {
                finalSteps.firstOrNull() ?: rawText.take(240)
            },
            steps = finalSteps,
            doNotDo = doNotLines.joinToString("\n"),
            rawText = rawText
        )
    }
}

data class InferenceTelemetry(
    val requestedMode: String,
    val actualSource: EngineSource,
    val modelLabel: String,
    val latencyMs: Long,
    val promptChars: Int,
    val routingReason: String,
    val bytesSentHint: String
)

data class RoutingDecisionLog(
    val id: String,
    val timestampMs: Long,
    val taskType: String,
    val requestedMode: String,
    val actualSource: EngineSource,
    val latencyMs: Long,
    val promptChars: Int,
    val routingReason: String
)

data class SessionTelemetry(
    val totalRequests: Int = 0,
    val onDeviceCount: Int = 0,
    val cloudCount: Int = 0,
    val totalOnDeviceLatencyMs: Long = 0L,
    val totalCloudLatencyMs: Long = 0L,
    val recentDecisions: List<RoutingDecisionLog> = emptyList()
) {
    val avgOnDeviceMs: Long
        get() = if (onDeviceCount > 0) totalOnDeviceLatencyMs / onDeviceCount else 0L

    val avgCloudMs: Long
        get() = if (cloudCount > 0) totalCloudLatencyMs / cloudCount else 0L

    val onDevicePercent: Int
        get() = if (totalRequests > 0) ((onDeviceCount * 100.0) / totalRequests).toInt() else 0

    val cloudPercent: Int
        get() = if (totalRequests > 0) 100 - onDevicePercent else 0

    val estimatedCloudCallsSaved: Int
        get() = onDeviceCount
}

data class RemotePolicyConfig(
    val cloudFallbackModel: String = "gemini-3.5-flash-lite",
    val hybridDefaultMode: String = "PREFER_ON_DEVICE",
    val longContextThresholdChars: Int = 1200,
    val emergencyDisclaimerText: String = "AegisPulse is an AI first-aid & preparedness assistant. It does not replace professional medical advice or official emergency services (911 / 112).",
    val lastFetchedMs: Long = 0L,
    val sourceLabel: String = "Default Policy"
)

data class IceMedicalProfile(
    val fullName: String = "",
    val bloodType: String = "",
    val allergies: String = "",
    val medications: String = "",
    val medicalConditions: String = "",
    val emergencyContacts: String = "",
    val generatedCardText: String = "",
    val generatedSource: String = "",
    val generatedLatencyMs: Long = 0L
)

@Keep
@IgnoreExtraProperties
data class IncidentLog(
    @DocumentId val id: String = "",
    val userId: String = "",
    val category: String = IncidentCategory.TRIAGE.name,
    val title: String = "",
    val promptSummary: String = "",
    val urgencyLevel: String = UrgencyLevel.MODERATE.name,
    val guidanceSummary: String = "",
    val checklistSteps: List<String> = emptyList(),
    val doNotDoNotes: String? = null,
    val inferenceSource: String = EngineSource.IN_CLOUD.name,
    val latencyMs: Long = 0L,
    val platform: String = "ANDROID",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class IncidentListItem(
    val incident: IncidentLog,
    val pendingOfflineSync: Boolean
)
