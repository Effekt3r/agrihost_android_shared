package za.co.mjrsolutions.shared.fcm_inbox.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import za.co.mjrsolutions.shared.fcm_inbox.FcmInbox
import za.co.mjrsolutions.shared.fcm_inbox.R
import za.co.mjrsolutions.shared.fcm_inbox.model.Message

class InboxActivity : AppCompatActivity() {

    private lateinit var recycler: RecyclerView
    private lateinit var progress: ProgressBar
    private lateinit var empty: TextView
    private lateinit var adapter: MessageAdapter
    private var listener: ListenerRegistration? = null
    private val readIds = mutableSetOf<String>()
    private val username get() = FcmInbox.config.usernameProvider()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_inbox)
        setSupportActionBar(findViewById<Toolbar>(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        recycler = findViewById(R.id.recycler)
        progress = findViewById(R.id.progress)
        empty = findViewById(R.id.emptyView)

        adapter = MessageAdapter(emptyList()) { msg ->
            startActivity(Intent(this, MessageDetailActivity::class.java).apply {
                putExtra(MessageDetailActivity.EXTRA_MESSAGE_ID, msg.id)
            })
        }
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        // Auto-open detail if launched from a notification.
        intent.getStringExtra(EXTRA_OPEN_MESSAGE_ID)?.takeIf { it.isNotEmpty() }?.let { id ->
            startActivity(Intent(this, MessageDetailActivity::class.java).apply {
                putExtra(MessageDetailActivity.EXTRA_MESSAGE_ID, id)
            })
        }
    }

    override fun onStart() {
        super.onStart()
        val u = username ?: run { showEmpty("Sign in to see messages"); return }
        loadReadIds(u)
        listener = FirebaseFirestore.getInstance().collection("messages")
            .whereArrayContains("recipientUsernames", u)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(100)
            .addSnapshotListener { snap, err ->
                progress.visibility = View.GONE
                if (err != null || snap == null) { showEmpty("Couldn't load"); return@addSnapshotListener }
                val list = snap.documents.mapNotNull { d ->
                    d.toObject(Message::class.java)?.also { it.id = d.id }
                }
                adapter.submit(list.map { it to !readIds.contains(it.id) })
                empty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            }
    }

    override fun onStop() {
        super.onStop()
        listener?.remove()
        listener = null
    }

    private fun loadReadIds(username: String) {
        FirebaseFirestore.getInstance().collectionGroup("reads")
            .whereEqualTo(FieldPath.documentId(), username)
            .get()
            .addOnSuccessListener { qs ->
                readIds.clear()
                qs.documents.forEach { d ->
                    d.reference.parent.parent?.id?.let(readIds::add)
                }
            }
    }

    private fun showEmpty(text: String) {
        progress.visibility = View.GONE
        empty.text = text
        empty.visibility = View.VISIBLE
    }

    companion object {
        const val EXTRA_OPEN_MESSAGE_ID = "openMessageId"
    }
}
