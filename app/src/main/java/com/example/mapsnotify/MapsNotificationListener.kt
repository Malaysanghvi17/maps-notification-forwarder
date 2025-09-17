package com.example.mapsnotify

import android.app.Notification
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class MapsNotificationListener : NotificationListenerService() {
    private var turnDistance = "0 m"
    companion object {
        private const val GOOGLE_MAPS_PACKAGE = "com.google.android.apps.maps"
        private const val TAG = "MapsNotifyListener"
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "🔗 Notification Listener connected - Ready to relay Maps notifications")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        sbn ?: return

        // Only process Google Maps notifications
        if (sbn.packageName == GOOGLE_MAPS_PACKAGE) {
            val notification = sbn.notification
            val extras = notification.extras

            val directionText = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString() // e.g., "3 min · 240 m"
            val titleText = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() // e.g., "0 m"
            val directionWithSymbol = getDirectionSymbol(directionText)
            if (titleText != null && titleText != "") {
                turnDistance = titleText
            };
            Log.d(TAG, "Maps notification captured: '$turnDistance' - '$directionWithSymbol' - '$subText'")

            // Send to MainActivity to create our own notification
            val intent = Intent("com.example.mapsnotify.NOTIFICATION_LISTENER").apply {
                putExtra("Direction", "("+directionWithSymbol+")" + titleText + " . " + directionText)
                putExtra("TimeDistInfo", subText)
            }
            sendBroadcast(intent)
        }
    }
    private fun getDirectionSymbol(text: String?): String {
        text ?: return "Navigation Update"
        val lowerText = text.lowercase()

        val symbol = when {
            "turn right" in lowerText || "exit right" in lowerText || "right" in lowerText -> "➡️"
            "turn left" in lowerText || "exit left" in lowerText || "left" in lowerText -> "⬅️"
            "keep right" in lowerText || "right" in lowerText -> "↗️"
            "keep left" in lowerText || "left" in lowerText -> "↖️"
            "make a u-turn" in lowerText || "u" in lowerText -> "↩️"
            "roundabout" in lowerText || "round" in lowerText -> "🔄"
            // We learned from your log that "straight" can be "Head north", "Head west", etc.
            "continue straight" in lowerText || "go straight" in lowerText || "straight" in lowerText || lowerText.startsWith("head") -> "⬆️"
            "destination" in lowerText -> "🏁"
            else -> "📍" // Default symbol for unknown directions
        }
        // Return the symbol followed by the original text
        return symbol
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.i(TAG, "🔌 Notification Listener disconnected")
    }
}