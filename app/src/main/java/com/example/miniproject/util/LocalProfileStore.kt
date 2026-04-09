package com.example.miniproject.util

import android.content.Context
import com.example.miniproject.data.model.UserProfile

object LocalProfileStore {
    private const val PREFS_NAME = "local_profile_store"

    fun save(context: Context, profile: UserProfile) {
        val userId = profile.userId.ifBlank { return }
        val keyPrefix = "user_${userId}_"
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString("${keyPrefix}name", profile.name)
            .putString("${keyPrefix}email", profile.email)
            .putInt("${keyPrefix}height", profile.height)
            .putInt("${keyPrefix}weight", profile.weight)
            .putString("${keyPrefix}gender", profile.gender)
            .putInt("${keyPrefix}age", profile.age)
            .putLong("${keyPrefix}createdAt", profile.createdAt)
            .putLong("${keyPrefix}updatedAt", profile.updatedAt)
            .apply()
    }

    fun load(context: Context, userId: String): UserProfile? {
        if (userId.isBlank()) return null

        val keyPrefix = "user_${userId}_"
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val name = prefs.getString("${keyPrefix}name", null) ?: return null

        return UserProfile(
            userId = userId,
            name = name,
            email = prefs.getString("${keyPrefix}email", "") ?: "",
            height = prefs.getInt("${keyPrefix}height", 170),
            weight = prefs.getInt("${keyPrefix}weight", 70),
            gender = prefs.getString("${keyPrefix}gender", "Male") ?: "Male",
            age = prefs.getInt("${keyPrefix}age", 25),
            createdAt = prefs.getLong("${keyPrefix}createdAt", System.currentTimeMillis()),
            updatedAt = prefs.getLong("${keyPrefix}updatedAt", System.currentTimeMillis())
        )
    }
}
