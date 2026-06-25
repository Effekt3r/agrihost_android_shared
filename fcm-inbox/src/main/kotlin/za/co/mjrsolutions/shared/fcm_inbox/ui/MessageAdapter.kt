package za.co.mjrsolutions.shared.fcm_inbox.ui

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import za.co.mjrsolutions.shared.fcm_inbox.R
import za.co.mjrsolutions.shared.fcm_inbox.model.Message

internal class MessageAdapter(
    private var items: List<Pair<Message, Boolean>>,            // (message, isUnread)
    private val onClick: (Message) -> Unit
) : RecyclerView.Adapter<MessageAdapter.VH>() {

    fun submit(newItems: List<Pair<Message, Boolean>>) {
        items = newItems
        notifyDataSetChanged()
    }

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val title: TextView = v.findViewById(R.id.title)
        val body: TextView = v.findViewById(R.id.body)
        val timestamp: TextView = v.findViewById(R.id.timestamp)
        val unreadDot: View = v.findViewById(R.id.unreadDot)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.fcminbox_item_message, parent, false)
        return VH(v)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, position: Int) {
        val (m, unread) = items[position]
        h.title.text = m.title
        h.body.text = m.body.ifEmpty { "(tap to view details)" }
        h.timestamp.text = DateUtils.getRelativeTimeSpanString(m.timestamp).toString()
        h.unreadDot.visibility = if (unread) View.VISIBLE else View.INVISIBLE
        h.itemView.setOnClickListener { onClick(m) }
    }
}
