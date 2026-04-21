package com.example.miniproject.ui.fragments

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.miniproject.R
import com.example.miniproject.data.model.DiscoveringUser
import com.example.miniproject.data.model.Friend
import com.example.miniproject.data.model.FriendRequest
import com.example.miniproject.ui.adapter.FriendAdapter
import com.example.miniproject.ui.viewmodel.LeaderboardViewModel
import kotlinx.coroutines.launch

/**
 * Leaderboard fragment — Firebase-based friend discovery.
 *
 * Flow:
 *  1. "Get Connect" → enters 60-second discovery window (publishes presence to Firestore)
 *  2. Nearby discovering users appear in a list → tap "Add" to send a friend request
 *  3. Incoming requests shown as a banner → Accept / Decline
 *  4. Accepted friends ranked by steps then calories in the leaderboard
 *  5. Long-press a friend card → Remove friend (both sides)
 */
class LeaderboardFragment : Fragment(), FriendAdapter.Listener {

    companion object {
        private const val BT_PERMISSION_REQ = 101
    }

    private lateinit var viewModel: LeaderboardViewModel

    // ── Views ──────────────────────────────────────────────────────────────
    private lateinit var btnGetConnect:      Button
    private lateinit var progressBar:        ProgressBar
    private lateinit var tvMyRank:           TextView
    private lateinit var tvMySteps:          TextView
    private lateinit var tvMyCalories:       TextView
    private lateinit var tvFriendsCount:     TextView
    private lateinit var tvNoConnections:    TextView

    // Discovering users section
    private lateinit var cardDiscovering:    View
    private lateinit var rvDiscovering:      RecyclerView
    private lateinit var tvDiscoveringTitle: TextView

    // Incoming requests banner
    private lateinit var cardRequests:       View
    private lateinit var tvRequestCount:     TextView
    private lateinit var btnViewRequests:    Button

    // Friends leaderboard
    private lateinit var rvLeaderboard:      RecyclerView

    private var friendAdapter: FriendAdapter? = null

