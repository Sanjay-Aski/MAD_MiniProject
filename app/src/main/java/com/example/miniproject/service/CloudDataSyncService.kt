package com.example.miniproject.service

import android.content.Context
import android.util.Log
import com.example.miniproject.data.FirebaseRepository
import com.example.miniproject.data.LocalRunRepository
import com.example.miniproject.data.model.UserProfile
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Service to sync cloud data to local system storage
 * Called after successful login to fetch and cache user data
 */
class CloudDataSyncService(private val context: Context) {
    companion object {
        private const val TAG = "CloudDataSyncService"
        private const val SYNC_PREFS = "sync_preferences"
        private const val LAST_SYNC_TIME = "last_sync_time"
        private const val USER_DATA_SYNCED = "user_data_synced"
    }

    private val firebaseRepository = FirebaseRepository()
    private val localRepository = LocalRunRepository(context)
    private val auth = FirebaseAuth.getInstance()
    private val prefs = context.getSharedPreferences(SYNC_PREFS, Context.MODE_PRIVATE)

    /**
     * Sync all user data from cloud to local storage
     * @param userId Current user's ID
     * @return true if sync successful
     */
    suspend fun syncUserDataFromCloud(userId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, " Starting cloud data sync for user: $userId")

                // 1. Fetch user profile from Firebase
                val userProfile = firebaseRepository.getUserProfile(userId)
                if (userProfile != null) {
                    saveSyncedUserProfile(userProfile)
                    Log.d(TAG, " User profile synced: ${userProfile.name}")
                } else {
                    Log.w(TAG, " No user profile found in cloud")
                }

                // 2. Update sync timestamp
                updateSyncTimestamp()

                // 3. Mark user data as synced
                markUserDataSynced()

                Log.d(TAG, " Cloud sync completed successfully")
                true
            } catch (e: Exception) {
                Log.e(TAG, " Cloud sync failed: ${e.message}", e)
                false
            }
        }
    }

    /**
     * Save synced user profile to SharedPreferences for quick access
     */
    private fun saveSyncedUserProfile(profile: UserProfile) {
        val userPrefs = context.getSharedPreferences("user_profile", Context.MODE_PRIVATE)
        userPrefs.edit().apply {
            putString("user_id", profile.userId)
            putString("user_name", profile.name)
            putString("user_email", profile.email)
            putInt("user_height", profile.height)
            putInt("user_weight", profile.weight)
            putString("user_gender", profile.gender)
            putInt("user_age", profile.age)
            putString("user_image_url", profile.profileImageUrl)
            putLong("profile_updated_at", profile.updatedAt)
            apply()
        }
        Log.d(TAG, "💾 User profile cached in local storage")
    }

    /**
     * Get synced user profile from local SharedPreferences
     */
    fun getSyncedUserProfile(): UserProfile? {
        val userPrefs = context.getSharedPreferences("user_profile", Context.MODE_PRIVATE)
        
        val userId = userPrefs.getString("user_id", null) ?: return null
        val name = userPrefs.getString("user_name", "") ?: ""
        val email = userPrefs.getString("user_email", "") ?: ""
        val height = userPrefs.getInt("user_height", 170)
        val weight = userPrefs.getInt("user_weight", 70)
        val gender = userPrefs.getString("user_gender", "Male") ?: "Male"
        val age = userPrefs.getInt("user_age", 25)
        val imageUrl = userPrefs.getString("user_image_url", "") ?: ""
        val updatedAt = userPrefs.getLong("profile_updated_at", System.currentTimeMillis())

        return UserProfile(
            userId = userId,
            name = name,
            email = email,
            height = height,
            weight = weight,
            gender = gender,
            age = age,
            profileImageUrl = imageUrl,
            updatedAt = updatedAt
        )
    }

    /**
     * Check if user data has been synced
     */
    fun isUserDataSynced(): Boolean {
        return prefs.getBoolean(USER_DATA_SYNCED, false)
    }

    /**
     * Check if sync is needed (last sync older than threshold)
     */
    fun isSyncNeeded(thresholdMinutes: Long = 60): Boolean {
        val lastSync = prefs.getLong(LAST_SYNC_TIME, 0)
        val currentTime = System.currentTimeMillis()
        val syncThresholdMs = thresholdMinutes * 60 * 1000

        return (currentTime - lastSync) > syncThresholdMs
    }

    /**
     * Clear all synced data
     */
    fun clearSyncedData() {
        val userPrefs = context.getSharedPreferences("user_profile", Context.MODE_PRIVATE)
        userPrefs.edit().clear().apply()
        
        prefs.edit().apply {
            remove(USER_DATA_SYNCED)
            remove(LAST_SYNC_TIME)
            apply()
        }
        
        Log.d(TAG, "Synced data cleared")
    }

    private fun updateSyncTimestamp() {
        prefs.edit().putLong(LAST_SYNC_TIME, System.currentTimeMillis()).apply()
    }

    private fun markUserDataSynced() {
        prefs.edit().putBoolean(USER_DATA_SYNCED, true).apply()
    }
}
