package com.example.miniproject.data

import android.util.Log
import com.example.miniproject.data.model.GPSPoint
import com.example.miniproject.data.model.RunSession
import com.example.miniproject.data.model.UserProfile
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.QueryDocumentSnapshot
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

class FirebaseRepository {
    companion object {
        private const val TAG = "FirebaseRepository"
        private const val MAX_EMBEDDED_ROUTE_POINTS = 300
    }

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val usersCollection = "users"
    private val runsCollection = "runs"
    private val gpsPointsCollection = "gps_points"

    suspend fun saveUserProfile(userProfile: UserProfile): Boolean {
        return try {
            val authUid = auth.currentUser?.uid
            if (authUid.isNullOrBlank()) {
                Log.w(TAG, "saveUserProfile rejected: unauthenticated user")
                return false
            }

            if (userProfile.userId.isNotBlank() && userProfile.userId != authUid) {
                Log.w(TAG, "saveUserProfile rejected: profile userId mismatch")
                return false
            }

            val now = System.currentTimeMillis()
            val profileData = mutableMapOf<String, Any>(
                "userId" to authUid,
                "name" to userProfile.name.trim(),
                "email" to userProfile.email.ifBlank { auth.currentUser?.email ?: "" },
                "height" to userProfile.height,
                "weight" to userProfile.weight,
                "gender" to userProfile.gender,
                "age" to userProfile.age,
                "profileImageUrl" to userProfile.profileImageUrl,
                "updatedAt" to now
            )

            if (userProfile.createdAt > 0) {
                profileData["createdAt"] = userProfile.createdAt
            } else {
                profileData["createdAt"] = now
            }

            firestore.collection(usersCollection)
                .document(authUid)
                .set(profileData, SetOptions.merge())
                .await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "saveUserProfile failed: ${e.message}", e)
            e.printStackTrace()
            false
        }
    }

    suspend fun getUserProfile(userId: String): UserProfile? {
        return try {
            val document = firestore.collection(usersCollection).document(userId).get().await()
            document.toObject(UserProfile::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun saveRunSession(runSession: RunSession): Boolean {
        return try {
            val userId = resolveUserId(runSession.userId) ?: return false
            val runData = buildRunData(runSession, userId)

            firestore.collection(runsCollection).document(runSession.runId).set(runData).await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "saveRunSession failed: ${e.message}", e)
            false
        }
    }

    suspend fun saveRunSessionWithRoute(
        runSession: RunSession,
        points: List<GPSPoint>,
        saveLegacyPointsCollection: Boolean = true
    ): Boolean {
        return try {
            val userId = resolveUserId(runSession.userId) ?: return false
            val runId = runSession.runId.ifBlank { return false }

            val normalizedPoints = points
                .filter { it.latitude.isFinite() && it.longitude.isFinite() }
                .filter { it.latitude != 0.0 || it.longitude != 0.0 }
                .sortedBy { it.timestamp }

            val runData = buildRunData(
                runSession.copy(pathPointsCount = normalizedPoints.size),
                userId
            )

            if (normalizedPoints.isNotEmpty() && normalizedPoints.size <= MAX_EMBEDDED_ROUTE_POINTS) {
                val routePointMaps = normalizedPoints.map { point ->
                    mapOf(
                        "latitude" to sanitizeDouble(point.latitude),
                        "longitude" to sanitizeDouble(point.longitude),
                        "timestamp" to point.timestamp,
                        "speed" to sanitizeDouble(point.speed.toDouble()),
                        "altitude" to sanitizeDouble(point.altitude),
                        "accuracy" to sanitizeDouble(point.accuracy.toDouble())
                    )
                }
                runData["routePoints"] = routePointMaps
            }

            firestore.collection(runsCollection).document(runId).set(runData).await()

            if (saveLegacyPointsCollection && normalizedPoints.isNotEmpty()) {
                val gpsSaved = saveGPSPoints(runId, normalizedPoints, userId)
                if (!gpsSaved) {
                    Log.w(TAG, "Run metadata saved but GPS points save failed for runId=$runId")
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "saveRunSessionWithRoute failed: ${e.message}", e)
            false
        }
    }

    suspend fun saveGPSPoints(runId: String, points: List<GPSPoint>, explicitUserId: String = ""): Boolean {
        return try {
            if (runId.isBlank()) return false

            val userId = resolveUserId(explicitUserId) ?: return false
            val sanitizedPoints = points
                .filter { it.latitude.isFinite() && it.longitude.isFinite() }
                .sortedBy { it.timestamp }

            sanitizedPoints.chunked(450).forEach { chunk ->
                val batch = firestore.batch()
                chunk.forEach { point ->
                    val pointData = mutableMapOf<String, Any>()
                    pointData["runId"] = runId
                    pointData["userId"] = userId
                    pointData["latitude"] = sanitizeDouble(point.latitude)
                    pointData["longitude"] = sanitizeDouble(point.longitude)
                    pointData["timestamp"] = point.timestamp
                    pointData["speed"] = sanitizeDouble(point.speed.toDouble())
                    pointData["altitude"] = sanitizeDouble(point.altitude)
                    pointData["accuracy"] = sanitizeDouble(point.accuracy.toDouble())

                    batch.set(firestore.collection(gpsPointsCollection).document(), pointData)
                }
                batch.commit().await()
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "saveGPSPoints failed: ${e.message}", e)
            false
        }
    }

    suspend fun getRunSessions(explicitUserId: String? = null): List<RunSession> {
        return try {
            val userId = explicitUserId ?: auth.currentUser?.uid
            if (userId == null) {
                return emptyList()
            }
            val documents = firestore.collection(runsCollection)
                .whereEqualTo("userId", userId)
                .get().await()

            documents.documents.mapNotNull { doc ->
                doc.toObject(RunSession::class.java)?.copy(runId = doc.id)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun getRunSessionById(runId: String): RunSession? {
        return try {
            val doc = firestore.collection(runsCollection).document(runId).get().await()
            doc.toObject(RunSession::class.java)?.copy(runId = doc.id)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun observeRunSessionsRealtime(
        onData: (List<RunSession>) -> Unit,
        onError: (String) -> Unit
    ): ListenerRegistration? {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            onError("User not authenticated")
            return null
        }

        return firestore.collection(runsCollection)
            .whereEqualTo("userId", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error.message ?: "Realtime listener error")
                    return@addSnapshotListener
                }

                val sessions = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(RunSession::class.java)?.copy(runId = doc.id)
                } ?: emptyList()
                onData(sessions)
            }
    }

    suspend fun getGPSPoints(runId: String): List<GPSPoint> {
        return try {
            val documents = firestore.collection(gpsPointsCollection)
                .whereEqualTo("runId", runId)
                .get().await()

            documents.toObjects(GPSPoint::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun getEmbeddedRoutePoints(runId: String): List<GPSPoint> {
        return try {
            val runDoc = firestore.collection(runsCollection).document(runId).get().await()
            val rawRoutePoints = runDoc.get("routePoints") as? List<*> ?: return emptyList()

            rawRoutePoints.mapNotNull { rawPoint ->
                val map = rawPoint as? Map<*, *> ?: return@mapNotNull null
                val latitude = (map["latitude"] as? Number)?.toDouble() ?: return@mapNotNull null
                val longitude = (map["longitude"] as? Number)?.toDouble() ?: return@mapNotNull null

                GPSPoint(
                    runId = runId,
                    latitude = latitude,
                    longitude = longitude,
                    timestamp = (map["timestamp"] as? Number)?.toLong() ?: 0L,
                    speed = (map["speed"] as? Number)?.toFloat() ?: 0f,
                    altitude = (map["altitude"] as? Number)?.toDouble() ?: 0.0,
                    accuracy = (map["accuracy"] as? Number)?.toFloat() ?: 0f
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "getEmbeddedRoutePoints failed: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun getRunRoutePoints(runId: String): List<GPSPoint> {
        val embedded = getEmbeddedRoutePoints(runId)
        if (embedded.isNotEmpty()) {
            return embedded.sortedBy { it.timestamp }
        }

        return getGPSPoints(runId).sortedBy { it.timestamp }
    }

    suspend fun deleteRunSession(runId: String): Boolean {
        return deleteRunWithGps(runId)
    }

    suspend fun deleteRunWithGps(runId: String): Boolean {
        return try {
            val gpsDocs = firestore.collection(gpsPointsCollection)
                .whereEqualTo("runId", runId)
                .get()
                .await()
                .documents

            val runDoc = firestore.collection(runsCollection).document(runId).get().await()
            val docsToDelete = mutableListOf<QueryDocumentSnapshot>()
            docsToDelete.addAll(gpsDocs.filterIsInstance<QueryDocumentSnapshot>())

            deleteInChunks(
                runDocIds = if (runDoc.exists()) listOf(runDoc.id) else emptyList(),
                gpsDocIds = docsToDelete.map { it.id }
            )
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun deleteAllUserRunData(explicitUserId: String? = null): Boolean {
        return try {
            val userId = explicitUserId ?: auth.currentUser?.uid ?: return false

            val runIds = firestore.collection(runsCollection)
                .whereEqualTo("userId", userId)
                .get()
                .await()
                .documents
                .map { it.id }

            val gpsDocIds = firestore.collection(gpsPointsCollection)
                .whereEqualTo("userId", userId)
                .get()
                .await()
                .documents
                .map { it.id }

            deleteInChunks(runIds, gpsDocIds)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private suspend fun deleteInChunks(runDocIds: List<String>, gpsDocIds: List<String>) {
        val allDocRefs = mutableListOf<com.google.firebase.firestore.DocumentReference>()
        runDocIds.forEach { allDocRefs.add(firestore.collection(runsCollection).document(it)) }
        gpsDocIds.forEach { allDocRefs.add(firestore.collection(gpsPointsCollection).document(it)) }

        allDocRefs.chunked(450).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { docRef ->
                batch.delete(docRef)
            }
            batch.commit().await()
        }
    }

    private fun buildRunData(runSession: RunSession, userId: String): MutableMap<String, Any> {
        val runData = mutableMapOf<String, Any>()
        runData["userId"] = userId
        runData["startTime"] = runSession.startTime.coerceAtLeast(0L)
        runData["endTime"] = runSession.endTime.coerceAtLeast(0L)
        runData["duration"] = runSession.duration.coerceAtLeast(0L)
        runData["distance"] = sanitizeDouble(runSession.distance)
        runData["avgSpeed"] = sanitizeDouble(runSession.avgSpeed)
        runData["maxSpeed"] = sanitizeDouble(runSession.maxSpeed)
        runData["steps"] = runSession.steps.coerceAtLeast(0)
        runData["calories"] = sanitizeDouble(runSession.calories)
        runData["pathPointsCount"] = runSession.pathPointsCount.coerceAtLeast(0)
        runData["title"] = runSession.title.ifBlank { "Running Session" }
        runData["notes"] = runSession.notes
        runData["createdAt"] = if (runSession.createdAt > 0L) {
            runSession.createdAt
        } else {
            System.currentTimeMillis()
        }
        runData["runId"] = runSession.runId
        return runData
    }

    private fun sanitizeDouble(value: Double): Double {
        return if (value.isFinite()) value else 0.0
    }

    private suspend fun resolveUserId(fallbackUserId: String): String? {
        auth.currentUser?.uid?.let { return it }

        val anonymousUid = try {
            auth.signInAnonymously().await().user?.uid
        } catch (e: Exception) {
            Log.e(TAG, "resolveUserId failed: ${e.message}", e)
            null
        }

        if (!anonymousUid.isNullOrBlank()) {
            return anonymousUid
        }

        return fallbackUserId.ifBlank { null }
    }
}
