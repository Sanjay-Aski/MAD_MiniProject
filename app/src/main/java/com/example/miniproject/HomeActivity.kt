package com.example.miniproject

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.example.miniproject.ui.fragments.DashboardFragment
import com.example.miniproject.ui.fragments.MapFragment
import com.example.miniproject.ui.fragments.AnalyticsFragment
import com.example.miniproject.ui.fragments.AccountSettingsFragment

class HomeActivity : AppCompatActivity() {
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
        } catch (e: Exception) {
            Log.e("HomeActivity", "Error in onCreate: ${e.message}", e)
            Toast.makeText(this, "Error loading home: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupNavigation() {
        bottomNav?.setOnItemSelectedListener { item ->
            try {
                val fragment = when (item.itemId) {
                    R.id.nav_dashboard -> DashboardFragment()
                    R.id.nav_map -> MapFragment()
                    R.id.nav_analytics -> AnalyticsFragment()
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
