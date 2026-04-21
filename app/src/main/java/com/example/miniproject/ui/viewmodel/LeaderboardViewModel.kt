package com.example.miniproject.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.miniproject.data.FirebaseRepository
import com.example.miniproject.data.LocalRunRepository
import com.example.miniproject.data.model.DiscoveringUser
import com.example.miniproject.data.model.Friend
import com.example.miniproject.data.model.FriendRequest
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the Leaderboard / Friends screen.
 *
 * Discovery flow:
 *  1. User taps "Get Connect" → [enterDiscoveryMode]
 *  2. We publish our presence to Firestore for 60 s
 *  3. Real-time listener shows other discovering users
 *  4. User taps a name → [sendFriendRequest]
 *  5. Other user sees incoming request → [acceptFriendRequest] / [rejectFriendRequest]
 *  6. Both sides' friends lists update via real-time listener
 *  7. Leaderboard sorted by totalSteps then totalCalories
 */
class LeaderboardViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = FirebaseRepository()
    private val localRunRepo = LocalRunRepository(application)
    private val auth = FirebaseAuth.getInstance()

    // ── Discovery ──────────────────────────────────────────────────────────
    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering: StateFlow<Boolean> = _isDiscovering

    private val _discoveringUsers = MutableStateFlow<List<DiscoveringUser>>(emptyList())
    val discoveringUsers: StateFlow<List<DiscoveringUser>> = _discoveringUsers

    private val _requestSent = MutableStateFlow<String?>(null)  // userId we just sent to
    val requestSent: StateFlow<String?> = _requestSent

    // ── Incoming requests ──────────────────────────────────────────────────
    private val _incomingRequests = MutableStateFlow<List<FriendRequest>>(emptyList())
    val incomingRequests: StateFlow<List<FriendRequest>> = _incomingRequests

    // ── Friends / Leaderboard ──────────────────────────────────────────────
    private val _friends = MutableStateFlow<List<Friend>>(emptyList())
    val friends: StateFlow<List<Friend>> = _friends

    private val _mySteps     = MutableStateFlow(0L)
    private val _myCalories  = MutableStateFlow(0.0)
    private val _myDistance  = MutableStateFlow(0.0)
    private val _myRuns      = MutableStateFlow(0)
    val mySteps:    StateFlow<Long>   = _mySteps
    val myCalories: StateFlow<Double> = _myCalories
    val myDistance: StateFlow<Double> = _myDistance

    // ── Status messages ────────────────────────────────────────────────────
    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage

    // Firestore listeners — cleaned up in onCleared()
    private var discoveryListener:  ListenerRegistration? = null
    private var requestsListener:   ListenerRegistration? = null
    private var friendsListener:    ListenerRegistration? = null
    private var discoveryTimer:     Job? = null

    init {
        startRequestsListener()
        startFriendsListener()
        loadMyTotalStats()
    }

    private fun loadMyTotalStats() {
        viewModelScope.launch {
            try {
                val allRuns = localRunRepo.getRunSessions()
                
                // Filter for current calendar month
                val calendar = java.util.Calendar.getInstance()
                val currentMonth = calendar.get(java.util.Calendar.MONTH)
                val currentYear = calendar.get(java.util.Calendar.YEAR)
                
                val currentMonthRuns = allRuns.filter { run ->
                    calendar.timeInMillis = run.startTime
                    calendar.get(java.util.Calendar.MONTH) == currentMonth &&
                    calendar.get(java.util.Calendar.YEAR) == currentYear
                }

                val totalSteps = currentMonthRuns.sumOf { it.steps.toLong() }
                val totalCalories = currentMonthRuns.sumOf { it.calories }
                val totalDistance = currentMonthRuns.sumOf { it.distance }
                val totalRuns = currentMonthRuns.size

                setMyStats(
                    steps = totalSteps,
                    calories = totalCalories,
                    distance = totalDistance,
                    runs = totalRuns
                )
                // Sync the total across to Firebase just to be sure it's up-to-date
                repo.updateMyStats(totalSteps, totalCalories, totalDistance, totalRuns)
            } catch (e: Exception) {
                // Log silently
            }
        }
    }

    // ── Discovery ───────────────────────────────────────────────────────────

    fun enterDiscoveryMode() {
        if (_isDiscovering.value) return
        _isDiscovering.value = true
        _discoveringUsers.value = emptyList()

        viewModelScope.launch {
            // Publish our presence
            val name  = auth.currentUser?.displayName ?: "Runner"
            val email = auth.currentUser?.email ?: ""
            repo.publishDiscoveryPresence(
                name           = name,
                email          = email,
                totalSteps     = _mySteps.value,
                totalCalories  = _myCalories.value,
                durationMs     = 60_000L
            )

            // Start real-time listener for other discovering users
            discoveryListener?.remove()
            discoveryListener = repo.observeDiscoveringUsers { list ->
                _discoveringUsers.value = list
            }

            // Auto-stop after 60 s
            discoveryTimer?.cancel()
            discoveryTimer = viewModelScope.launch {
                delay(60_000L)
                exitDiscoveryMode()
            }
        }
    }

    fun exitDiscoveryMode() {
        if (!_isDiscovering.value) return
        _isDiscovering.value = false
        discoveryTimer?.cancel()
        discoveryListener?.remove()
        discoveryListener = null
        _discoveringUsers.value = emptyList()
        viewModelScope.launch { repo.removeDiscoveryPresence() }
    }

    // ── Friend requests ─────────────────────────────────────────────────────

    fun sendFriendRequest(toUser: DiscoveringUser) {
        viewModelScope.launch {
            val ok = repo.sendFriendRequest(toUser.userId, toUser.name)
            if (ok) {
                _requestSent.value = toUser.userId
                _statusMessage.value = "Request sent to ${toUser.name} "
            } else {
                _statusMessage.value = "Failed to send request "
            }
        }
    }

    fun acceptFriendRequest(request: FriendRequest) {
        viewModelScope.launch {
            val ok = repo.acceptFriendRequest(
                fromUserId = request.fromUserId,
                fromName   = request.fromName,
                fromEmail  = request.fromEmail
            )
            _statusMessage.value = if (ok) "You're now friends with ${request.fromName}! 🎉"
            else "Could not accept request "
        }
    }

    fun rejectFriendRequest(request: FriendRequest) {
        viewModelScope.launch {
            repo.rejectFriendRequest(request.fromUserId)
            _statusMessage.value = "Request from ${request.fromName} declined"
        }
    }

    // ── Friends list ────────────────────────────────────────────────────────

    fun removeFriend(friend: Friend) {
        viewModelScope.launch {
            val ok = repo.removeFriend(friend.friendUserId)
            _statusMessage.value = if (ok) "${friend.friendName} removed from friends"
            else "Could not remove friend "
        }
    }

    /** Sorted leaderboard: steps desc */
    fun rankedFriends(): List<Friend> =
        _friends.value
            .sortedByDescending { it.totalSteps }
            .mapIndexed { i, f -> f.copy(rank = i + 1) }

    /** My rank among friends (1-based) */
    fun myRank(): Int {
        val mySteps = _mySteps.value
        val above = _friends.value.count { f ->
            f.totalSteps > mySteps
        }
        return above + 1
    }

    // ── My stats ────────────────────────────────────────────────────────────

    /** Called by the fragment after loading run history */
    fun setMyStats(steps: Long, calories: Double, distance: Double, runs: Int) {
        _mySteps.value    = steps
        _myCalories.value = calories
        _myDistance.value = distance
        _myRuns.value     = runs
    }

    fun clearStatusMessage() { _statusMessage.value = null }

    // ── Private helpers ─────────────────────────────────────────────────────

    private fun startRequestsListener() {
        requestsListener?.remove()
        requestsListener = repo.observeIncomingRequests { list ->
            _incomingRequests.value = list
        }
    }

    private fun startFriendsListener() {
        friendsListener?.remove()
        friendsListener = repo.observeFriends { list ->
            _friends.value = list
        }
    }

    override fun onCleared() {
        super.onCleared()
        discoveryTimer?.cancel()
        discoveryListener?.remove()
        requestsListener?.remove()
        friendsListener?.remove()
        if (_isDiscovering.value) {
            viewModelScope.launch { repo.removeDiscoveryPresence() }
        }
    }
}
