package za.co.mjrsolutions.shared.fcm_inbox.ui

import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.Spannable
import android.text.SpannableString
import android.text.TextWatcher
import android.text.style.BackgroundColorSpan
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.google.firebase.firestore.FirebaseFirestore
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import za.co.mjrsolutions.shared.fcm_inbox.FcmInbox
import za.co.mjrsolutions.shared.fcm_inbox.R
import za.co.mjrsolutions.shared.fcm_inbox.model.Message

class MessageDetailActivity : AppCompatActivity() {

    private lateinit var jsonView: TextView
    private lateinit var scroll: ScrollView
    private lateinit var counter: TextView
    private lateinit var btnPrev: ImageButton
    private lateinit var btnNext: ImageButton
    private lateinit var searchInput: EditText
    private lateinit var parseBanner: TextView

    private var fullText: String = ""
    private var matches: List<Int> = emptyList()
    private var matchIdx: Int = 0
    private var currentSearch: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_message_detail)
        setSupportActionBar(findViewById<Toolbar>(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        jsonView = findViewById(R.id.jsonView)
        scroll = findViewById(R.id.scroll)
        counter = findViewById(R.id.matchCounter)
        btnPrev = findViewById(R.id.btnPrev)
        btnNext = findViewById(R.id.btnNext)
        searchInput = findViewById(R.id.searchInput)
        parseBanner = findViewById(R.id.parseBanner)

        val id = intent.getStringExtra(EXTRA_MESSAGE_ID) ?: run { finish(); return }

        FirebaseFirestore.getInstance().collection("messages").document(id).get()
            .addOnSuccessListener { snap ->
                val msg = snap.toObject(Message::class.java) ?: run { finish(); return@addOnSuccessListener }
                title = msg.title
                renderBody(msg)
                markRead(id)
            }
            .addOnFailureListener { finish() }

        btnPrev.setOnClickListener { stepMatch(-1) }
        btnNext.setOnClickListener { stepMatch(+1) }
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                currentSearch = s?.toString().orEmpty()
                recomputeMatches()
                applyHighlights()
            }
        })
    }

    private fun renderBody(msg: Message) {
        val raw = msg.syncJson.ifEmpty { msg.body }
        fullText = try {
            when {
                raw.trimStart().startsWith("{") -> JSONObject(raw).toString(2)
                raw.trimStart().startsWith("[") -> JSONArray(raw).toString(2)
                else -> { showParseBanner(); raw }
            }
        } catch (_: JSONException) {
            showParseBanner(); raw
        }
        jsonView.text = fullText
    }

    private fun showParseBanner() {
        parseBanner.text = "Couldn't parse as JSON; showing raw body."
        parseBanner.visibility = View.VISIBLE
    }

    private fun recomputeMatches() {
        if (currentSearch.isBlank()) {
            matches = emptyList(); matchIdx = 0
            counter.text = "0/0"
            return
        }
        val q = currentSearch.lowercase()
        val src = fullText.lowercase()
        val out = mutableListOf<Int>()
        var i = src.indexOf(q)
        while (i >= 0) {
            out.add(i)
            i = src.indexOf(q, i + 1)
        }
        matches = out
        matchIdx = if (out.isEmpty()) 0 else 0
        updateCounter()
    }

    private fun stepMatch(direction: Int) {
        if (matches.isEmpty()) return
        matchIdx = ((matchIdx + direction) % matches.size + matches.size) % matches.size
        applyHighlights()
        updateCounter()
    }

    private fun updateCounter() {
        counter.text = if (matches.isEmpty()) "0/0" else "${matchIdx + 1}/${matches.size}"
    }

    private fun applyHighlights() {
        val span = SpannableString(fullText)
        val len = currentSearch.length
        matches.forEachIndexed { i, start ->
            val color = if (i == matchIdx) Color.YELLOW else Color.parseColor("#FFF59D")
            span.setSpan(BackgroundColorSpan(color), start, start + len, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        jsonView.text = span
        if (matches.isNotEmpty()) {
            // Approximate scroll: line index → y. Simple fallback: scroll by relative ratio of char index.
            jsonView.post {
                val layout = jsonView.layout ?: return@post
                val line = layout.getLineForOffset(matches[matchIdx])
                val y = layout.getLineTop(line)
                scroll.smoothScrollTo(0, y)
            }
        }
    }

    private fun markRead(id: String) {
        val u = FcmInbox.config.usernameProvider() ?: return
        // Persist the username as a field (the doc id is the username too): InboxActivity's
        // read-state query filters the "reads" collection group by this field, which a
        // collection-group documentId() filter cannot do.
        FirebaseFirestore.getInstance().collection("messages").document(id)
            .collection("reads").document(u)
            .set(mapOf("readAt" to System.currentTimeMillis(), "username" to u))
    }

    companion object {
        const val EXTRA_MESSAGE_ID = "messageId"
    }
}
