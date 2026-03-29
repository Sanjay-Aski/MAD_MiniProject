package com.example.miniproject.ui.fragments

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.miniproject.R
import com.example.miniproject.RunTrackingActivity
import com.example.miniproject.data.model.RunSession
import com.example.miniproject.ui.adapter.RecentRunsAdapter
import com.example.miniproject.ui.viewmodel.DashboardViewModel
import com.example.miniproject.util.RunTargetSettingsStore
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import java.util.Locale

class DashboardFragment : Fragment() {
    companion object {
        private const val TAG = "DashboardFragment"
    }

    // ViewModel
    private val viewModel: DashboardViewModel by viewModels()

    private var tvTotalDistance: TextView? = null
    private var tvTotalSteps: TextView? = null
    private var tvTotalCalories: TextView? = null
    private var tvDailyGoalProgress: TextView? = null
    private var tvDailyGoalPercentage: TextView? = null
    private var progressBar: ProgressBar? = null
    private var tvAvgSpeed: TextView? = null
    private var tvMaxSpeed: TextView? = null
    private var tvTotalDuration: TextView? = null
    private var rvRecentRuns: androidx.recyclerview.widget.RecyclerView? = null
    private var chartWeeklyTrends: LineChart? = null
    private var btnStartRun: Button? = null
    private var tvAnalyticsTotalRuns: TextView? = null
    private var tvAnalyticsBestDistance: TextView? = null
    private var tvAnalyticsFastestSpeed: TextView? = null
    private var tvAnalyticsAvgPace: TextView? = null
    private var stepGoalTarget: Int = 10000

