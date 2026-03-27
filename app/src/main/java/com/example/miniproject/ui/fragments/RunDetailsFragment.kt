package com.example.miniproject.ui.fragments

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.miniproject.R
import com.example.miniproject.data.FirebaseRepository
import com.example.miniproject.data.LocalRunRepository
import com.example.miniproject.data.model.GPSPoint
import com.example.miniproject.data.model.RunSession
import com.example.miniproject.ui.viewmodel.DashboardViewModel
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * RunDetailsFragment - Displays detailed statistics for a specific run
 * Similar to the RunSense image showing:
 * - Run name/date
 * - Performance score
 * - Heart rate zones
 * - Pace consistency
 * - Detailed metrics
 */
class RunDetailsFragment : Fragment() {
    companion object {
        private const val TAG = "RunDetailsFragment"
        private const val ARG_RUN_ID = "run_id"

        fun newInstance(runId: String): RunDetailsFragment {
            return RunDetailsFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_RUN_ID, runId)
                }
            }
        }
    }

    private val viewModel: DashboardViewModel by viewModels()
    private var localRunRepository: LocalRunRepository? = null
    private val firebaseRepository = FirebaseRepository()

    // Args
    private var runId: String = ""

    // UI Components - Header
    private var tvRunTitle: TextView? = null
    private var tvRunDate: TextView? = null
    private var tvDeleteRunDetail: TextView? = null
    private var tvPerformanceScore: TextView? = null
    private var tvRunCompareLabel: TextView? = null
    private var tvRunCompareValue: TextView? = null

    // UI Components - Main Metrics
    private var tvDistance: TextView? = null
    private var tvDuration: TextView? = null
    private var tvAvgSpeed: TextView? = null
    private var tvMaxSpeed: TextView? = null
    private var tvPace: TextView? = null

    // UI Components - Detailed Stats
    private var tvSteps: TextView? = null
    private var tvCalories: TextView? = null
    private var tvStrideLength: TextView? = null
    private var tvCadence: TextView? = null
    private var tvHeartRateZone: TextView? = null
    private var tvZoneDistribution: TextView? = null
    private var tvZoneTime: TextView? = null
    private var tvRecovery: TextView? = null

    // UI Components - Charts
    private var paceChart: BarChart? = null
    private var calorieProgressBar: ProgressBar? = null
    private var routePoints: List<GPSPoint> = emptyList()

    private var currentRun: RunSession? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_run_details, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        try {
            localRunRepository = LocalRunRepository(requireContext())
            initializeViews(view)
            setupObservers()
            loadRunDetails()

            Log.d(TAG, "RunDetailsFragment created with runId: $runId")
        } catch (e: Exception) {
            Log.e(TAG, "Error in onViewCreated: ${e.message}", e)
            Toast.makeText(context, "Error loading run details", Toast.LENGTH_SHORT).show()
        }
    }

    private fun initializeViews(view: View) {
        // Header
        tvRunTitle = view.findViewById(R.id.tv_run_title)
        tvRunDate = view.findViewById(R.id.tv_run_date)
        tvDeleteRunDetail = view.findViewById(R.id.tv_delete_run_detail)
        tvPerformanceScore = view.findViewById(R.id.tv_performance_score)
        tvRunCompareLabel = view.findViewById(R.id.tv_run_compare_label)
        tvRunCompareValue = view.findViewById(R.id.tv_run_compare_value)

        // Main Metrics
        tvDistance = view.findViewById(R.id.tv_distance_detail)
        tvDuration = view.findViewById(R.id.tv_duration_detail)
        tvAvgSpeed = view.findViewById(R.id.tv_avg_speed_detail)
        tvMaxSpeed = view.findViewById(R.id.tv_max_speed_detail)
        tvPace = view.findViewById(R.id.tv_pace_detail)

        // Detailed Stats
        tvSteps = view.findViewById(R.id.tv_steps_detail)
        tvCalories = view.findViewById(R.id.tv_calories_detail)
        tvStrideLength = view.findViewById(R.id.tv_stride_length)
        tvCadence = view.findViewById(R.id.tv_cadence)
        tvHeartRateZone = view.findViewById(R.id.tv_heart_rate_zone_detail)
        tvZoneDistribution = view.findViewById(R.id.tv_zone_distribution)
        tvZoneTime = view.findViewById(R.id.tv_zone_time)
        tvRecovery = view.findViewById(R.id.tv_recovery_time)

        // Charts
        paceChart = view.findViewById(R.id.pace_consistency_chart)
        calorieProgressBar = view.findViewById(R.id.calorie_progress)

        tvDeleteRunDetail?.setOnClickListener {
            if (runId.isNotBlank()) {
                confirmDeleteCurrentRun()
            }
        }
    }

    private fun setupObservers() {
        viewModel.errorMessage.observe(viewLifecycleOwner) { error ->
            if (error != null) {
                Toast.makeText(context, "Error: $error", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadRunDetails() {
        // Get runId from arguments
        runId = arguments?.getString(ARG_RUN_ID) ?: ""
        
        if (runId.isEmpty()) {
            Toast.makeText(context, "Error: Run ID not found", Toast.LENGTH_SHORT).show()
            return
        }

        Log.d(TAG, "Loading run details for runId: $runId")

        lifecycleScope.launch {
            try {
                currentRun = localRunRepository?.getRunSessionById(runId)
                if (currentRun == null) {
                    currentRun = firebaseRepository.getRunSessionById(runId)
                    if (currentRun != null) {
                        localRunRepository?.saveRunSessionWithRoute(currentRun!!, emptyList())
                        Log.d(TAG, "Run loaded from Firebase and cached locally for runId=$runId")
                    } else {
                        Log.e(TAG, "Run with ID $runId not found locally or in Firebase")
                        Toast.makeText(context, "Run not found on device or cloud", Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                }

                routePoints = localRunRepository?.getRunRoutePoints(runId) ?: emptyList()
                if (routePoints.isEmpty()) {
                    routePoints = firebaseRepository.getRunRoutePoints(runId)
                    if (routePoints.isNotEmpty() && currentRun != null) {
                        localRunRepository?.saveRunSessionWithRoute(currentRun!!, routePoints)
                        Log.d(TAG, "Route points loaded from Firebase and cached locally for runId=$runId")
                    }
                }
                Log.d(TAG, "Fetched ${routePoints.size} GPS points for run $runId")
                
                displayRunDetails(currentRun!!)
                if (routePoints.isNotEmpty()) {
                    Log.d(TAG, "Rendering map with ${routePoints.size} points")
                    renderRouteMap(routePoints)
                } else {
                    Log.d(TAG, "No GPS points available for map, showing empty map")
                    renderRouteMap(emptyList())
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading run details: ${e.message}", e)
                Toast.makeText(context, "Failed to load run details: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun displayRunDetails(run: RunSession) {
        try {
            val effectiveAvgSpeed = if (run.avgSpeed > 0.0) {
                run.avgSpeed
            } else if (run.duration > 0 && run.distance > 0.0) {
                run.distance / (run.duration / 3600.0)
            } else {
                0.0
            }

            // Header Information
            tvRunTitle?.text = run.title.ifBlank { "Running Session" }
            tvRunDate?.text = SimpleDateFormat("EEEE, MMMM d, yyyy HH:mm", Locale.getDefault())
                .format(java.util.Date(run.startTime))

            // Performance Score (0-100) - based on distance, speed, consistency
            val performanceScore = calculatePerformanceScore(run)
            tvPerformanceScore?.text = performanceScore.toInt().toString()
            tvRunCompareLabel?.text = "Run Efficiency"
            tvRunCompareValue?.text = String.format("%.0f%%", (performanceScore / 100.0) * 100)
            tvPerformanceScore?.setTextColor(
                when {
                    performanceScore >= 80 -> ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark)
                    performanceScore >= 60 -> ContextCompat.getColor(requireContext(), android.R.color.holo_blue_dark)
                    else -> ContextCompat.getColor(requireContext(), android.R.color.holo_orange_dark)
                }
            )

            // Main Metrics
            tvDistance?.text = String.format("%.2f km", run.distance)
            tvDuration?.text = formatDuration(run.duration)
            tvAvgSpeed?.text = String.format("%.1f km/h", effectiveAvgSpeed)
            tvMaxSpeed?.text = String.format("%.1f km/h", run.maxSpeed)
            tvPace?.text = calculatePace(effectiveAvgSpeed)

            // Detailed Stats
            tvSteps?.text = "${run.steps} steps"
            tvCalories?.text = String.format("%.0f cal", run.calories)

            // Calculate stride length (distance * 1000 / steps)
            val strideLength = if (run.steps > 0) {
                (run.distance * 1000) / run.steps
            } else {
                0.75 // default
            }
            tvStrideLength?.text = String.format("%.2f m", strideLength)

            // Calculate cadence (steps per minute)
            val cadence = if (run.duration > 0) {
                (run.steps * 60) / run.duration
            } else {
                0
            }
            tvCadence?.text = "$cadence steps/min"

            // Heart Rate Zone (simulated - would come from wearable)
            val heartRateZone = calculateHeartRateZone(run.copy(avgSpeed = effectiveAvgSpeed))
            tvHeartRateZone?.text = heartRateZone
            val easyPercent = (100 - ((effectiveAvgSpeed / 15.0) * 100).toInt()).coerceIn(20, 90)
            tvZoneDistribution?.text = "$easyPercent% Easy"
            tvZoneTime?.text = formatDuration((run.duration * easyPercent) / 100)

            // Recovery Time (estimated)
            val recoveryTime = estimateRecoveryTime(run)
            tvRecovery?.text = recoveryTime

            // Setup Charts
            setupPaceConsistencyChart(run.copy(avgSpeed = effectiveAvgSpeed))
            setupCalorieProgress(run)

            // Set calorie progress bar
            val calorieGoal = 500.0
            val progress = ((run.calories / calorieGoal) * 100).toInt().coerceIn(0, 100)
            calorieProgressBar?.progress = progress

            Log.d(TAG, "Run details displayed: ${run.distance}km, ${run.calories}cal, ${run.steps} steps")
        } catch (e: Exception) {
            Log.e(TAG, "Error displaying run details: ${e.message}", e)
        }
    }

    private fun renderRouteMap(points: List<GPSPoint>) {
        if (!isAdded) {
            Log.d(TAG, "Fragment not added, skipping map render")
            return
        }
        
        try {
            val container = childFragmentManager.findFragmentById(R.id.run_route_map_container)
            val mapFragment = if (container is MapFragment) {
                Log.d(TAG, "MapFragment already exists")
                container
            } else {
                Log.d(TAG, "Creating new MapFragment")
                val created = MapFragment()
                childFragmentManager.beginTransaction()
                    .replace(R.id.run_route_map_container, created)
                    .commit()
                created
            }

            childFragmentManager.executePendingTransactions()
            val active = childFragmentManager.findFragmentById(R.id.run_route_map_container) as? MapFragment
            if (active != null) {
                if (points.isNotEmpty()) {
                    Log.d(TAG, "Updating map with ${points.size} GPS points")
                    active.updateGPSPath(points)
                } else {
                    Log.d(TAG, "No GPS points to render on map")
                    active.updateGPSPath(emptyList())
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error rendering route map: ${e.message}", e)
        }
    }

    private fun calculatePerformanceScore(run: RunSession): Double {
        // Performance Score = (distance/5km * 20) + (speed/10km/h * 20) + (consistency * 20) + (zones * 20) + (recovery * 20)
        var score = 0.0

        // Distance component
        score += (run.distance / 5.0) * 20.0

        // Speed component
        score += (run.avgSpeed / 10.0) * 20.0

        // Consistency component (speed variance)
        val consistency = if (run.maxSpeed > 0) {
            (run.avgSpeed / run.maxSpeed) * 20.0
        } else {
            0.0
        }
        score += consistency

        // Recovery component
        if (run.duration > 1200) { // > 20 minutes = good endurance
            score += 20.0
        } else {
            score += (run.duration / 1200.0) * 20.0
        }

        return score.coerceIn(0.0, 100.0)
    }

    private fun calculatePace(speedKmh: Double): String {
        return if (speedKmh > 0) {
            val paceMinutes = 60.0 / speedKmh
            val minutes = paceMinutes.toInt()
            val seconds = ((paceMinutes - minutes) * 60).toInt()
            String.format("%d:%02d /km", minutes, seconds)
        } else {
            "0:00"
        }
    }

    private fun formatDuration(seconds: Long): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return when {
            hours > 0 -> String.format("%dh %02dm %02ds", hours, minutes, secs)
            minutes > 0 -> String.format("%dm %02ds", minutes, secs)
            else -> String.format("%ds", secs)
        }
    }

    private fun calculateHeartRateZone(run: RunSession): String {
        // Simulate HR zones based on run intensity
        // Zone 1: Very Light (50-60%)
        // Zone 2: Light (60-70%)
        // Zone 3: Moderate (70-80%)
        // Zone 4: Hard (80-90%)
        // Zone 5: Maximum (90-100%)

        val intensity = (run.avgSpeed / 15.0).coerceIn(0.0, 1.0) // Assume 15 km/h is max intensity
        val percentage = (intensity * 100).toInt()

        return when {
            percentage < 60 -> "Zone 1: Very Light ($percentage%)"
            percentage < 70 -> "Zone 2: Light ($percentage%)"
            percentage < 80 -> "Zone 3: Moderate ($percentage%)"
            percentage < 90 -> "Zone 4: Hard ($percentage%)"
            else -> "Zone 5: Maximum ($percentage%)"
        }
    }

    private fun estimateRecoveryTime(run: RunSession): String {
        // Recovery time based on intensity and duration
        val intensity = run.avgSpeed / 15.0 // Max intensity at 15 km/h
        val baseDurationHours = run.duration / 3600.0
        val recoveryHours = (intensity * baseDurationHours) * 2 // 2x the intensity duration

        return when {
            recoveryHours < 1 -> "< 1 hour"
            recoveryHours < 2 -> "1-2 hours"
            recoveryHours < 4 -> "2-4 hours"
            else -> "> 4 hours"
        }
    }

    private fun setupPaceConsistencyChart(run: RunSession) {
        try {
            paceChart?.let { chart ->
                // Validation check
                if (run.avgSpeed <= 0) {
                    Log.w(TAG, "Invalid run speed: ${run.avgSpeed}, showing empty chart")
                    chart.data = null
                    chart.setNoDataText("No pace data available")
                    chart.invalidate()
                    return@let
                }
                
                // Create pace variations data (simulated from average speed)
                val entries = mutableListOf<BarEntry>()
                val labels = mutableListOf<String>()

                // Calculate pace data with realistic variations
                val avgPace = 60.0 / run.avgSpeed // minutes per km
                Log.d(TAG, "Average pace: $avgPace min/km from speed ${run.avgSpeed} km/h")
                
                repeat(5) { i ->
                    val variation = (avgPace * (0.8 + (i * 0.04))) // Slight variations: -20% to +20%
                    entries.add(BarEntry(i.toFloat(), variation.toFloat()))
                    labels.add("${(i + 1) * 20}%")
                }

                if (entries.isNotEmpty()) {
                    val dataSet = BarDataSet(entries, "Pace (min/km)").apply {
                        color = ContextCompat.getColor(requireContext(), R.color.accent_blue)
                        valueTextSize = 10f
                    }

                    val data = BarData(dataSet)
                    chart.data = data
                    chart.xAxis.valueFormatter = IndexAxisValueFormatter(labels)
                    chart.description.text = "Pace Consistency Throughout Run"
                    chart.description.isEnabled = true
                    chart.invalidate()
                    Log.d(TAG, "Pace consistency chart setup complete with ${entries.size} entries")
                } else {
                    chart.setNoDataText("No pace data available")
                    chart.invalidate()
                }
            } ?: Log.w(TAG, "paceChart is null")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up pace chart: ${e.message}", e)
            paceChart?.setNoDataText("Error loading chart")
            paceChart?.invalidate()
        }
    }

    private fun setupCalorieProgress(run: RunSession) {
        // Calorie progress already handled by progress bar
        // Could add more detailed breakdown here if needed
    }

    private fun confirmDeleteCurrentRun() {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Run Data")
            .setMessage("Delete this run and route points from local app database?")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    val success = localRunRepository?.deleteRunWithGps(runId) == true
                    if (success) {
                        Toast.makeText(requireContext(), "Run deleted", Toast.LENGTH_SHORT).show()
                        parentFragmentManager.popBackStack()
                    } else {
                        Toast.makeText(requireContext(), "Could not delete run", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

}
