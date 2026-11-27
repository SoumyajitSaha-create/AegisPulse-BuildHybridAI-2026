package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.aegisDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "aegispulse_local_vault"
)

/**
 * Strictly device-local DataStore persistence for the Private ICE Medical Vault,
 * theme preference, global hybrid routing override, and cumulative session telemetry.
 * ICE medical data NEVER auto-syncs to Cloud Firestore.
 */
class LocalVaultStore(context: Context) {
    private val dataStore = context.applicationContext.aegisDataStore

    private object Keys {
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        val ROUTING_OVERRIDE = stringPreferencesKey("routing_override")

        // Private ICE Vault keys (strictly device-local)
        val ICE_NAME = stringPreferencesKey("ice_full_name")
        val ICE_BLOOD = stringPreferencesKey("ice_blood_type")
        val ICE_ALLERGIES = stringPreferencesKey("ice_allergies")
        val ICE_MEDICATIONS = stringPreferencesKey("ice_medications")
        val ICE_CONDITIONS = stringPreferencesKey("ice_conditions")
        val ICE_CONTACTS = stringPreferencesKey("ice_contacts")
        val ICE_CARD_TEXT = stringPreferencesKey("ice_card_text")
        val ICE_CARD_SOURCE = stringPreferencesKey("ice_card_source")
        val ICE_CARD_LATENCY = longPreferencesKey("ice_card_latency")

        // Local telemetry totals
        val TELEMETRY_TOTAL = intPreferencesKey("telemetry_total")
        val TELEMETRY_ON_DEVICE = intPreferencesKey("telemetry_on_device")
        val TELEMETRY_CLOUD = intPreferencesKey("telemetry_cloud")
        val TELEMETRY_ON_DEVICE_LAT = longPreferencesKey("telemetry_on_device_lat")
        val TELEMETRY_CLOUD_LAT = longPreferencesKey("telemetry_cloud_lat")
    }

    val isDarkMode: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.DARK_MODE] ?: true
    }

    val routingOverride: Flow<HybridOverrideMode> = dataStore.data.map { prefs ->
        HybridOverrideMode.fromRaw(prefs[Keys.ROUTING_OVERRIDE])
    }

    val iceProfile: Flow<IceMedicalProfile> = dataStore.data.map { prefs ->
        IceMedicalProfile(
            fullName = prefs[Keys.ICE_NAME].orEmpty(),
            bloodType = prefs[Keys.ICE_BLOOD].orEmpty(),
            allergies = prefs[Keys.ICE_ALLERGIES].orEmpty(),
            medications = prefs[Keys.ICE_MEDICATIONS].orEmpty(),
            medicalConditions = prefs[Keys.ICE_CONDITIONS].orEmpty(),
            emergencyContacts = prefs[Keys.ICE_CONTACTS].orEmpty(),
            generatedCardText = prefs[Keys.ICE_CARD_TEXT].orEmpty(),
            generatedSource = prefs[Keys.ICE_CARD_SOURCE].orEmpty(),
            generatedLatencyMs = prefs[Keys.ICE_CARD_LATENCY] ?: 0L
        )
    }

    val persistedTelemetryCounts: Flow<SessionTelemetry> = dataStore.data.map { prefs ->
        SessionTelemetry(
            totalRequests = prefs[Keys.TELEMETRY_TOTAL] ?: 0,
            onDeviceCount = prefs[Keys.TELEMETRY_ON_DEVICE] ?: 0,
            cloudCount = prefs[Keys.TELEMETRY_CLOUD] ?: 0,
            totalOnDeviceLatencyMs = prefs[Keys.TELEMETRY_ON_DEVICE_LAT] ?: 0L,
            totalCloudLatencyMs = prefs[Keys.TELEMETRY_CLOUD_LAT] ?: 0L
        )
    }

    suspend fun setDarkMode(dark: Boolean) {
        dataStore.edit { it[Keys.DARK_MODE] = dark }
    }

    suspend fun setRoutingOverride(mode: HybridOverrideMode) {
        dataStore.edit { it[Keys.ROUTING_OVERRIDE] = mode.name }
    }

    suspend fun saveIceProfile(profile: IceMedicalProfile) {
        dataStore.edit { prefs ->
            prefs[Keys.ICE_NAME] = profile.fullName.trim()
            prefs[Keys.ICE_BLOOD] = profile.bloodType.trim()
            prefs[Keys.ICE_ALLERGIES] = profile.allergies.trim()
            prefs[Keys.ICE_MEDICATIONS] = profile.medications.trim()
            prefs[Keys.ICE_CONDITIONS] = profile.medicalConditions.trim()
            prefs[Keys.ICE_CONTACTS] = profile.emergencyContacts.trim()
            if (profile.generatedCardText.isNotBlank()) {
                prefs[Keys.ICE_CARD_TEXT] = profile.generatedCardText
                prefs[Keys.ICE_CARD_SOURCE] = profile.generatedSource
                prefs[Keys.ICE_CARD_LATENCY] = profile.generatedLatencyMs
            }
        }
    }

    suspend fun saveGeneratedIceCard(cardText: String, source: EngineSource, latencyMs: Long) {
        dataStore.edit { prefs ->
            prefs[Keys.ICE_CARD_TEXT] = cardText
            prefs[Keys.ICE_CARD_SOURCE] = source.name
            prefs[Keys.ICE_CARD_LATENCY] = latencyMs
        }
    }

    suspend fun recordTelemetry(source: EngineSource, latencyMs: Long) {
        dataStore.edit { prefs ->
            prefs[Keys.TELEMETRY_TOTAL] = (prefs[Keys.TELEMETRY_TOTAL] ?: 0) + 1
            if (source == EngineSource.ON_DEVICE) {
                prefs[Keys.TELEMETRY_ON_DEVICE] = (prefs[Keys.TELEMETRY_ON_DEVICE] ?: 0) + 1
                prefs[Keys.TELEMETRY_ON_DEVICE_LAT] = (prefs[Keys.TELEMETRY_ON_DEVICE_LAT] ?: 0L) + latencyMs
            } else {
                prefs[Keys.TELEMETRY_CLOUD] = (prefs[Keys.TELEMETRY_CLOUD] ?: 0) + 1
                prefs[Keys.TELEMETRY_CLOUD_LAT] = (prefs[Keys.TELEMETRY_CLOUD_LAT] ?: 0L) + latencyMs
            }
        }
    }

    suspend fun resetTelemetry() {
        dataStore.edit { prefs ->
            prefs[Keys.TELEMETRY_TOTAL] = 0
            prefs[Keys.TELEMETRY_ON_DEVICE] = 0
            prefs[Keys.TELEMETRY_CLOUD] = 0
            prefs[Keys.TELEMETRY_ON_DEVICE_LAT] = 0L
            prefs[Keys.TELEMETRY_CLOUD_LAT] = 0L
        }
    }
}
