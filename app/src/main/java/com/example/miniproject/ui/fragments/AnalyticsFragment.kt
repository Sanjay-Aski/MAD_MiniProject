package com.example.miniproject.ui.fragments

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.example.miniproject.R
import com.example.miniproject.ui.viewmodel.AnalyticsViewModel
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import androidx.core.content.ContextCompat

class AnalyticsFragment : Fragment() {
    private val viewModel: AnalyticsViewModel by viewModels()
    private var barChartDistance: BarChart? = null
    private var lineChartSpeed: LineChart? = null
    private var barChartCalories: BarChart? = null
    private var tvBestRun: TextView? = null
    private var tvFastestPace: TextView? = null
    private var tvTotalCalories: TextView? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return try {
            inflater.inflate(R.layout.fragment_analytics, container, false)
        } catch (e: Exception) {
            Log.e("AnalyticsFragment", "Error inflating layout: ${e.message}", e)
            Toast.makeText(context, "Error loading analytics", Toast.LENGTH_SHORT).show()
            null
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        try {
            initializeCharts(view)
            loadAnalyticsData()
        } catch (e: Exception) {
            Log.e("AnalyticsFragment", "Error in onViewCreated: ${e.message}", e)
            Toast.makeText(context, "Error loading analytics data: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun initializeCharts(view: View) {
        try {
            barChartDistance = view.findViewById(R.id.bar_chart_distance)
            lineChartSpeed = view.findViewById(R.id.line_chart_speed)
            barChartCalories = view.findViewById(R.id.bar_chart_calories)
            tvBestRun = view.findViewById(R.id.tv_best_run_value)
            tvFastestPace = view.findViewById(R.id.tv_fastest_pace_value)
            tvTotalCalories = view.findViewById(R.id.tv_total_calories_value)

            setupChartAppearance()
        } catch (e: Exception) {
            Log.e("AnalyticsFragment", "Error initializing charts: ${e.message}", e)
            throw e
        }
    }

    private fun setupChartAppearance() {
        try {
            // Distance Chart
            barChartDistance?.apply {
                setBackgroundColor(resources.getColor(R.color.dark_bg, null))
                xAxis.textColor = resources.getColor(R.color.gray, null)
                axisLeft.textColor = resources.getColor(R.color.gray, null)
                axisRight.textColor = resources.getColor(R.color.gray, null)
                legend.textColor = resources.getColor(R.color.white, null)
                description.text = "Weekly Distance (km)"
                description.textColor = resources.getColor(R.color.white, null)
            }

            // Speed Chart
            lineChartSpeed?.apply {
                setBackgroundColor(resources.getColor(R.color.dark_bg, null))
                xAxis.textColor = resources.getColor(R.color.gray, null)
                axisLeft.textColor = resources.getColor(R.color.gray, null)
                axisRight.textColor = resources.getColor(R.color.gray, null)
                legend.textColor = resources.getColor(R.color.white, null)
                description.text = "Average Speed Trend"
                description.textColor = resources.getColor(R.color.white, null)
            }

            // Calories Chart
            barChartCalories?.apply {
                setBackgroundColor(resources.getColor(R.color.dark_bg, null))
                xAxis.textColor = resources.getColor(R.color.gray, null)
                axisLeft.textColor = resources.getColor(R.color.gray, null)
                axisRight.textColor = resources.getColor(R.color.gray, null)
                legend.textColor = resources.getColor(R.color.white, null)
                description.text = "Weekly Calories Burned"
                description.textColor = resources.getColor(R.color.white, null)
            }
        } catch (e: Exception) {
            Log.e("AnalyticsFragment", "Error in setupChartAppearance: ${e.message}", e)
        }
    }

    private fun loadAnalyticsData() {
        try {
            setupObservers()
            viewModel.loadAnalyticsData()
        } catch (e: Exception) {
            Log.e("AnalyticsFragment", "Error in loadAnalyticsData: ${e.message}", e)
        }
    }

    private fun setupObservers() {
        viewModel.weeklyDistances.observe(viewLifecycleOwner) { days ->
            val entries = days.mapIndexed { index, item -> BarEntry(index.toFloat(), item.second.toFloat()) }
            val dataSet = BarDataSet(entries, "Distance (km)").apply {
                color = ContextCompat.getColor(requireContext(), R.color.accent_blue)
                valueTextColor = ContextCompat.getColor(requireContext(), R.color.white)
            }
            barChartDistance?.data = BarData(dataSet)
            barChartDistance?.invalidate()
        }

        viewModel.weeklySpeeds.observe(viewLifecycleOwner) { days ->
            val entries = days.mapIndexed { index, item -> Entry(index.toFloat(), item.second.toFloat()) }
            val dataSet = LineDataSet(entries, "Avg Speed (km/h)").apply {
                color = ContextCompat.getColor(requireContext(), R.color.accent_green)
                setCircleColor(ContextCompat.getColor(requireContext(), R.color.accent_green))
                lineWidth = 2f
                valueTextColor = ContextCompat.getColor(requireContext(), R.color.white)
            }
            lineChartSpeed?.data = LineData(dataSet)
            lineChartSpeed?.invalidate()
        }

        viewModel.weeklyCalories.observe(viewLifecycleOwner) { days ->
            val entries = days.mapIndexed { index, item -> BarEntry(index.toFloat(), item.second.toFloat()) }
            val dataSet = BarDataSet(entries, "Calories (cal)").apply {
                color = ContextCompat.getColor(requireContext(), R.color.accent_orange)
                valueTextColor = ContextCompat.getColor(requireContext(), R.color.white)
            }
            barChartCalories?.data = BarData(dataSet)
            barChartCalories?.invalidate()
        }

        viewModel.personalBest.observe(viewLifecycleOwner) { best ->
            tvBestRun?.text = String.format("%.2f km", best)
        }

        viewModel.avgSpeed.observe(viewLifecycleOwner) { speed ->
            tvFastestPace?.text = String.format("%.1f km/h", speed)
        }

        viewModel.totalCalories.observe(viewLifecycleOwner) { calories ->
            tvTotalCalories?.text = String.format("%.0f cal", calories)
        }
    }
}
