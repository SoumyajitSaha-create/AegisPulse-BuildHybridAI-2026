package com.example.data

import android.content.Context
import android.util.Log
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.firestore.persistentCacheSettings
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

private const val REPO_TAG = "IncidentRepository"

/**
 * Cloud Firestore repository backed by the provisioned named database
 * (`R.string.firestore_database_id`) with persistent offline cache enabled.
 * All operations require an authenticated Google Sign-In UID and align 1:1
 * with `firestore.rules`.
 */
class IncidentRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        createConfiguredFirestore(context.applicationContext)
    )

    val offlineCacheEnabled: Boolean = true

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("Please sign in with Google to sync incident logs.")
    }

    fun observeIncidents(userId: String): Flow<List<IncidentListItem>> = flow {
        require(userId.isNotBlank()) { "Authenticated userId is required." }
        val path = "users/$userId/incidents"
        emitAll(
            db.collection("users")
                .document(userId)
                .collection("incidents")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots(MetadataChanges.INCLUDE)
                .map { querySnapshot ->
                    querySnapshot.documents.mapNotNull { doc ->
                        val parsed = runCatching {
                            doc.toObject(
                                IncidentLog::class.java,
                                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                            )
                        }.getOrNull() ?: return@mapNotNull null
                        IncidentListItem(
                            incident = parsed,
                            pendingOfflineSync = doc.metadata.hasPendingWrites()
                        )
                    }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun saveIncident(
        category: IncidentCategory,
        title: String,
        promptSummary: String,
        urgency: UrgencyLevel,
        guidanceSummary: String,
        checklistSteps: List<String>,
        doNotDoNotes: String?,
        inferenceSource: EngineSource,
        latencyMs: Long
    ): Result<String> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/incidents"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid).collection("incidents").document()
            docPath = docRef.path

            val sanitizedTitle = title.trim().ifBlank { "Field Incident" }.take(160)
            val sanitizedPrompt = promptSummary.trim().ifBlank { sanitizedTitle }.take(1200)
            val sanitizedGuidance = guidanceSummary.trim().ifBlank { "Guidance recorded." }.take(6000)
            val sanitizedSteps = checklistSteps.map { it.trim() }.filter { it.isNotBlank() }.take(25)
            val sanitizedDoNot = doNotDoNotes?.trim()?.takeIf { it.isNotBlank() }?.take(1500)
            val safeLatency = latencyMs.coerceIn(0L, 300_000L)

            val rawPayload = mutableMapOf<String, Any?>(
                "userId" to uid,
                "category" to category.name,
                "title" to sanitizedTitle,
                "promptSummary" to sanitizedPrompt,
                "urgencyLevel" to urgency.name,
                "guidanceSummary" to sanitizedGuidance,
                "checklistSteps" to sanitizedSteps,
                "doNotDoNotes" to sanitizedDoNot,
                "inferenceSource" to inferenceSource.name,
                "latencyMs" to safeLatency,
                "platform" to "ANDROID",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )

            @Suppress("UNCHECKED_CAST")
            val payload = rawPayload.filterValues { it != null } as Map<String, Any>

            // Queue write into Firestore local cache immediately (resolves when online or immediately from local persistence)
            val writeTask = docRef.set(payload)
            // If offline, Firestore queues the write locally and triggers snapshot listeners immediately.
            // We attach a listener for error logging rather than blocking indefinitely offline.
            writeTask.addOnFailureListener { e ->
                handleFirestoreError(e, OperationType.CREATE, docPath)
            }
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteIncident(incidentId: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/incidents/$incidentId"
        return try {
            val uid = requireUserId()
            require(incidentId.isNotBlank()) { "Incident ID must not be blank." }
            val docRef = db.collection("users").document(uid).collection("incidents").document(incidentId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun syncTelemetryEntry(entry: RoutingDecisionLog): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.success(Unit)
        val docRef = db.collection("users").document(uid).collection("telemetry").document()
        val docPath = docRef.path
        return try {
            val payload = mapOf(
                "userId" to uid,
                "taskType" to entry.taskType.trim().ifBlank { "Hybrid Task" }.take(80),
                "requestedMode" to entry.requestedMode.take(40),
                "actualSource" to entry.actualSource.name,
                "latencyMs" to entry.latencyMs.coerceIn(0L, 300_000L),
                "promptChars" to entry.promptChars.coerceIn(0, 50_000),
                "routingReason" to entry.routingReason.trim().ifBlank { "Routed by HybridAIRouter" }.take(300),
                "createdAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).addOnFailureListener { e ->
                handleFirestoreError(e, OperationType.CREATE, docPath)
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    companion object {
        private fun createConfiguredFirestore(appContext: Context): FirebaseFirestore {
            val databaseId = appContext.getString(R.string.firestore_database_id)
            val instance = FirebaseFirestore.getInstance(databaseId)
            runCatching {
                instance.firestoreSettings = firestoreSettings {
                    setLocalCacheSettings(persistentCacheSettings {
                        setSizeBytes(50L * 1024L * 1024L)
                    })
                }
            }.onFailure {
                // Settings can only be applied once before first Firestore call; safe to ignore if already set
                Log.d(REPO_TAG, "Firestore settings already initialized: ${it.message}")
            }
            return instance
        }
    }
}
