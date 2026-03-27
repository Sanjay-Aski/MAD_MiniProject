package com.example.miniproject.ui.fragments

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.miniproject.R
import com.example.miniproject.data.LocalRunRepository
import com.example.miniproject.data.model.RunSession
import com.example.miniproject.ui.adapter.RecentRunsAdapter
import com.example.miniproject.ui.viewmodel.DashboardViewModel
import kotlinx.coroutines.launch

class HistoryFragment : Fragment() {
    private val viewModel: DashboardViewModel by viewModels()
    private var localRunRepository: LocalRunRepository? = null
    private var rvHistory: RecyclerView? = null
    private var tvEmpty: TextView? = null
    private var tvDeleteAllCloud: TextView? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_history, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        localRunRepository = LocalRunRepository(requireContext())
        rvHistory = view.findViewById(R.id.rv_history_runs)
        tvEmpty = view.findViewById(R.id.tv_history_empty)
        tvDeleteAllCloud = view.findViewById(R.id.tv_delete_all_cloud)

        rvHistory?.layoutManager = LinearLayoutManager(requireContext())

        viewModel.allRunsLiveData.observe(viewLifecycleOwner) { runs ->
            val sorted = runs.sortedByDescending { it.startTime }
            rvHistory?.adapter = RecentRunsAdapter(
                runs = sorted,
                onRunClick = { runId ->
                    parentFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, RunDetailsFragment.newInstance(runId))
                        .addToBackStack(null)
                        .commit()
                },
                onDeleteClick = { run ->
                    confirmDeleteRun(run)
                }
            )
            tvEmpty?.visibility = if (sorted.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) {
            if (!it.isNullOrBlank()) {
                Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
            }
        }

        viewModel.loadDashboardData()
        viewModel.startRealtimeUpdates()

        tvDeleteAllCloud?.setOnClickListener {
            confirmDeleteAllCloudData()
        }

        Log.d("HistoryFragment", "History realtime updates started")
    }

    private fun confirmDeleteRun(run: RunSession) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Run Data")
            .setMessage("Delete this run and its route points from local app database?")
            .setPositiveButton("Delete") { _, _ ->
                deleteRunFromLocal(run.runId)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteRunFromLocal(runId: String) {
        lifecycleScope.launch {
            val success = localRunRepository?.deleteRunWithGps(runId) == true
            if (success) {
                Toast.makeText(requireContext(), "Run deleted", Toast.LENGTH_SHORT).show()
                viewModel.loadDashboardData()
            } else {
                Toast.makeText(requireContext(), "Failed to delete run", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun confirmDeleteAllCloudData() {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete All Run Data")
            .setMessage("This will permanently delete all locally saved runs and route points.")
            .setPositiveButton("Delete All") { _, _ ->
                lifecycleScope.launch {
                    val success = localRunRepository?.deleteAllRunData() == true
                    if (success) {
                        Toast.makeText(requireContext(), "All run data deleted", Toast.LENGTH_SHORT).show()
                        viewModel.loadDashboardData()
                    } else {
                        Toast.makeText(requireContext(), "Failed to delete run data", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        viewModel.stopRealtimeUpdates()
    }
}
