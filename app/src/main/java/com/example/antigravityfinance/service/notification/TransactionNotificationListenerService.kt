package com.example.antigravityfinance.service.notification

import android.app.Notification
import android.content.Context
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.antigravityfinance.service.sms.detection.SmsDetectionModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Reads transaction notifications. Most banks and every UPI app post their alerts as
 * notifications rather than SMS, so without this listener those transactions are never
 * seen by the app at all.
 *
 * The service is declared in the manifest and enabled by the user in system settings; the
 * app calls [requestRebind] on every launch because Android unbinds the service
 * aggressively and a stale binding silently stops delivery.
 */
class TransactionNotificationListenerService : NotificationListenerService() {

    companion object {
        const val TAG = "TxNotificationListener"

        /**
         * Asks the system to bind this service. Safe to call repeatedly. On Android 11+
         * this needs the notification-listener permission, which the user grants in
         * system settings; without it the call is a no-op.
         */
        fun requestRebind(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
            try {
                android.service.notification.NotificationListenerService.requestRebind(
                    android.content.ComponentName(
                        context,
                        TransactionNotificationListenerService::class.java
                    )
                )
            } catch (e: Exception) {
                Log.w(TAG, "requestRebind failed: ${e.javaClass.simpleName}")
            }
        }
    }

    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "Listener connected")
        requestRebind(this)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.w(TAG, "Listener disconnected by the system; requesting a rebind")
        // Without this the service stays dead until the next app launch.
        requestRebind(this)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn?.notification ?: return
        val packageName = sbn.packageName ?: return

        // A group summary carries no message of its own and would duplicate the children.
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) {
            Log.d(TAG, "SKIPPED group summary from $packageName")
            return
        }

        val title = notification.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val texts = extractText(notification)
        if (texts.isEmpty()) {
            Log.d(TAG, "SKIPPED notification without text from $packageName")
            return
        }

        val sender = title.ifBlank { packageName }
        val timestamp = sbn.postTime
        scope.launch {
            texts.forEachIndexed { index, text ->
                val body = if (texts.size == 1) "$title $text".trim() else text
                Log.i(TAG, "RECEIVED [$index] from $packageName ('$sender'): $body")
                NotificationIngestor.ingest(applicationContext, body, sender, timestamp, "NOTIFICATION")
            }
        }
    }

    /**
     * Chat-style notifications (Google Pay, PhonePe, Paytm) hide the message inside
     * MessagingStyle rather than in the plain text extras.
     */
    private fun extractText(notification: Notification): List<String> {
        val texts = mutableListOf<String>()

        NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)
            ?.messages
            ?.forEach { message ->
                message.text?.toString()?.takeIf { it.isNotBlank() }?.let(texts::add)
            }

        if (texts.isEmpty()) {
            val extras = notification.extras
            extras?.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.forEach { line ->
                line?.toString()?.takeIf { it.isNotBlank() }?.let(texts::add)
            }
        }
        if (texts.isEmpty()) {
            extrasText(notification)?.takeIf { it.isNotBlank() }?.let(texts::add)
        }
        if (texts.isEmpty()) {
            extrasBigText(notification)?.takeIf { it.isNotBlank() }?.let(texts::add)
        }
        return texts
    }

    private fun extrasText(notification: Notification): String? =
        notification.extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString()

    private fun extrasBigText(notification: Notification): String? =
        notification.extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
}