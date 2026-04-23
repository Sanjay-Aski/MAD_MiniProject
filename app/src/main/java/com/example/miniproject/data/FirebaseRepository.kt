package com.example.miniproject.data

import android.util.Log
import com.example.miniproject.data.model.DiscoveringUser
import com.example.miniproject.data.model.Friend
import com.example.miniproject.data.model.FriendRequest
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

    // =====================================================================
    // FRIEND DISCOVERY
    // =====================================================================

    /**
     * Publish this user to `discovery/{uid}` so others can see them.
     * The document expires (discoveringUntil) after [durationMs] ms.
     */
    suspend fun publishDiscoveryPresence(
        name: String,
        email: String,
        totalSteps: Long,
        totalCalories: Double,
        durationMs: Long = 60_000L
    ) {
        val uid = auth.currentUser?.uid ?: return
        val data = mapOf(
            "userId"           to uid,
            "name"             to name,
            "email"            to email,
            "discoveringUntil" to System.currentTimeMillis() + durationMs,
            "totalSteps"       to totalSteps,
            "totalCalories"    to totalCalories
        )
        try {
            firestore.collection("discovery").document(uid).set(data).await()
            Log.d(TAG, "Discovery presence published")
        } catch (e: Exception) {
            Log.e(TAG, "publishDiscoveryPresence failed: ${e.message}", e)
        }
    }

    /** Remove our own discovery presence document */
    suspend fun removeDiscoveryPresence() {
        val uid = auth.currentUser?.uid ?: return
        try {
            firestore.collection("discovery").document(uid).delete().await()
        } catch (e: Exception) {
            Log.e(TAG, "removeDiscoveryPresence failed: ${e.message}", e)
        }
    }

    /**
     * Real-time listener on `discovery` — calls [onUpdate] whenever the list changes.
     * Filters out stale entries (discoveringUntil < now) and our own entry.
     */
    fun observeDiscoveringUsers(onUpdate: (List<DiscoveringUser>) -> Unit): ListenerRegistration {
        val myUid = auth.currentUser?.uid ?: ""
        return firestore.collection("discovery")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "observeDiscoveringUsers error: ${error.message}")
                    return@addSnapshotListener
                }
                val now = System.currentTimeMillis()
                val list = snapshot?.documents
                    ?.filter { it.id != myUid }
                    ?.mapNotNull { doc ->
                        val until = doc.getLong("discoveringUntil") ?: 0L
                        if (until < now) return@mapNotNull null
                        DiscoveringUser(
                            userId         = doc.getString("userId") ?: "",
                            name           = doc.getString("name") ?: "",
                            email          = doc.getString("email") ?: "",
                            discoveringUntil = until,
                            totalSteps     = doc.getLong("totalSteps") ?: 0L,
                            totalCalories  = doc.getDouble("totalCalories") ?: 0.0
                        )
                    } ?: emptyList()
                onUpdate(list)
            }
    }

    // =====================================================================
    // FRIEND REQUESTS
    // =====================================================================

    /**
     * Send a friend request to [toUserId].
     * Writes to `friend_requests/{toUserId}/incoming/{myUid}`.
     */
    suspend fun sendFriendRequest(toUserId: String, toName: String = ""): Boolean {
        val myUid  = auth.currentUser?.uid ?: return false
        val myName  = auth.currentUser?.displayName
            ?: firestore.collection("users").document(myUid)
                .get().await().getString("name") ?: "Runner"
        val myEmail = auth.currentUser?.email ?: ""
        return try {
            val data = mapOf(
                "requestId"  to myUid,
                "fromUserId" to myUid,
                "fromName"   to myName,
                "fromEmail"  to myEmail,
                "toUserId"   to toUserId,
                "sentAt"     to System.currentTimeMillis(),
                "status"     to FriendRequest.STATUS_PENDING
            )
            firestore.collection("friend_requests")
                .document(toUserId)
                .collection("incoming")
                .document(myUid)
                .set(data).await()
            Log.d(TAG, "Friend request sent to $toUserId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "sendFriendRequest failed: ${e.message}", e)
            false
        }
    }

    /**
     * Real-time listener for incoming requests to the current user.
     * Listens on `friend_requests/{myUid}/incoming`.
     */
    fun observeIncomingRequests(onUpdate: (List<FriendRequest>) -> Unit): ListenerRegistration? {
        val myUid = auth.currentUser?.uid ?: return null
        return firestore.collection("friend_requests")
            .document(myUid)
            .collection("incoming")
            .whereEqualTo("status", FriendRequest.STATUS_PENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "observeIncomingRequests error: ${error.message}")
                    return@addSnapshotListener
                }
                val requests = snapshot?.documents?.mapNotNull { doc ->
                    FriendRequest(
                        requestId  = doc.id,
                        fromUserId = doc.getString("fromUserId") ?: "",
                        fromName   = doc.getString("fromName") ?: "",
                        fromEmail  = doc.getString("fromEmail") ?: "",
                        toUserId   = doc.getString("toUserId") ?: "",
                        sentAt     = doc.getLong("sentAt") ?: 0L,
                        status     = doc.getString("status") ?: FriendRequest.STATUS_PENDING
                    )
                } ?: emptyList()
                onUpdate(requests)
            }
    }

    /**
     * Accept [fromUserId]'s request:
     * 1. Add each user to the other's `users/{uid}/friends` sub-collection
     * 2. Update the request status to "accepted"
     * 3. Fetch and write the requester's stats too
     */
    suspend fun acceptFriendRequest(fromUserId: String, fromName: String, fromEmail: String): Boolean {
        val myUid = auth.currentUser?.uid ?: return false
        return try {
            val now     = System.currentTimeMillis()
            val myStats = getMyStats()

            // Fetch requester's stats
            val theirDoc = firestore.collection("users").document(fromUserId)
                .collection("stats").document("summary").get().await()
            val theirSteps    = theirDoc.getLong("totalSteps") ?: 0L
            val theirCalories = theirDoc.getDouble("totalCalories") ?: 0.0
            val theirDistance = theirDoc.getDouble("totalDistance") ?: 0.0
            val theirRuns     = theirDoc.getLong("totalRuns")?.toInt() ?: 0

            val myName  = auth.currentUser?.displayName
                ?: firestore.collection("users").document(myUid)
                    .get().await().getString("name") ?: "Runner"
            val myEmail = auth.currentUser?.email ?: ""

            // Write friend record on MY side
            firestore.collection("users").document(myUid)
                .collection("friends").document(fromUserId)
                .set(mapOf(
                    "friendUserId"   to fromUserId,
                    "friendName"     to fromName,
                    "friendEmail"    to fromEmail,
                    "addedAt"        to now,
                    "totalSteps"     to theirSteps,
                    "totalCalories"  to theirCalories,
                    "totalDistance"  to theirDistance,
                    "totalRuns"      to theirRuns
                )).await()

            // Write friend record on THEIR side
            firestore.collection("users").document(fromUserId)
                .collection("friends").document(myUid)
                .set(mapOf(
                    "friendUserId"   to myUid,
                    "friendName"     to myName,
                    "friendEmail"    to myEmail,
                    "addedAt"        to now,
                    "totalSteps"     to myStats.first,
                    "totalCalories"  to myStats.second,
                    "totalDistance"  to myStats.third,
                    "totalRuns"      to myStats.fourth
                )).await()

            // Mark request accepted
            firestore.collection("friend_requests")
                .document(myUid).collection("incoming").document(fromUserId)
                .update("status", FriendRequest.STATUS_ACCEPTED).await()

            Log.d(TAG, "Friend request accepted: $fromUserId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "acceptFriendRequest failed: ${e.message}", e)
            false
        }
    }

    /** Reject and delete a friend request */
    suspend fun rejectFriendRequest(fromUserId: String): Boolean {
        val myUid = auth.currentUser?.uid ?: return false
        return try {
            firestore.collection("friend_requests")
                .document(myUid).collection("incoming").document(fromUserId)
                .delete().await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "rejectFriendRequest failed: ${e.message}", e)
            false
        }
    }

    // =====================================================================
    // FRIENDS LIST  (real-time)
    // =====================================================================

    /**
     * Real-time listener for the current user's friends list.
     * Listens on `users/{myUid}/friends`.
     */
    fun observeFriends(onUpdate: (List<Friend>) -> Unit): ListenerRegistration? {
        val myUid = auth.currentUser?.uid ?: return null
        return firestore.collection("users").document(myUid)
            .collection("friends")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "observeFriends error: ${error.message}")
                    return@addSnapshotListener
                }
                val friends = snapshot?.documents?.mapNotNull { doc ->
                    Friend(
                        friendUserId   = doc.getString("friendUserId") ?: return@mapNotNull null,
                        friendName     = doc.getString("friendName") ?: "",
                        friendEmail    = doc.getString("friendEmail") ?: "",
                        addedAt        = doc.getLong("addedAt") ?: 0L,
                        totalSteps     = doc.getLong("totalSteps") ?: 0L,
                        totalCalories  = doc.getDouble("totalCalories") ?: 0.0,
                        totalDistance  = doc.getDouble("totalDistance") ?: 0.0,
                        totalRuns      = doc.getLong("totalRuns")?.toInt() ?: 0
                    )
                } ?: emptyList()
                onUpdate(friends)
            }
    }

    /**
     * Remove a friend from both sides.
     */
    suspend fun removeFriend(friendUserId: String): Boolean {
        val myUid = auth.currentUser?.uid ?: return false
        return try {
            firestore.collection("users").document(myUid)
                .collection("friends").document(friendUserId).delete().await()
            firestore.collection("users").document(friendUserId)
                .collection("friends").document(myUid).delete().await()
            Log.d(TAG, "Removed friend $friendUserId (both sides)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "removeFriend failed: ${e.message}", e)
            false
        }
    }

    // =====================================================================
    // USER STATS  (written after every run so friends see up-to-date data)
    // =====================================================================

    /**
     * Update `users/{uid}/stats/summary` and then refresh all friends'
     * cached copy of our stats so their leaderboards stay current.
     */
    suspend fun updateMyStats(
        totalSteps: Long,
        totalCalories: Double,
        totalDistance: Double,
        totalRuns: Int
    ) {
        val myUid = auth.currentUser?.uid ?: return
        try {
            val statsData = mapOf(
                "totalSteps"    to totalSteps,
                "totalCalories" to totalCalories,
                "totalDistance" to totalDistance,
                "totalRuns"     to totalRuns,
                "updatedAt"     to System.currentTimeMillis()
            )
            // Save my own stats summary
            firestore.collection("users").document(myUid)
                .collection("stats").document("summary")
                .set(statsData, SetOptions.merge()).await()

            // Update cached stats in all friends' documents
            val friendDocs = firestore.collection("users").document(myUid)
                .collection("friends").get().await()
            for (doc in friendDocs.documents) {
                val friendId = doc.getString("friendUserId") ?: continue
                firestore.collection("users").document(friendId)
                    .collection("friends").document(myUid)
                    .update(mapOf(
                        "totalSteps"    to totalSteps,
                        "totalCalories" to totalCalories,
                        "totalDistance" to totalDistance,
                        "totalRuns"     to totalRuns
                    )).await()
            }
            Log.d(TAG, "Stats updated and propagated to friends")
        } catch (e: Exception) {
            Log.e(TAG, "updateMyStats failed: ${e.message}", e)
        }
    }

    /** Return (steps, calories, distance, runs) for the current user */
    private suspend fun getMyStats(): Quadruple<Long, Double, Double, Int> {
        val myUid = auth.currentUser?.uid ?: return Quadruple(0L, 0.0, 0.0, 0)
        return try {
            val doc = firestore.collection("users").document(myUid)
                .collection("stats").document("summary").get().await()
            Quadruple(
                doc.getLong("totalSteps") ?: 0L,
                doc.getDouble("totalCalories") ?: 0.0,
                doc.getDouble("totalDistance") ?: 0.0,
                doc.getLong("totalRuns")?.toInt() ?: 0
            )
        } catch (e: Exception) {
            Quadruple(0L, 0.0, 0.0, 0)
        }
    }

    /** Simple 4-tuple helper (Kotlin has only up to Triple in stdlib) */
    private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
}

