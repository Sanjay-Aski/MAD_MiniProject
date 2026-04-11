package com.example.miniproject.ui.adapter

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.miniproject.R
import com.example.miniproject.data.model.RunSession
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * RecentRunsAdapter - Displays list of recent runs in RecyclerView
 * Each item is clickable to navigate to run details
 */
class RecentRunsAdapter(
    private val runs: List<RunSession>,
    private val onRunClick: (String) -> Unit,
    private val onDeleteClick: ((RunSession) -> Unit)? = null
) : RecyclerView.Adapter<RecentRunsAdapter.RunViewHolder>() {

    companion object {
        private const val TAG = "RecentRunsAdapter"
    }

    inner class RunViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvRunType: TextView = itemView.findViewById(R.id.tv_run_type)
        private val tvRunDate: TextView = itemView.findViewById(R.id.tv_run_list_date)
        private val tvDistance: TextView = itemView.findViewById(R.id.tv_run_distance)
        private val tvDuration: TextView = itemView.findViewById(R.id.tv_run_duration)
        private val tvPace: TextView = itemView.findViewById(R.id.tv_run_pace)
        private val tvCalories: TextView = itemView.findViewById(R.id.tv_run_calories)
        private val tvDeleteRun: TextView = itemView.findViewById(R.id.tv_delete_run)

        fun bind(run: RunSession) {
            try {
                // Run Type (inferred from distance and speed)
                val runType = if (run.title.isNotBlank()) {
                    run.title
                } else {
                    when {
                        run.avgSpeed > 12 -> "Sprint Run"
                        run.avgSpeed > 10 -> "Fast Run"
                        run.avgSpeed > 8 -> "Moderate Run"
                        run.avgSpeed > 6 -> "Jogging"
                        else -> "Walking"
                    }
                }
                tvRunType.text = runType

                // Date
                val dateFormat = SimpleDateFormat("MMM d, yyyy • HH:mm", Locale.getDefault())
                tvRunDate.text = dateFormat.format(Date(run.startTime))

                // Distance
                tvDistance.text = String.format("%.2f km", run.distance)

                // Duration
                tvDuration.text = formatDuration(run.duration)

                // Pace
                tvPace.text = calculatePace(run.avgSpeed)

                // Calories
                tvCalories.text = String.format("%.0f cal", run.calories)

                // Click listener
                itemView.setOnClickListener {
                    Log.d(TAG, "Run clicked: ${run.runId}")
                    onRunClick(run.runId)
                }

                tvDeleteRun.setOnClickListener {
                    Log.d(TAG, "Delete clicked for run: ${run.runId}")
                    onDeleteClick?.invoke(run)
                }

                Log.d(TAG, "Bound run: $runType - ${run.distance}km")
            } catch (e: Exception) {
                Log.e(TAG, "Error binding run: ${e.message}", e)
            }
        }

        private fun formatDuration(seconds: Long): String {
            val hours = seconds / 3600
            val minutes = (seconds % 3600) / 60
            return when {
                hours > 0 -> String.format("%dh %dm", hours, minutes)
                else -> String.format("%dm", minutes)
            }
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
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RunViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_recent_run, parent, false)
        return RunViewHolder(view)
    }

    override fun onBindViewHolder(holder: RunViewHolder, position: Int) {
        holder.bind(runs[position])
    }

    override fun getItemCount(): Int = runs.size
}
