package com.example.miniproject.util

import android.content.Context

data class RunTargetSettings(
    val distanceKm: Double,
    val steps: Int,
    val calories: Double,
    val durationMinutes: Int
)

object RunTargetSettingsStore {
    private const val PREFS_NAME = "run_target_settings"
    private const val KEY_DISTANCE_KM = "target_distance_km"
    private const val KEY_STEPS = "target_steps"
    private const val KEY_CALORIES = "target_calories"
    private const val KEY_DURATION_MIN = "target_duration_min"

    private const val DEFAULT_DISTANCE_KM = 5.0
    private const val DEFAULT_STEPS = 6000
    private const val DEFAULT_CALORIES = 200.0
    private const val DEFAULT_DURATION_MIN = 30

    fun load(context: Context): RunTargetSettings {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return RunTargetSettings(
            distanceKm = prefs.getFloat(KEY_DISTANCE_KM, DEFAULT_DISTANCE_KM.toFloat()).toDouble(),
            steps = prefs.getInt(KEY_STEPS, DEFAULT_STEPS),
            calories = prefs.getFloat(KEY_CALORIES, DEFAULT_CALORIES.toFloat()).toDouble(),
            durationMinutes = prefs.getInt(KEY_DURATION_MIN, DEFAULT_DURATION_MIN)
        )
    }

    fun save(context: Context, settings: RunTargetSettings) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putFloat(KEY_DISTANCE_KM, settings.distanceKm.toFloat())
            .putInt(KEY_STEPS, settings.steps)
            .putFloat(KEY_CALORIES, settings.calories.toFloat())
            .putInt(KEY_DURATION_MIN, settings.durationMinutes)
            .apply()
    }
}