    private lateinit var recentRunsAdapter: RecentRunsAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_dashboard, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadStepGoalTarget()
        initializeViews(view)
        setupRecycler()
        setupActions()
        setupObservers()
        loadDashboardData()
        viewModel.startRealtimeUpdates()
    }

    override fun onResume() {
        super.onResume()
        val previous = stepGoalTarget
        loadStepGoalTarget()
        if (previous != stepGoalTarget) {
            updateStepGoalUI(viewModel.todaySteps.value ?: 0)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        viewModel.stopRealtimeUpdates()
    }

    private fun initializeViews(view: View) {
        tvTotalDistance = view.findViewById(R.id.tv_total_distance)
        tvTotalSteps = view.findViewById(R.id.tv_total_steps)
        tvTotalCalories = view.findViewById(R.id.tv_total_calories)
        tvDailyGoalProgress = view.findViewById(R.id.tv_daily_goal_progress)
        tvDailyGoalPercentage = view.findViewById(R.id.tv_daily_goal_percentage)
        progressBar = view.findViewById(R.id.progress_daily_goal)
        tvAvgSpeed = view.findViewById(R.id.tv_avg_speed)
        tvMaxSpeed = view.findViewById(R.id.tv_max_speed)
        tvTotalDuration = view.findViewById(R.id.tv_total_duration)
        rvRecentRuns = view.findViewById(R.id.rv_recent_runs)
        chartWeeklyTrends = view.findViewById(R.id.chart_weekly_trends)
        btnStartRun = view.findViewById(R.id.btn_start_run)
        tvAnalyticsTotalRuns = view.findViewById(R.id.tv_analytics_total_runs)
        tvAnalyticsBestDistance = view.findViewById(R.id.tv_analytics_best_distance)
        tvAnalyticsFastestSpeed = view.findViewById(R.id.tv_analytics_fastest_speed)
        tvAnalyticsAvgPace = view.findViewById(R.id.tv_analytics_avg_pace)
        progressBar?.max = stepGoalTarget
        updateStepGoalUI(viewModel.todaySteps.value ?: 0)
    }

    private fun loadStepGoalTarget() {
        stepGoalTarget = RunTargetSettingsStore
            .load(requireContext())
            .steps
            .coerceAtLeast(1)
    }

    private fun updateStepGoalUI(todaySteps: Int) {
        val safeSteps = todaySteps.coerceAtLeast(0)
        val progressPercent = ((safeSteps.toDouble() / stepGoalTarget.toDouble()) * 100.0)
            .toInt()
            .coerceIn(0, 999)

        progressBar?.max = stepGoalTarget
        progressBar?.progress = safeSteps.coerceAtMost(stepGoalTarget)
        tvDailyGoalPercentage?.text = "${progressPercent}%"
        tvDailyGoalProgress?.text = String.format(
            Locale.getDefault(),
            "%,d / %,d steps",
            safeSteps,
            stepGoalTarget
        )
    }

    private fun setupRecycler() {
        recentRunsAdapter = RecentRunsAdapter(runs = emptyList(), onRunClick = { runId ->
            // Navigate to RunDetailsFragment with runId
            Log.d(TAG, "Run clicked: $runId, navigating to details")
            navigateToRunDetails(runId)
        })
        rvRecentRuns?.layoutManager = LinearLayoutManager(requireContext())
        rvRecentRuns?.adapter = recentRunsAdapter
    }

    private fun navigateToRunDetails(runId: String) {
        try {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, RunDetailsFragment.newInstance(runId))
                .addToBackStack(null)
                .commit()
        } catch (e: Exception) {
            Log.e(TAG, "Error navigating to run details: ${e.message}", e)
            Toast.makeText(context, "Error opening run details", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupActions() {
        btnStartRun?.setOnClickListener {
            startActivity(Intent(requireContext(), RunTrackingActivity::class.java))
        }
    }

    private fun setupObservers() {
        // Observe today's stats
        viewModel.todayDistance.observe(viewLifecycleOwner) { distance ->
            tvTotalDistance?.text = viewModel.getFormattedDistance(distance)
        }

        viewModel.todaySteps.observe(viewLifecycleOwner) { steps ->
            tvTotalSteps?.text = String.format(Locale.getDefault(), "%,d", steps)
            updateStepGoalUI(steps)
        }

        viewModel.todayCalories.observe(viewLifecycleOwner) { calories ->
            tvTotalCalories?.text = viewModel.getFormattedCalories(calories)
        }

        viewModel.todayDuration.observe(viewLifecycleOwner) { duration ->
            tvTotalDuration?.text = viewModel.getFormattedTime(duration)
        }

        viewModel.todayAvgSpeed.observe(viewLifecycleOwner) { speed ->
            tvAvgSpeed?.text = String.format(Locale.getDefault(), "%.1f km/h", speed)
        }

        viewModel.todayMaxSpeed.observe(viewLifecycleOwner) { speed ->
            tvMaxSpeed?.text = String.format(Locale.getDefault(), "%.1f km/h", speed)
        }

        viewModel.goalProgress.observe(viewLifecycleOwner) {
            updateStepGoalUI(viewModel.todaySteps.value ?: 0)
        }

        viewModel.recentRuns.observe(viewLifecycleOwner) { runs ->
            // Update adapter with new list
            recentRunsAdapter = RecentRunsAdapter(runs = runs, onRunClick = { runId ->
                Log.d(TAG, "Run clicked: $runId, navigating to details")
                navigateToRunDetails(runId)
            })
            rvRecentRuns?.adapter = recentRunsAdapter
            
            // Update chart when runs change
            renderWeeklyChart(runs)
            updateAnalyticsSection(runs)
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            // TODO: Show/hide loading indicator if available
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { error ->
            if (error != null) {
                Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
                viewModel.clearErrors()
            }
        }
    }

    private fun loadDashboardData() {
        viewModel.loadDashboardData()
    }

    private fun renderWeeklyChart(runs: List<RunSession>) {
        val labels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        val distanceByDay = DoubleArray(7)

        runs.forEach { run ->
            val day = java.util.Calendar.getInstance().apply { timeInMillis = run.startTime }
            val index = ((day.get(java.util.Calendar.DAY_OF_WEEK) + 5) % 7)
            distanceByDay[index] += run.distance
        }

        val entries = labels.indices.map { Entry(it.toFloat(), distanceByDay[it].toFloat()) }
        val dataSet = LineDataSet(entries, "Distance").apply {
            color = ContextCompat.getColor(requireContext(), R.color.accent_green)
            lineWidth = 2.5f
            setCircleColor(ContextCompat.getColor(requireContext(), R.color.accent_blue))
            circleRadius = 3f
            setDrawValues(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER
            setDrawFilled(true)
            fillColor = ContextCompat.getColor(requireContext(), R.color.accent_teal)
            fillAlpha = 60
        }

        chartWeeklyTrends?.apply {
            data = LineData(dataSet)
            description.isEnabled = false
            legend.isEnabled = false
            axisRight.isEnabled = false
            xAxis.valueFormatter = IndexAxisValueFormatter(labels)
            xAxis.granularity = 1f
            xAxis.setDrawGridLines(false)
            axisLeft.setDrawGridLines(true)
            axisLeft.axisMinimum = 0f
            setTouchEnabled(false)
            invalidate()
        }
    }

    private fun updateAnalyticsSection(runs: List<RunSession>) {
        if (runs.isEmpty()) {
            tvAnalyticsTotalRuns?.text = "Total Runs: 0"
            tvAnalyticsBestDistance?.text = "Best Distance: 0.00 km"
            tvAnalyticsFastestSpeed?.text = "Fastest Speed: 0.0 km/h"
            tvAnalyticsAvgPace?.text = "Average Pace: 0:00 /km"
            return
        }

        val totalRuns = runs.size
        val bestDistance = runs.maxOf { it.distance }
        val fastestSpeed = runs.maxOf { it.maxSpeed }
        val avgSpeed = runs.map { if (it.avgSpeed > 0) it.avgSpeed else if (it.duration > 0 && it.distance > 0) it.distance / (it.duration / 3600.0) else 0.0 }
            .average()
        val pace = if (avgSpeed > 0) {
            val paceMinutes = 60.0 / avgSpeed
            val min = paceMinutes.toInt()
            val sec = ((paceMinutes - min) * 60).toInt()
            String.format("%d:%02d /km", min, sec)
        } else {
            "0:00 /km"
        }

        tvAnalyticsTotalRuns?.text = "Total Runs: $totalRuns"
        tvAnalyticsBestDistance?.text = String.format(Locale.getDefault(), "Best Distance: %.2f km", bestDistance)
        tvAnalyticsFastestSpeed?.text = String.format(Locale.getDefault(), "Fastest Speed: %.1f km/h", fastestSpeed)
        tvAnalyticsAvgPace?.text = "Average Pace: $pace"
    }

}
