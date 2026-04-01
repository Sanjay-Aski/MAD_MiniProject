package com.example.miniproject.data

import android.content.Context
import android.util.Log
import com.example.miniproject.data.model.GPSPoint
import com.example.miniproject.data.model.RunSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class LocalRunRepository(context: Context) {
    companion object {
        private const val TAG = "LocalRunRepository"
        private const val PREFS_NAME = "local_run_db"
        private const val RUNS_KEY = "runs"
        private const val ROUTES_KEY = "routes"
    }

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val lock = Any()

    suspend fun saveRunSessionWithRoute(runSession: RunSession, points: List<GPSPoint>): Boolean {
        return withContext(Dispatchers.IO) {
            synchronized(lock) {
                try {
                    val sanitizedPoints = points
                        .filter { it.latitude != 0.0 || it.longitude != 0.0 }
                        .sortedBy { it.timestamp }

                    val runs = readRunsMutable()
                    val normalizedRun = runSession.copy(pathPointsCount = sanitizedPoints.size)
                    runs.removeAll { it.runId == normalizedRun.runId }
                    runs.add(normalizedRun)

                    val routes = readRoutesJsonObject()
                    routes.put(normalizedRun.runId, pointsToJsonArray(normalizedRun.runId, sanitizedPoints))

                    prefs.edit()
                        .putString(RUNS_KEY, runsToJsonArray(runs).toString())
                        .putString(ROUTES_KEY, routes.toString())
                        .apply()

                    true
                } catch (e: Exception) {
                    Log.e(TAG, "saveRunSessionWithRoute failed: ${e.message}", e)
                    false
                }
            }
        }
    }

    suspend fun getRunSessions(): List<RunSession> {
        return withContext(Dispatchers.IO) {
            synchronized(lock) {
                readRunsMutable().sortedByDescending { it.startTime }
            }
        }
    }

    suspend fun getRunSessionById(runId: String): RunSession? {
        return withContext(Dispatchers.IO) {
            synchronized(lock) {
                readRunsMutable().firstOrNull { it.runId == runId }
            }
        }
    }

    suspend fun getRunRoutePoints(runId: String): List<GPSPoint> {
        return withContext(Dispatchers.IO) {
            synchronized(lock) {
                val routes = readRoutesJsonObject()
                val pointsJson = routes.optJSONArray(runId) ?: JSONArray()
                val points = mutableListOf<GPSPoint>()

                for (i in 0 until pointsJson.length()) {
                    val item = pointsJson.optJSONObject(i) ?: continue
                    points.add(
                        GPSPoint(
                            runId = runId,
                            latitude = item.optDouble("latitude", 0.0),
                            longitude = item.optDouble("longitude", 0.0),
                            timestamp = item.optLong("timestamp", System.currentTimeMillis()),
                            speed = item.optDouble("speed", 0.0).toFloat(),
                            altitude = item.optDouble("altitude", 0.0),
                            accuracy = item.optDouble("accuracy", 0.0).toFloat()
                        )
                    )
                }

                points.sortedBy { it.timestamp }
            }
        }
    }

    suspend fun deleteRunWithGps(runId: String): Boolean {
        return withContext(Dispatchers.IO) {
            synchronized(lock) {
                try {
                    val runs = readRunsMutable().filterNot { it.runId == runId }
                    val routes = readRoutesJsonObject()
                    routes.remove(runId)

                    prefs.edit()
                        .putString(RUNS_KEY, runsToJsonArray(runs).toString())
                        .putString(ROUTES_KEY, routes.toString())
                        .apply()

                    true
                } catch (e: Exception) {
                    Log.e(TAG, "deleteRunWithGps failed: ${e.message}", e)
                    false
                }
            }
        }
    }

    suspend fun deleteAllRunData(): Boolean {
        return withContext(Dispatchers.IO) {
            synchronized(lock) {
                try {
                    prefs.edit()
                        .remove(RUNS_KEY)
                        .remove(ROUTES_KEY)
                        .apply()
                    true
                } catch (e: Exception) {
                    Log.e(TAG, "deleteAllRunData failed: ${e.message}", e)
                    false
                }
            }
        }
    }

    private fun readRunsMutable(): MutableList<RunSession> {
        val runsJson = prefs.getString(RUNS_KEY, "[]") ?: "[]"
        val array = JSONArray(runsJson)
        val runs = mutableListOf<RunSession>()

        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            runs.add(
                RunSession(
                    runId = obj.optString("runId", ""),
                    userId = obj.optString("userId", ""),
                    startTime = obj.optLong("startTime", 0L),
                    endTime = obj.optLong("endTime", 0L),
                    duration = obj.optLong("duration", 0L),
                    distance = obj.optDouble("distance", 0.0),
                    avgSpeed = obj.optDouble("avgSpeed", 0.0),
                    maxSpeed = obj.optDouble("maxSpeed", 0.0),
                    steps = obj.optInt("steps", 0),
                    calories = obj.optDouble("calories", 0.0),
                    pathPointsCount = obj.optInt("pathPointsCount", 0),
                    title = obj.optString("title", "Running Session"),
                    notes = obj.optString("notes", ""),
                    createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                )
            )
        }

        return runs
    }

    private fun runsToJsonArray(runs: List<RunSession>): JSONArray {
        val array = JSONArray()
        for (run in runs) {
            array.put(
                JSONObject().apply {
                    put("runId", run.runId)
                    put("userId", run.userId)
                    put("startTime", run.startTime)
                    put("endTime", run.endTime)
                    put("duration", run.duration)
                    put("distance", run.distance)
                    put("avgSpeed", run.avgSpeed)
                    put("maxSpeed", run.maxSpeed)
                    put("steps", run.steps)
                    put("calories", run.calories)
                    put("pathPointsCount", run.pathPointsCount)
                    put("title", run.title)
                    put("notes", run.notes)
                    put("createdAt", run.createdAt)
                }
            )
        }
        return array
    }

    private fun readRoutesJsonObject(): JSONObject {
        val routesJson = prefs.getString(ROUTES_KEY, "{}") ?: "{}"
        return JSONObject(routesJson)
    }

    private fun pointsToJsonArray(runId: String, points: List<GPSPoint>): JSONArray {
        val array = JSONArray()
        for (point in points) {
            array.put(
                JSONObject().apply {
                    put("runId", runId)
                    put("latitude", point.latitude)
                    put("longitude", point.longitude)
                    put("timestamp", point.timestamp)
                    put("speed", point.speed.toDouble())
                    put("altitude", point.altitude)
                    put("accuracy", point.accuracy.toDouble())
                }
            )
        }
        return array
    }
}
