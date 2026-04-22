package com.example.miniproject.data.model

/**
 * A pending friend request — stored in Firestore under
 * friend_requests/{toUserId}/incoming/{fromUserId}
 */
data class FriendRequest(
    val requestId: String = "",       // = fromUserId for easy lookup
    val fromUserId: String = "",
    val fromName: String = "",
    val fromEmail: String = "",
    val toUserId: String = "",
    val sentAt: Long = System.currentTimeMillis(),
    val status: String = STATUS_PENDING  // "pending" | "accepted" | "rejected"
) {
    companion object {
        const val STATUS_PENDING  = "pending"
        const val STATUS_ACCEPTED = "accepted"
        const val STATUS_REJECTED = "rejected"
    }
}
