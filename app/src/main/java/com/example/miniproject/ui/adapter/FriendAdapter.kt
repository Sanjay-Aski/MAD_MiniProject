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
 * Adapter for the ranked friends leaderboard list.
 */
class FriendAdapter(
    private val friends: List<Friend>,
    private val listener: Listener
) : RecyclerView.Adapter<FriendAdapter.FriendVH>() {

    interface Listener {
        fun onRemoveFriend(friend: Friend)
        fun onFriendDetails(friend: Friend)
    }

    inner class FriendVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
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

            btnDetails.setOnClickListener { listener.onFriendDetails(friend) }
            btnRemove.setOnClickListener  { listener.onRemoveFriend(friend) }
            itemView.setOnClickListener   { listener.onFriendDetails(friend) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        FriendVH(LayoutInflater.from(parent.context)
            .inflate(R.layout.item_leaderboard_entry, parent, false))

    override fun onBindViewHolder(holder: FriendVH, position: Int) =
        holder.bind(friends[position])

    override fun getItemCount() = friends.size
}
