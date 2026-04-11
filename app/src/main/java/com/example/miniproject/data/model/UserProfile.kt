package com.example.miniproject.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profiles")
data class UserProfile(
    @PrimaryKey
    val userId: String = "",
    val name: String = "",
    val email: String = "",
    val height: Int = 170, // in cm
    val weight: Int = 70, // in kg
    val gender: String = "Male", // Male or Female
    val age: Int = 25,
    val profileImageUrl: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun getStrideLength(): Double {
        return if (gender.equals("Male", ignoreCase = true)) {
            height * 0.415
        } else {
            height * 0.413
        }
    }
}
