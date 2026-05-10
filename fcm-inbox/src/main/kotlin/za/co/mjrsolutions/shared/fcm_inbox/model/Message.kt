package za.co.mjrsolutions.shared.fcm_inbox.model

import com.google.firebase.firestore.PropertyName
import java.util.Date

data class Message(
    var id: String = "",
    var title: String = "",
    var body: String = "",
    var data: String = "",
    var sender: String = "",
    var timestamp: Long = 0L,
    var kind: String = "broadcast",                   // "broadcast" | "sync_event"
    var brand: String = "",
    var recipientUsernames: List<String> = emptyList(),
    var syncJson: String = "",
    var reportRef: Map<String, Any?> = emptyMap()
)
