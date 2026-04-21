package com.example.miniproject.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.miniproject.R
import com.example.miniproject.data.model.Friend

/**
 * Legacy adapter kept for compatibility — delegates to FriendAdapter internals.
 * Uses the updated item_leaderboard_entry layout (Steps / Calories / Distance).
 */
class LeaderboardAdapter(
    private val friends: List<Friend>,
    private val listener: OnPeerActionListener? = null
) : RecyclerView.Adapter<LeaderboardAdapter.VH>() {

    interface OnPeerActionListener {
        fun onPeerDetails(peerId: String)
        fun onPeerRemove(peerId: String)
        fun onPeerMessage(peerId: String)
    }

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvRank:     TextView = itemView.findViewById(R.id.tv_rank)
        val tvName:     TextView = itemView.findViewById(R.id.tv_peer_name)
        val tvSteps:    TextView = itemView.findViewById(R.id.tv_peer_steps)
        val tvCalories: TextView = itemView.findViewById(R.id.tv_peer_calories)
        val tvDistance: TextView = itemView.findViewById(R.id.tv_peer_distance)
        val tvRuns:     TextView = itemView.findViewById(R.id.tv_peer_run_count)
        val btnDetails: Button   = itemView.findViewById(R.id.btn_peer_details)
        val btnRemove:  Button   = itemView.findViewById(R.id.btn_remove_peer)

        fun bind(friend: Friend) {
            tvRank.text     = when (friend.rank) {
                1 -> "🥇"; 2 -> "🥈"; 3 -> "🥉"
                else -> "#${friend.rank}"
            }
            tvName.text     = friend.friendName.ifBlank { friend.friendEmail }
            tvSteps.text    = "${friend.totalSteps} steps"
            tvCalories.text = "${friend.totalCalories.toInt()} kcal"
            tvDistance.text = "${"%.2f".format(friend.totalDistance)} km"
            tvRuns.text     = "${friend.totalRuns} runs"

            btnDetails.setOnClickListener { listener?.onPeerDetails(friend.friendUserId) }
            btnRemove.setOnClickListener  { listener?.onPeerRemove(friend.friendUserId) }
            itemView.setOnClickListener   { listener?.onPeerDetails(friend.friendUserId) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context)
            .inflate(R.layout.item_leaderboard_entry, parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) =
        holder.bind(friends[position])

    override fun getItemCount() = friends.size
}
