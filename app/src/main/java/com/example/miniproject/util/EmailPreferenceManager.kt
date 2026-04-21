package com.example.miniproject.util

import android.content.Context
import com.google.firebase.auth.FirebaseAuth

/**
 * Utility class to manage email preferences and session data
 */
object EmailPreferenceManager {
    private const val PREFS_NAME = "email_preferences"
    private const val SELECTED_EMAIL = "selected_email"
    private const val LAST_LOGIN_TIME = "last_login_time"
    private const val LOGGED_IN_USER_ID = "logged_in_user_id"

    /**
     * Save selected email after login
     */
    fun saveSelectedEmail(context: Context, email: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString(SELECTED_EMAIL, email)
            putLong(LAST_LOGIN_TIME, System.currentTimeMillis())
            putString(LOGGED_IN_USER_ID, FirebaseAuth.getInstance().currentUser?.uid ?: "")
            apply()
        }
    }

    /**
     * Get currently selected email
     */
    fun getSelectedEmail(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(SELECTED_EMAIL, null)
    }

    /**
     * Get last login time
     */
    fun getLastLoginTime(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong(LAST_LOGIN_TIME, 0)
    }

    /**
     * Get logged in user ID
     */
    fun getLoggedInUserId(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(LOGGED_IN_USER_ID, null)
    }

    /**
     * Check if user is logged in and has valid session
     */
    fun isUserLoggedIn(context: Context): Boolean {
        val currentUser = FirebaseAuth.getInstance().currentUser
        return currentUser != null && !getSelectedEmail(context).isNullOrBlank()
    }

    /**
     * Clear email preferences on logout
     */
    fun clearEmailPreferences(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }

    /**
     * Get all login history
     */
    fun getLoginHistory(context: Context): List<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val historyJson = prefs.getString("login_history", "[]") ?: "[]"
        
        // Parse and return (simplified)
        return listOf(getSelectedEmail(context) ?: "unknown")
    }
}