    // ── Lifecycle ───────────────────────────────────────────────────────────

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_leaderboard, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindViews(view)
        viewModel = ViewModelProvider(this)[LeaderboardViewModel::class.java]
        setupObservers()
        setupListeners()
    }

    // ── View binding ────────────────────────────────────────────────────────

    private fun bindViews(v: View) {
        btnGetConnect      = v.findViewById(R.id.btn_get_connect)
        progressBar        = v.findViewById(R.id.progress_bar)
        tvMyRank           = v.findViewById(R.id.tv_my_rank)
        tvMySteps          = v.findViewById(R.id.tv_my_steps)
        tvMyCalories       = v.findViewById(R.id.tv_my_calories)
        tvFriendsCount     = v.findViewById(R.id.tv_connected_count)
        tvNoConnections    = v.findViewById(R.id.tv_no_connections)
        cardDiscovering    = v.findViewById(R.id.card_discovering)
        rvDiscovering      = v.findViewById(R.id.rv_discovering)
        tvDiscoveringTitle = v.findViewById(R.id.tv_discovering_title)
        cardRequests       = v.findViewById(R.id.card_requests)
        tvRequestCount     = v.findViewById(R.id.tv_request_count)
        btnViewRequests    = v.findViewById(R.id.btn_view_requests)
        rvLeaderboard      = v.findViewById(R.id.rv_leaderboard)

        rvLeaderboard.layoutManager = LinearLayoutManager(requireContext())
        rvDiscovering.layoutManager = LinearLayoutManager(requireContext())
    }

    // ── Observers ───────────────────────────────────────────────────────────

    private fun setupObservers() {

        // Discovery mode UI
        lifecycleScope.launch {
            viewModel.isDiscovering.collect { discovering ->
                progressBar.visibility = if (discovering) View.VISIBLE else View.GONE
                btnGetConnect.text     = if (discovering) "⏳ Discovering..." else " Get Connect"
                btnGetConnect.isEnabled = !discovering
                cardDiscovering.visibility = if (discovering) View.VISIBLE else
                    if (viewModel.discoveringUsers.value.isEmpty()) View.GONE else View.VISIBLE
            }
        }

        // Discovering users list
        lifecycleScope.launch {
            viewModel.discoveringUsers.collect { users ->
                cardDiscovering.visibility =
                    if (users.isEmpty() && !viewModel.isDiscovering.value) View.GONE
                    else View.VISIBLE
                tvDiscoveringTitle.text =
                    if (users.isEmpty()) "🔍 Searching for users..."
                    else "📡 ${users.size} user(s) found nearby"
                showDiscoveringUsers(users)
            }
        }

        // Incoming friend requests badge
        lifecycleScope.launch {
            viewModel.incomingRequests.collect { requests ->
                cardRequests.visibility = if (requests.isEmpty()) View.GONE else View.VISIBLE
                tvRequestCount.text = "${requests.size} friend request(s) waiting"
            }
        }

        // Friends leaderboard
        lifecycleScope.launch {
            viewModel.friends.collect { _ ->
                refreshLeaderboard()
            }
        }

        // Toast messages
        lifecycleScope.launch {
            viewModel.statusMessage.collect { msg ->
                msg ?: return@collect
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                viewModel.clearStatusMessage()
            }
        }
    }

    // ── Listeners ───────────────────────────────────────────────────────────

    private fun setupListeners() {
        btnGetConnect.setOnClickListener { handleGetConnect() }

        btnViewRequests.setOnClickListener {
            showIncomingRequestsDialog()
        }
    }

    // ── Discovery ───────────────────────────────────────────────────────────

    private fun handleGetConnect() {
        viewModel.enterDiscoveryMode()
    }

    private fun showDiscoveringUsers(users: List<DiscoveringUser>) {
        // Inline adapter for the discovering list
        rvDiscovering.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            inner class VH(v: View) : RecyclerView.ViewHolder(v) {
                val name:     TextView = v.findViewById(R.id.tv_disc_name)
                val steps:    TextView = v.findViewById(R.id.tv_disc_steps)
                val btnAdd:   Button   = v.findViewById(R.id.btn_send_request)
            }

            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val v = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_discovering_user, parent, false)
                return VH(v)
            }

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val vh   = holder as VH
                val user = users[position]
                vh.name.text  = user.name
                vh.steps.text = "👟 ${user.totalSteps} steps  🔥 ${user.totalCalories.toInt()} kcal"

                val alreadySent = viewModel.requestSent.value == user.userId
                val alreadyFriend = viewModel.friends.value.any { it.friendUserId == user.userId }
                when {
                    alreadyFriend  -> { vh.btnAdd.text = " Friends"; vh.btnAdd.isEnabled = false }
                    alreadySent    -> { vh.btnAdd.text = "Sent";    vh.btnAdd.isEnabled = false }
                    else           -> {
                        vh.btnAdd.text = "Add Friend"
                        vh.btnAdd.isEnabled = true
                        vh.btnAdd.setOnClickListener { viewModel.sendFriendRequest(user) }
                    }
                }
            }

            override fun getItemCount() = users.size
        }
    }

    // ── Friend requests dialog ───────────────────────────────────────────────

    private fun showIncomingRequestsDialog() {
        val requests = viewModel.incomingRequests.value
        if (requests.isEmpty()) return

        val names = requests.map { it.fromName }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle("👥 Friend Requests")
            .setItems(names) { _, index ->
                showRequestActionDialog(requests[index])
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showRequestActionDialog(request: FriendRequest) {
        AlertDialog.Builder(requireContext())
            .setTitle("Friend request from ${request.fromName}")
            .setMessage("Do you want to connect with ${request.fromName}?\n\nYou'll appear in each other's leaderboard.")
            .setPositiveButton(" Accept") { _, _ -> viewModel.acceptFriendRequest(request) }
            .setNegativeButton(" Decline") { _, _ -> viewModel.rejectFriendRequest(request) }
            .setNeutralButton("Later", null)
            .show()
    }

    // ── Leaderboard ──────────────────────────────────────────────────────────

    private fun refreshLeaderboard() {
        val ranked = viewModel.rankedFriends()
        tvFriendsCount.text = "Friends: ${ranked.size}"
        tvMyRank.text       = "Your Rank: #${viewModel.myRank()}"
        tvMySteps.text      = "Steps: ${viewModel.mySteps.value}"
        tvMyCalories.text   = "Calories: ${viewModel.myCalories.value.toInt()} kcal"

        tvNoConnections.visibility = if (ranked.isEmpty()) View.VISIBLE else View.GONE
        rvLeaderboard.visibility   = if (ranked.isEmpty()) View.GONE    else View.VISIBLE

        friendAdapter = FriendAdapter(ranked, this)
        rvLeaderboard.adapter = friendAdapter
    }

    // ── FriendAdapter.Listener ────────────────────────────────────────────

    override fun onRemoveFriend(friend: Friend) {
        AlertDialog.Builder(requireContext())
            .setTitle("Remove Friend")
            .setMessage("Remove ${friend.friendName} from your friends list?\nThey will also be removed from your leaderboard.")
            .setPositiveButton("Remove") { _, _ -> viewModel.removeFriend(friend) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onFriendDetails(friend: Friend) {
        AlertDialog.Builder(requireContext())
            .setTitle("👤 ${friend.friendName}")
            .setMessage(
                "📧 ${friend.friendEmail}\n\n" +
                "👟 Total Steps:    ${friend.totalSteps}\n" +
                "🔥 Calories:       ${friend.totalCalories.toInt()} kcal\n" +
                "📏 Total Distance: ${"%.2f".format(friend.totalDistance)} km\n" +
                "🏃 Runs:           ${friend.totalRuns}"
            )
            .setNegativeButton("Remove Friend") { _, _ -> viewModel.removeFriend(friend) }
            .setNeutralButton("Close", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        viewModel.exitDiscoveryMode()
    }
}
