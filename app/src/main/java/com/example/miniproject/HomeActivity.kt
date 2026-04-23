package com.example.miniproject

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.example.miniproject.ui.fragments.DashboardFragment
import com.example.miniproject.ui.fragments.HistoryFragment
import com.example.miniproject.ui.fragments.LeaderboardFragment
import com.example.miniproject.ui.fragments.AccountSettingsFragment
import com.example.miniproject.ui.fragments.RunDetailsFragment

class HomeActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_OPEN_RUN_ID = "extra_open_run_id"
    }

    private var bottomNav: BottomNavigationView? = null
    private lateinit var firebaseAuth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_home)

            firebaseAuth = FirebaseAuth.getInstance()
            bottomNav = findViewById(R.id.bottom_nav)
            
            if (bottomNav == null) {
                Toast.makeText(this, "Navigation view not found", Toast.LENGTH_SHORT).show()
                Log.e("HomeActivity", "bottom_nav view not found in layout")
                return
            }
            
            setupNavigation()

            // Load default fragment
            if (savedInstanceState == null) {
                loadFragment(DashboardFragment())
                bottomNav?.selectedItemId = R.id.nav_dashboard
            }

            handleOpenRunIntent(intent)
        } catch (e: Exception) {
            Log.e("HomeActivity", "Error in onCreate: ${e.message}", e)
            Toast.makeText(this, "Error loading home: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOpenRunIntent(intent)
    }

    private fun handleOpenRunIntent(intent: Intent?) {
        val runId = intent?.getStringExtra(EXTRA_OPEN_RUN_ID) ?: return
        if (runId.isBlank()) return

        bottomNav?.selectedItemId = R.id.nav_analytics
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, RunDetailsFragment.newInstance(runId))
            .addToBackStack(null)
            .commit()
    }

    private fun setupNavigation() {
        bottomNav?.setOnItemSelectedListener { item ->
            try {
                val fragment = when (item.itemId) {
                    R.id.nav_dashboard -> DashboardFragment()
                    R.id.nav_leaderboard -> LeaderboardFragment()
                    R.id.nav_analytics -> HistoryFragment()
                    R.id.nav_settings -> AccountSettingsFragment()
                    else -> DashboardFragment()
                }
                loadFragment(fragment)
                true
            } catch (e: Exception) {
                Log.e("HomeActivity", "Error loading fragment: ${e.message}", e)
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                false
            }
        }
    }

    private fun loadFragment(fragment: androidx.fragment.app.Fragment) {
        try {
            val transaction = supportFragmentManager.beginTransaction()
            transaction.replace(R.id.fragment_container, fragment)
            transaction.commit()
        } catch (e: Exception) {
            Log.e("HomeActivity", "Error loading fragment: ${e.message}", e)
        }
    }
}
