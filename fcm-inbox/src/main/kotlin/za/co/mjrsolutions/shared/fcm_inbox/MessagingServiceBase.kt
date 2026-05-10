package za.co.mjrsolutions.shared.fcm_inbox

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import za.co.mjrsolutions.shared.fcm_inbox.ui.InboxActivity

abstract class MessagingServiceBase : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        FcmTokenRegistrar(applicationContext).update(token)
    }

    override fun onMessageReceived(msg: RemoteMessage) {
        val title = msg.notification?.title ?: msg.data["title"] ?: "New message"
        val body = msg.notification?.body ?: msg.data["body"] ?: ""
        val messageId = msg.data["messageId"] ?: ""

        ensureChannel()

        val intent = Intent(this, InboxActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            putExtra(InboxActivity.EXTRA_OPEN_MESSAGE_ID, messageId)
        }
        val pi = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_bell)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()

        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(messageId.hashCode(), notif)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(NotificationChannel(
            CHANNEL_ID, "Inbox", NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Sync notifications and broadcasts"
        })
    }

    companion object {
        const val CHANNEL_ID = "fcm_inbox_default"
    }
}
