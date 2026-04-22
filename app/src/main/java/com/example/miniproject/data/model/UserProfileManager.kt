package com.example.miniproject.data.model

import com.example.miniproject.data.FirebaseRepository

/**
 * Module 5: User Profile & Health Settings
 * Stores user demographics to personalize calculations
 */

enum class UnitsPreference {
    METRIC,      // km, kg, cm
    IMPERIAL     // miles, lbs, inches
}

class UserProfileManager(private val firebaseRepository: FirebaseRepository) {
    private val TAG = "UserProfileManager"
    
    /**
     * Calculate stride length based on height and gender
     * Formula: height(cm) × multiplier
     * Male: height × 0.415
     * Female: height × 0.413
     */
    fun calculateStrideLength(profile: UserProfile): Double {
        val multiplier = if (profile.gender.equals("Male", ignoreCase = true)) 0.415 else 0.413
        val stride = (profile.height * multiplier) / 100.0 // Convert to meters
        
        android.util.Log.d(TAG, """
            📏 Stride Calculation:
            ├─ Height: ${profile.height} cm
            ├─ Gender: ${profile.gender}
            ├─ Multiplier: $multiplier
            └─ Stride: ${String.format("%.3f", stride)} m
        """.trimIndent())
        
        return stride
    }
    
    /**
     * Calculate calories burned during a run
     * Formula: distance(km) × weight(kg) × 1.036
     */
    fun calculateCalories(distanceKm: Double, profile: UserProfile): Double {
        val calories = distanceKm * profile.weight * 1.036
        
        android.util.Log.d(TAG, """
            🔥 Calorie Calculation:
            ├─ Distance: ${String.format("%.2f", distanceKm)} km
            ├─ Weight: ${profile.weight} kg
            ├─ Formula: distance × weight × 1.036
            └─ Calories: ${String.format("%.1f", calories)} cal
        """.trimIndent())
        
        return calories
    }
    
    /**
     * Estimate distance from step count using stride
     * Formula: steps × stride length
     */
    fun estimateDistanceFromSteps(stepCount: Int, profile: UserProfile): Double {
        val stride = calculateStrideLength(profile)
        val distanceMeters = stepCount * stride
        val distanceKm = distanceMeters / 1000.0
        
        android.util.Log.d(TAG, """
            📍 Distance from Steps:
            ├─ Steps: $stepCount
            ├─ Stride: ${String.format("%.3f", stride)} m
            └─ Distance: ${String.format("%.3f", distanceKm)} km
        """.trimIndent())
        
        return distanceKm
    }
    
    /**
     * Check if user is meeting their weekly goal
     */
    fun isWeeklyGoalMet(achievedValue: Double, profile: UserProfile): Boolean {
        // Default weekly goal since current UserProfile model has no weeklyGoalValue field
        val weeklyGoalValue = 30.0
        val met = achievedValue >= weeklyGoalValue
        
        android.util.Log.d(TAG, """
            🎯 Goal Check:
            ├─ Goal Type: DISTANCE
            ├─ Target: $weeklyGoalValue
            ├─ Achieved: ${String.format("%.2f", achievedValue)}
            └─ Status: ${if (met) " ACHIEVED" else "NOT MET"}
        """.trimIndent())
        
        return met
    }
    
    /**
     * Get recommended pace based on goal
     */
    fun getRecommendedPace(profile: UserProfile): Double {
        // Default target pace since current UserProfile model has no targetPaceMinKm field
        return 6.0
    }
    
    /**
     * Convert units based on preference
     */
    fun convertDistance(distanceKm: Double, profile: UserProfile): Double {
        // Current UserProfile model has no preferredUnits field; keep metric by default
        return distanceKm
    }
    
    /**
     * Get unit label
     */
    fun getDistanceUnit(profile: UserProfile): String {
        return "km"
    }
    
    /**
     * Convert weight based on preference
     */
    fun convertWeight(weightKg: Int, profile: UserProfile): Double {
        return weightKg.toDouble()
    }
    
    /**
     * Get weight unit label
     */
    fun getWeightUnit(profile: UserProfile): String {
        return "kg"
    }
    
    /**
     * Save user profile to database
     */
    suspend fun saveProfile(profile: UserProfile): Boolean {
        return try {
            firebaseRepository.saveUserProfile(profile)
        } catch (e: Exception) {
            android.util.Log.e(TAG, " Error saving profile: ${e.message}", e)
            false
        }
    }
    
    /**
     * Load user profile from database
     */
    suspend fun loadProfile(userId: String): UserProfile? {
        return try {
            firebaseRepository.getUserProfile(userId)
        } catch (e: Exception) {
            android.util.Log.e(TAG, " Error loading profile: ${e.message}", e)
            null
        }
    }
    
    /**
     * Generate personalized recommendation
     */
    fun getPersonalizedRecommendation(profile: UserProfile): String {
        return buildString {
            append("💡 Personalized Insights:\n")
            append("├─ Stride Length: ${String.format("%.2f", calculateStrideLength(profile))}m\n")
            append("├─ Target Pace: ${getRecommendedPace(profile)} min/km\n")
            append("├─ Weekly Goal: 30.0 ${getDistanceUnit(profile)}\n")
            append("├─ Weight: ${convertWeight(profile.weight, profile)} ${getWeightUnit(profile)}\n")
            if (profile.gender.equals("Male", ignoreCase = true)) {
                append("├─ Using male stride multiplier (0.415)\n")
            } else {
                append("├─ Using female stride multiplier (0.413)\n")
            }
            append("└─ Calories burned per km: ${String.format("%.1f", profile.weight * 1.036)} cal")
        }
    }
}
