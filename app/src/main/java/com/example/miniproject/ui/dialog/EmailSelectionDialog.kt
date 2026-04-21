package com.example.miniproject.ui.dialog

import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import android.widget.ListView
import android.widget.SimpleAdapter
import com.example.miniproject.R

/**
 * Dialog for selecting which email to login with
 * Displays available emails from user's linked accounts
 */
class EmailSelectionDialog(
    private val context: Context,
    private val emails: List<String>,
    private val onEmailSelected: (String) -> Unit,
    private val onCancel: () -> Unit
) {
    fun show() {
        if (emails.isEmpty()) {
            onCancel()
            return
        }

        // If only one email, auto-select it
        if (emails.size == 1) {
            onEmailSelected(emails[0])
            return
        }

        // Multiple emails - show selection dialog
        val items = emails.map { email ->
            mapOf(
                "email" to email,
                "icon" to "📧"
            )
        }

        val listView = ListView(context).apply {
            adapter = SimpleAdapter(
                context,
                items,
                android.R.layout.simple_list_item_1,
                arrayOf("email"),
                intArrayOf(android.R.id.text1)
            )

            setOnItemClickListener { _, _, position, _ ->
                onEmailSelected(emails[position])
            }
        }

        AlertDialog.Builder(context)
            .setTitle("Select Email to Login")
            .setMessage("Choose which email account you want to login with:")
            .setView(listView)
            .setNegativeButton("Cancel") { _, _ -> onCancel() }
            .setCancelable(true)
            .setOnCancelListener { onCancel() }
            .show()
    }
}

/**
 * Callback interface for email selection
 */
interface EmailSelectionCallback {
    fun onEmailSelected(email: String)
    fun onSelectionCancelled()
}
