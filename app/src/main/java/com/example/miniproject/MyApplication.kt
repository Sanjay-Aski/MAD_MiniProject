package com.example.miniproject

import android.app.Application
import android.util.Log
import org.osmdroid.config.Configuration

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Initialize OSMDroid with proper configuration for free tile loading
        try {
            Configuration.getInstance().apply {
                // Set user agent for OSMDroid requests
                userAgentValue = packageName
                
                // Configure tile cache paths for offline and background loading
                osmdroidBasePath = cacheDir
                osmdroidTileCache = cacheDir
                
                // Set cache size for map tiles (500 tiles for good coverage)
                cacheMapTileCount = 500
            }
            Log.d("MyApplication", "OSMDroid initialized successfully - free tiles configured")
        } catch (e: Exception) {
            Log.e("MyApplication", "Error initializing OSMDroid: ${e.message}", e)
        }
    }
}
