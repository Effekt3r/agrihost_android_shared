package za.co.mjrsolutions.shared.fcm_inbox.ui

import android.content.Intent
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import za.co.mjrsolutions.shared.fcm_inbox.AppAdminGate
import za.co.mjrsolutions.shared.fcm_inbox.FcmInbox
import za.co.mjrsolutions.shared.fcm_inbox.R

/**
 * Drop-in helper for activities that have a Toolbar/menu and want to surface the inbox bell.
 * Call [bind] from onCreate, and forward onCreateOptionsMenu/onOptionsItemSelected.
 */
class BellIconBinding(
    private val activity: AppCompatActivity
) {
    private var listener: ListenerRegistration? = null
    private var unreadCount: Int = 0
    private var isAdmin: Boolean = false
    private var menuRef: Menu? = null

    fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuRef = menu
        if (isAdmin) {
            activity.menuInflater.inflate(R.menu.menu_bell, menu)
            updateBadgeTitle()
        }
        return true
    }

    fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_inbox) {
            activity.startActivity(Intent(activity, InboxActivity::class.java))
            return true
        }
        return false
    }

    fun bind() {
        val username = FcmInbox.config.usernameProvider()
        if (username.isNullOrBlank()) return
        // Async admin check
        Thread {
            isAdmin = AppAdminGate().isAdminForCurrentBrandBlocking()
            activity.runOnUiThread { activity.invalidateOptionsMenu() }
        }.start()
        // Live unread badge — single query: messages where user is a recipient.
        listener = FirebaseFirestore.getInstance().collection("messages")
            .whereArrayContains("recipientUsernames", username)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(100)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) return@addSnapshotListener
                unreadCount = snap.size()
                updateBadgeTitle()
            }
    }

    fun unbind() {
        listener?.remove(); listener = null
    }

    private fun updateBadgeTitle() {
        val item = menuRef?.findItem(R.id.action_inbox) ?: return
        item.title = if (unreadCount > 0) "Inbox ($unreadCount)" else "Inbox"
    }
}
