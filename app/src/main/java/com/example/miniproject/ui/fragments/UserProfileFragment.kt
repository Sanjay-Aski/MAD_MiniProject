package com.example.miniproject.ui.fragments

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.example.miniproject.R
import com.example.miniproject.ui.viewmodel.UserProfileViewModel
import com.google.firebase.auth.FirebaseAuth
import java.util.Locale

/**
 * UserProfileFragment - Displays and edits user profile information
 * Shows: Name, Email, Height, Weight, Age, Gender, Stride Length, Calibrated Stride
 */
class UserProfileFragment : Fragment() {
    companion object {
        private const val TAG = "UserProfileFragment"
    }

    private val viewModel: UserProfileViewModel by viewModels()
    private val auth = FirebaseAuth.getInstance()

    // UI Components - Display Labels
    private var tvNameLabel: TextView? = null
    private var tvEmailLabel: TextView? = null
    private var tvHeightLabel: TextView? = null
    private var tvWeightLabel: TextView? = null
    private var tvAgeLabel: TextView? = null
    private var tvGenderLabel: TextView? = null
    private var tvStrideLabel: TextView? = null
    private var tvCalibratedStrideLabel: TextView? = null

    // UI Components - Edit Fields
    private var etName: EditText? = null
    private var etEmail: EditText? = null
    private var etHeight: EditText? = null
    private var etWeight: EditText? = null
    private var etAge: EditText? = null
    private var etGender: EditText? = null
    private var etTargetDistance: EditText? = null
    private var etTargetSteps: EditText? = null
    private var etTargetCalories: EditText? = null
    private var etTargetDuration: EditText? = null

