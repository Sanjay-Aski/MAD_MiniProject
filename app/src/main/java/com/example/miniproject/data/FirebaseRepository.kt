package com.example.miniproject.data

import com.example.miniproject.data.model.GPSPoint
import com.example.miniproject.data.model.RunSession
import com.example.miniproject.data.model.UserProfile
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class FirebaseRepository {
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val usersCollection = "users"
    private val runsCollection = "runs"
    private val gpsPointsCollection = "gps_points"

    suspend fun saveUserProfile(userProfile: UserProfile): Boolean {
        return try {
            val userId = auth.currentUser?.uid ?: return false
            firestore.collection(usersCollection).document(userId).set(userProfile).await()
            true
        } catch (e: Exception) {
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
            val userId = auth.currentUser?.uid ?: return false
            val runData = mutableMapOf<String, Any>()
            runData["userId"] = userId
            runData["startTime"] = runSession.startTime
            runData["endTime"] = runSession.endTime
            runData["duration"] = runSession.duration
            runData["distance"] = runSession.distance
            runData["avgSpeed"] = runSession.avgSpeed
            runData["maxSpeed"] = runSession.maxSpeed
            runData["steps"] = runSession.steps
            runData["calories"] = runSession.calories
            runData["pathPointsCount"] = runSession.pathPointsCount
            runData["title"] = runSession.title
            runData["notes"] = runSession.notes
            runData["createdAt"] = runSession.createdAt

            firestore.collection(runsCollection).document(runSession.runId).set(runData).await()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun saveGPSPoints(runId: String, points: List<GPSPoint>): Boolean {
        return try {
            val userId = auth.currentUser?.uid ?: return false
            for (point in points) {
                val pointData = mutableMapOf<String, Any>()
                pointData["runId"] = runId
                pointData["userId"] = userId
                pointData["latitude"] = point.latitude
                pointData["longitude"] = point.longitude
                pointData["timestamp"] = point.timestamp
                pointData["speed"] = point.speed
                pointData["altitude"] = point.altitude
                pointData["accuracy"] = point.accuracy

                firestore.collection(gpsPointsCollection).add(pointData).await()
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun getRunSessions(): List<RunSession> {
        return try {
            val userId = auth.currentUser?.uid ?: return emptyList()
            val documents = firestore.collection(runsCollection)
                .whereEqualTo("userId", userId)
                .get().await()

            documents.toObjects(RunSession::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
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

    suspend fun deleteRunSession(runId: String): Boolean {
        return try {
            firestore.collection(runsCollection).document(runId).delete().await()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
