package com.example.miniproject.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.miniproject.data.FirebaseRepository
import com.example.miniproject.data.model.UserProfile
import com.example.miniproject.util.LocalProfileStore
import com.example.miniproject.util.RunTargetSettings
import com.example.miniproject.util.RunTargetSettingsStore
import kotlinx.coroutines.launch

/**
 * UserProfileViewModel - Manages user profile and settings
 */
class UserProfileViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val TAG = "UserProfileViewModel"
    }

    private val firebaseRepository = FirebaseRepository()

    // User profile data
    private val _height = MutableLiveData<Int>(170) // cm
    val height: LiveData<Int> = _height

    private val _weight = MutableLiveData<Double>(70.0) // kg
    val weight: LiveData<Double> = _weight

    private val _age = MutableLiveData<Int>(25)
    val age: LiveData<Int> = _age

    private val _gender = MutableLiveData<String>("M") // M or F
    val gender: LiveData<String> = _gender

    private val _name = MutableLiveData<String>("")
    val name: LiveData<String> = _name

    private val _email = MutableLiveData<String>("")
    val email: LiveData<String> = _email

    // Goals
    private val _dailyDistanceGoal = MutableLiveData<Double>(5.0) // km
    val dailyDistanceGoal: LiveData<Double> = _dailyDistanceGoal

    private val _weeklyDistanceGoal = MutableLiveData<Double>(30.0) // km
    val weeklyDistanceGoal: LiveData<Double> = _weeklyDistanceGoal

    private val _dailyStepsGoal = MutableLiveData<Int>(10000)
    val dailyStepsGoal: LiveData<Int> = _dailyStepsGoal

    private val _runDistanceTarget = MutableLiveData<Double>(5.0)
    val runDistanceTarget: LiveData<Double> = _runDistanceTarget

    private val _runStepsTarget = MutableLiveData<Int>(6000)
    val runStepsTarget: LiveData<Int> = _runStepsTarget

    private val _runCaloriesTarget = MutableLiveData<Double>(200.0)
    val runCaloriesTarget: LiveData<Double> = _runCaloriesTarget

    private val _runDurationTargetMinutes = MutableLiveData<Int>(30)
    val runDurationTargetMinutes: LiveData<Int> = _runDurationTargetMinutes

    // Settings
    private val _preferredUnit = MutableLiveData<String>("km") // km or miles
    val preferredUnit: LiveData<String> = _preferredUnit

    private val _notificationsEnabled = MutableLiveData<Boolean>(true)
    val notificationsEnabled: LiveData<Boolean> = _notificationsEnabled

    // Loading and status
    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _successMessage = MutableLiveData<String>()
    val successMessage: LiveData<String> = _successMessage

    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> = _errorMessage

    /**
     * Load user profile from Firebase
     */
    fun loadProfile(userId: String) {
        if (userId.isEmpty()) {
            Log.w(TAG, "Cannot load profile: userId is empty")
            return
        }
        
        viewModelScope.launch {
            try {
                _isLoading.value = true
                Log.d(TAG, "Loading profile for userId: $userId")
                
                val profile = firebaseRepository.getUserProfile(userId)
                
                if (profile != null) {
                    LocalProfileStore.save(getApplication(), profile)
                    _height.value = profile.height
                    _weight.value = profile.weight.toDouble()
                    _age.value = profile.age
                    _gender.value = profile.gender
                    _name.value = profile.name
                    _email.value = profile.email
                    loadRunTargetSettings()
                    Log.d(TAG, "Profile loaded successfully: ${profile.name} (${profile.email})")
                } else {
                    val local = LocalProfileStore.load(getApplication(), userId)
                    if (local != null) {
                        _height.value = local.height
                        _weight.value = local.weight.toDouble()
                        _age.value = local.age
                        _gender.value = local.gender
                        _name.value = local.name
                        _email.value = local.email
                        Log.d(TAG, "Loaded profile from local cache for userId: $userId")
                    } else {
                        Log.d(TAG, "No profile found for userId: $userId, using default values")
                        _height.value = 170
                        _weight.value = 70.0
                        _age.value = 25
                        _gender.value = "M"
                        _name.value = ""
                        _email.value = ""
                    }
                    loadRunTargetSettings()
                }
                
                _isLoading.value = false
            } catch (e: Exception) {
                _errorMessage.value = "Error loading profile: ${e.message}"
                Log.e(TAG, "Error loading profile for userId $userId: ${e.message}", e)
                _isLoading.value = false
            }
        }
    }

    /**
     * Save user profile to Firebase
     */
    fun saveProfile(userId: String) {
        if (userId.isEmpty()) {
            _errorMessage.value = "User ID is required to save profile"
            Log.e(TAG, "Cannot save profile: userId is empty")
            return
        }
        
        viewModelScope.launch {
            try {
                _isLoading.value = true
                Log.d(TAG, "Saving profile for userId: $userId")
                
                val profile = UserProfile(
                    userId = userId,
                    name = _name.value ?: "",
                    email = _email.value ?: "",
                    height = _height.value ?: 170,
                    weight = _weight.value?.toInt() ?: 70,
                    age = _age.value ?: 25,
                    gender = _gender.value ?: "Male"
                )

                LocalProfileStore.save(getApplication(), profile)
                saveRunTargetSettings()
                
                Log.d(TAG, "Profile object created: $profile")
                
                val saved = firebaseRepository.saveUserProfile(profile)
                
                if (saved) {
                    _successMessage.value = "Profile saved successfully!"
                    Log.d(TAG, "Profile saved successfully to Firebase")
                } else {
                    _successMessage.value = "Profile saved locally. Cloud sync failed."
                    Log.w(TAG, "Firebase saveUserProfile returned false, local cache saved")
                }
                
                _isLoading.value = false
            } catch (e: Exception) {
                _errorMessage.value = "Error saving profile: ${e.message}"
                Log.e(TAG, "Error saving profile: ${e.message}", e)
                _isLoading.value = false
            }
        }
    }

    /**
     * Update height
     */
    fun setHeight(heightValue: Int) {
        _height.value = heightValue
    }

    /**
     * Update weight
     */
    fun setWeight(weightValue: Double) {
        _weight.value = weightValue
    }

    /**
     * Update age
     */
    fun setAge(ageValue: Int) {
        _age.value = ageValue
    }

    /**
     * Update gender
     */
    fun setGender(genderValue: String) {
        _gender.value = genderValue
    }

    /**
     * Update name
     */
    fun setName(nameValue: String) {
        _name.value = nameValue
    }

    /**
     * Update email
     */
    fun setEmail(emailValue: String) {
        _email.value = emailValue
    }

    /**
     * Update daily distance goal
     */
    fun setDailyDistanceGoal(goal: Double) {
        _dailyDistanceGoal.value = goal
    }

    /**
     * Update weekly distance goal
     */
    fun setWeeklyDistanceGoal(goal: Double) {
        _weeklyDistanceGoal.value = goal
    }

    /**
     * Update daily steps goal
     */
    fun setDailyStepsGoal(goal: Int) {
        _dailyStepsGoal.value = goal
    }

    fun setRunDistanceTarget(goalKm: Double) {
        _runDistanceTarget.value = goalKm
    }

    fun setRunStepsTarget(goalSteps: Int) {
        _runStepsTarget.value = goalSteps
    }

    fun setRunCaloriesTarget(goalCalories: Double) {
        _runCaloriesTarget.value = goalCalories
    }

    fun setRunDurationTargetMinutes(goalMinutes: Int) {
        _runDurationTargetMinutes.value = goalMinutes
    }

    /**
     * Update preferred unit
     */
    fun setPreferredUnit(unit: String) {
        _preferredUnit.value = unit
    }

    /**
     * Toggle notifications
     */
    fun setNotifications(enabled: Boolean) {
        _notificationsEnabled.value = enabled
    }

    /**
     * Calculate stride length in meters
     * Formula: Height (cm) × 0.415 for men, × 0.413 for women
     */
    fun calculateStride(): Double {
        val heightCm = _height.value?.toDouble() ?: 170.0
        val multiplier = if (_gender.value == "M") 0.415 else 0.413
        return heightCm * multiplier / 100 // Convert to meters
    }

    /**
     * Convert distance based on preferred unit
     */
    fun convertDistance(distanceKm: Double): Double {
        return if (_preferredUnit.value == "miles") {
            distanceKm * 0.621371
        } else {
            distanceKm
        }
    }

    /**
     * Get unit label
     */
    fun getUnitLabel(): String = _preferredUnit.value ?: "km"

    /**
     * Get unit speed label
     */
    fun getSpeedUnitLabel(): String {
        return if (_preferredUnit.value == "miles") "mph" else "km/h"
    }

    fun loadRunTargetSettings() {
        val settings = RunTargetSettingsStore.load(getApplication())
        _runDistanceTarget.value = settings.distanceKm
        _runStepsTarget.value = settings.steps
        _runCaloriesTarget.value = settings.calories
        _runDurationTargetMinutes.value = settings.durationMinutes
    }

    fun saveRunTargetSettings() {
        val settings = RunTargetSettings(
            distanceKm = (_runDistanceTarget.value ?: 5.0).coerceAtLeast(0.1),
            steps = (_runStepsTarget.value ?: 6000).coerceAtLeast(1),
            calories = (_runCaloriesTarget.value ?: 200.0).coerceAtLeast(1.0),
            durationMinutes = (_runDurationTargetMinutes.value ?: 30).coerceAtLeast(1)
        )
        RunTargetSettingsStore.save(getApplication(), settings)
    }

    /**
     * Clear error messages
     */
    fun clearErrors() {
        _errorMessage.value = null
    }

    /**
     * Clear success messages
     */
    fun clearSuccessMessage() {
        _successMessage.value = null
    }

    /**
     * Clear all messages
     */
    fun clearMessages() {
        _successMessage.value = null
        _errorMessage.value = null
    }
}