    // UI Components - Info Display
    private var tvStepsToday: TextView? = null
    private var tvDistanceToday: TextView? = null
    private var tvCaloriesToday: TextView? = null
    private var tvAvgPaceToday: TextView? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_user_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        try {
            initializeViews(view)
            setupObservers()
            viewModel.loadRunTargetSettings()
            loadUserProfile()

            Log.d(TAG, "UserProfileFragment created")
        } catch (e: Exception) {
            Log.e(TAG, "Error in onViewCreated: ${e.message}", e)
            Toast.makeText(context, "Error loading profile", Toast.LENGTH_SHORT).show()
        }
    }

    private fun initializeViews(view: View) {
        // Display Labels
        tvNameLabel = view.findViewById(R.id.tv_name_display)
        tvEmailLabel = view.findViewById(R.id.tv_email_display)
        tvHeightLabel = view.findViewById(R.id.tv_height_display)
        tvWeightLabel = view.findViewById(R.id.tv_weight_display)
        tvAgeLabel = view.findViewById(R.id.tv_age_display)
        tvGenderLabel = view.findViewById(R.id.tv_gender_display)
        tvStrideLabel = view.findViewById(R.id.tv_stride_display)
        tvCalibratedStrideLabel = view.findViewById(R.id.tv_calibrated_stride_display)

        // Edit Fields
        etName = view.findViewById(R.id.et_user_name)
        etEmail = view.findViewById(R.id.et_user_email)
        etHeight = view.findViewById(R.id.et_user_height)
        etWeight = view.findViewById(R.id.et_user_weight)
        etAge = view.findViewById(R.id.et_user_age)
        etGender = view.findViewById(R.id.et_user_gender)
        etTargetDistance = view.findViewById(R.id.et_target_distance)
        etTargetSteps = view.findViewById(R.id.et_target_steps)
        etTargetCalories = view.findViewById(R.id.et_target_calories)
        etTargetDuration = view.findViewById(R.id.et_target_duration)

        // Info Display
        tvStepsToday = view.findViewById(R.id.tv_steps_today_profile)
        tvDistanceToday = view.findViewById(R.id.tv_distance_today_profile)
        tvCaloriesToday = view.findViewById(R.id.tv_calories_today_profile)
        tvAvgPaceToday = view.findViewById(R.id.tv_pace_today_profile)

        // Save button
        view.findViewById<View>(R.id.btn_save_profile)?.setOnClickListener {
            saveProfile()
        }
    }

    private fun setupObservers() {
        // Observe profile data
        viewModel.name.observe(viewLifecycleOwner) { name ->
            tvNameLabel?.text = name
            etName?.setText(name)
        }

        viewModel.email.observe(viewLifecycleOwner) { email ->
            tvEmailLabel?.text = email
            etEmail?.setText(email)
        }

        viewModel.height.observe(viewLifecycleOwner) { height ->
            tvHeightLabel?.text = "$height cm"
            etHeight?.setText(height.toString())
        }

        viewModel.weight.observe(viewLifecycleOwner) { weight ->
            tvWeightLabel?.text = "$weight kg"
            etWeight?.setText(weight.toString())
        }

        viewModel.age.observe(viewLifecycleOwner) { age ->
            tvAgeLabel?.text = "$age years"
            etAge?.setText(age.toString())
        }

        viewModel.gender.observe(viewLifecycleOwner) { gender ->
            tvGenderLabel?.text = gender
            etGender?.setText(gender)
        }

        // Observe stride lengths
        val stride = viewModel.calculateStride()
        tvStrideLabel?.text = String.format("%.2f m", stride)
        tvCalibratedStrideLabel?.text = "Not yet calibrated"

        viewModel.runDistanceTarget.observe(viewLifecycleOwner) { goal ->
            etTargetDistance?.setText(String.format(Locale.US, "%.2f", goal))
        }

        viewModel.runStepsTarget.observe(viewLifecycleOwner) { goal ->
            etTargetSteps?.setText(goal.toString())
        }

        viewModel.runCaloriesTarget.observe(viewLifecycleOwner) { goal ->
            etTargetCalories?.setText(String.format(Locale.US, "%.0f", goal))
        }

        viewModel.runDurationTargetMinutes.observe(viewLifecycleOwner) { goal ->
            etTargetDuration?.setText(goal.toString())
        }

        // Observe error messages
        viewModel.errorMessage.observe(viewLifecycleOwner) { error ->
            if (error != null) {
                Log.e(TAG, "ViewModel error: $error")
                Toast.makeText(context, "Error: $error", Toast.LENGTH_SHORT).show()
                viewModel.clearErrors()
            }
        }

        // Observe success messages
        viewModel.successMessage.observe(viewLifecycleOwner) { success ->
            if (success != null) {
                Log.d(TAG, "Success: $success")
                Toast.makeText(context, success, Toast.LENGTH_SHORT).show()
                viewModel.clearSuccessMessage()
            }
        }
    }

    private fun loadUserProfile() {
        try {
            val userId = auth.currentUser?.uid ?: ""

            if (userId.isNotEmpty()) {
                Log.d(TAG, "Loading profile for userId: $userId")
                viewModel.loadProfile(userId)
            } else {
                Log.e(TAG, "Cannot load profile: userId not found")
                Toast.makeText(context, "User not logged in", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading profile: ${e.message}", e)
            Toast.makeText(context, "Error loading profile: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveProfile() {
        try {
            val userId = auth.currentUser?.uid ?: ""

            if (userId.isEmpty()) {
                Log.e(TAG, "Cannot save profile: userId not found")
                Toast.makeText(context, "User not logged in", Toast.LENGTH_SHORT).show()
                return
            }

            val name = etName?.text?.toString()?.trim() ?: ""
            val email = etEmail?.text?.toString()?.trim() ?: ""
            val height = etHeight?.text?.toString()?.toIntOrNull() ?: 0
            val weight = etWeight?.text?.toString()?.toDoubleOrNull() ?: 0.0
            val age = etAge?.text?.toString()?.toIntOrNull() ?: 0
            val gender = etGender?.text?.toString()?.trim() ?: ""
            val targetDistanceKm = etTargetDistance?.text?.toString()?.toDoubleOrNull() ?: 0.0
            val targetSteps = etTargetSteps?.text?.toString()?.toIntOrNull() ?: 0
            val targetCalories = etTargetCalories?.text?.toString()?.toDoubleOrNull() ?: 0.0
            val targetDurationMin = etTargetDuration?.text?.toString()?.toIntOrNull() ?: 0

            if (name.isEmpty() || email.isEmpty()) {
                Toast.makeText(context, "Name and email are required", Toast.LENGTH_SHORT).show()
                return
            }

            if (height <= 0 || weight <= 0.0 || age <= 0 || gender.isBlank()) {
                Toast.makeText(context, "Enter valid height, weight, age and gender", Toast.LENGTH_SHORT).show()
                return
            }

            if (targetDistanceKm <= 0.0 || targetSteps <= 0 || targetCalories <= 0.0 || targetDurationMin <= 0) {
                Toast.makeText(context, "Set valid run targets for distance, steps, calories and duration", Toast.LENGTH_SHORT).show()
                return
            }

            Log.d(TAG, "Saving profile with userId: $userId")
            viewModel.setName(name)
            viewModel.setEmail(email)
            viewModel.setHeight(height)
            viewModel.setWeight(weight)
            viewModel.setAge(age)
            viewModel.setGender(gender)
            viewModel.setRunDistanceTarget(targetDistanceKm)
            viewModel.setRunStepsTarget(targetSteps)
            viewModel.setRunCaloriesTarget(targetCalories)
            viewModel.setRunDurationTargetMinutes(targetDurationMin)
            viewModel.saveRunTargetSettings()
            viewModel.saveProfile(userId)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving profile: ${e.message}", e)
            Toast.makeText(context, "Error saving profile: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
