package com.example.miniproject.ui.fragments

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.miniproject.LoginActivity
import com.example.miniproject.data.FirebaseRepository
import com.example.miniproject.R
import com.example.miniproject.data.model.UserProfile
import com.example.miniproject.util.LocalProfileStore
import com.example.miniproject.util.RunTargetSettings
import com.example.miniproject.util.RunTargetSettingsStore
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.util.Locale

class AccountSettingsFragment : Fragment() {
    private val firebaseRepository = FirebaseRepository()
    private val firebaseAuth = FirebaseAuth.getInstance()

    private var tvUserName: TextView? = null
    private var etName: EditText? = null
    private var etEmail: EditText? = null
    private var etHeight: EditText? = null
    private var etWeight: EditText? = null
    private var etAge: EditText? = null
    private var etTargetDistance: EditText? = null
    private var etTargetSteps: EditText? = null
    private var etTargetCalories: EditText? = null
    private var etTargetDuration: EditText? = null
    private var rgGender: RadioGroup? = null
    private var rbMale: RadioButton? = null
    private var rbFemale: RadioButton? = null
    private var btnSave: Button? = null
    private var btnLogout: Button? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return try {
            inflater.inflate(R.layout.fragment_account_settings, container, false)
        } catch (e: Exception) {
            Log.e("AccountSettingsFragment", "Error inflating layout: ${e.message}", e)
            Toast.makeText(context, "Error loading settings", Toast.LENGTH_SHORT).show()
            null
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        try {
            initializeViews(view)
            loadUserProfile()
            setupListeners()
        } catch (e: Exception) {
            Log.e("AccountSettingsFragment", "Error in onViewCreated: ${e.message}", e)
            Toast.makeText(context, "Error loading settings data: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun initializeViews(view: View) {
        try {
            tvUserName = view.findViewById(R.id.tv_user_name)
            etName = view.findViewById(R.id.et_name)
            etEmail = view.findViewById(R.id.et_email)
            etHeight = view.findViewById(R.id.et_height)
            etWeight = view.findViewById(R.id.et_weight)
            etAge = view.findViewById(R.id.et_age)
            etTargetDistance = view.findViewById(R.id.et_target_distance)
            etTargetSteps = view.findViewById(R.id.et_target_steps)
            etTargetCalories = view.findViewById(R.id.et_target_calories)
            etTargetDuration = view.findViewById(R.id.et_target_duration)
            rgGender = view.findViewById(R.id.rg_gender)
            rbMale = view.findViewById(R.id.rb_male)
            rbFemale = view.findViewById(R.id.rb_female)
            btnSave = view.findViewById(R.id.btn_save)
            btnLogout = view.findViewById(R.id.btn_logout)
            bindRunTargetSettings()
        } catch (e: Exception) {
            Log.e("AccountSettingsFragment", "Error initializing views: ${e.message}", e)
            throw e
        }
    }

    private fun bindRunTargetSettings() {
        val settings = RunTargetSettingsStore.load(requireContext())
        etTargetDistance?.setText(String.format(Locale.US, "%.2f", settings.distanceKm))
        etTargetSteps?.setText(settings.steps.toString())
        etTargetCalories?.setText(String.format(Locale.US, "%.0f", settings.calories))
        etTargetDuration?.setText(settings.durationMinutes.toString())
    }

    private fun loadUserProfile() {
        try {
            val userId = firebaseAuth.currentUser?.uid
            if (userId.isNullOrBlank()) {
                Toast.makeText(context, "Please login first", Toast.LENGTH_SHORT).show()
                return
            }

            lifecycleScope.launch {
                val userProfile = firebaseRepository.getUserProfile(userId)
                if (userProfile != null) {
                    LocalProfileStore.save(requireContext(), userProfile)
                    displayUserProfile(userProfile)
                } else {
                    val fallback = LocalProfileStore.load(requireContext(), userId) ?: UserProfile(
                        userId = userId,
                        name = firebaseAuth.currentUser?.displayName ?: "",
                        email = firebaseAuth.currentUser?.email ?: "",
                        height = 170,
                        weight = 70,
                        gender = "Male",
                        age = 25
                    )
                    displayUserProfile(fallback)
                }
            }
        } catch (e: Exception) {
            Log.e("AccountSettingsFragment", "Error loading user profile: ${e.message}", e)
        }
    }

    private fun displayUserProfile(profile: UserProfile) {
        try {
            tvUserName?.text = profile.name
            etName?.setText(profile.name)
            etEmail?.setText(profile.email)
            etHeight?.setText(profile.height.toString())
            etWeight?.setText(profile.weight.toString())
            etAge?.setText(profile.age.toString())

            if (profile.gender.equals("Male", ignoreCase = true)) {
                rbMale?.isChecked = true
            } else {
                rbFemale?.isChecked = true
            }
        } catch (e: Exception) {
            Log.e("AccountSettingsFragment", "Error displaying user profile: ${e.message}", e)
        }
    }

    private fun setupListeners() {
        try {
            btnSave?.setOnClickListener {
                saveUserProfile()
            }

            btnLogout?.setOnClickListener {
                logout()
            }
        } catch (e: Exception) {
            Log.e("AccountSettingsFragment", "Error setting up listeners: ${e.message}", e)
        }
    }

    private fun saveUserProfile() {
        try {
            val userId = firebaseAuth.currentUser?.uid
            if (userId.isNullOrBlank()) {
                Toast.makeText(context, "Please login first", Toast.LENGTH_SHORT).show()
                return
            }

            val name = etName?.text.toString().trim()
            val email = etEmail?.text.toString().trim()
            val height = etHeight?.text.toString().toIntOrNull() ?: 170
            val weight = etWeight?.text.toString().toIntOrNull() ?: 70
            val age = etAge?.text.toString().toIntOrNull() ?: 25
            val gender = if (rbMale?.isChecked == true) "Male" else "Female"
            val targetDistance = etTargetDistance?.text.toString().toDoubleOrNull() ?: 0.0
            val targetSteps = etTargetSteps?.text.toString().toIntOrNull() ?: 0
            val targetCalories = etTargetCalories?.text.toString().toDoubleOrNull() ?: 0.0
            val targetDuration = etTargetDuration?.text.toString().toIntOrNull() ?: 0

            if (name.isBlank() || email.isBlank()) {
                Toast.makeText(context, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                return
            }

            if (targetDistance <= 0.0 || targetSteps <= 0 || targetCalories <= 0.0 || targetDuration <= 0) {
                Toast.makeText(context, "Please enter valid target limits", Toast.LENGTH_SHORT).show()
                return
            }

            val userProfile = UserProfile(
                userId = userId,
                name = name,
                email = email,
                height = height,
                weight = weight,
                gender = gender,
                age = age
            )

            lifecycleScope.launch {
                LocalProfileStore.save(requireContext(), userProfile)
                RunTargetSettingsStore.save(
                    requireContext(),
                    RunTargetSettings(
                        distanceKm = targetDistance,
                        steps = targetSteps,
                        calories = targetCalories,
                        durationMinutes = targetDuration
                    )
                )

                val saved = firebaseRepository.saveUserProfile(userProfile)
                if (saved) {
                    Toast.makeText(context, "Profile updated successfully!", Toast.LENGTH_SHORT).show()
                    tvUserName?.text = name
                } else {
                    Toast.makeText(context, "Saved locally. Cloud sync failed.", Toast.LENGTH_LONG).show()
                    tvUserName?.text = name
                }
            }
        } catch (e: Exception) {
            Log.e("AccountSettingsFragment", "Error saving user profile: ${e.message}", e)
            Toast.makeText(context, "Error saving profile: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun logout() {
        try {
            firebaseAuth.signOut()
            val intent = android.content.Intent(requireContext(), LoginActivity::class.java).apply {
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
            Toast.makeText(context, "Logged out", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("AccountSettingsFragment", "Error during logout: ${e.message}", e)
            Toast.makeText(context, "Error logging out: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
