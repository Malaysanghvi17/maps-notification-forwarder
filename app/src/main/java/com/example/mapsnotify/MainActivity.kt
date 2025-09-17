
package com.example.mapsnotify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.method.ScrollingMovementMethod
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var tvNotifications: TextView
    private lateinit var btnToggleMonitoring: Button
    private lateinit var tvStatus: TextView
    private var isMonitoring = false
    private lateinit var notificationReceiver: NotificationReceiver
    private lateinit var notificationManager: NotificationManagerCompat

    companion object {
        const val CHANNEL_ID = "maps_notify_channel"
        const val NOTIFICATION_ID = 1001
    }
    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                val logEntry = "Permission is granted. You can now post notifications."

                runOnUiThread {
                    tvNotifications.append(logEntry)
                }
            } else {
                val logEntry = "notification permission denied!"

                runOnUiThread {
                    tvNotifications.append(logEntry)
                }
            }
        }
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            updateUi()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupNotificationChannel()
        askNotificationPermission()
        setupReceiver()
        updateUi()
    }

    private fun initViews() {
        tvNotifications = findViewById(R.id.tv_notifications)
        btnToggleMonitoring = findViewById(R.id.btn_toggle_monitoring)
        tvStatus = findViewById(R.id.tv_status)

        tvNotifications.movementMethod = ScrollingMovementMethod()

        btnToggleMonitoring.setOnClickListener {
            if (isNotificationListenerEnabled()) {
                toggleMonitoring()
            } else {
                requestNotificationListenerPermission()
            }
        }
    }
    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                // Directly ask for the permission
                requestNotificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
    private fun setupNotificationChannel() {
        notificationManager = NotificationManagerCompat.from(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Maps Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Replayed Google Maps notifications for smartwatch"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun setupReceiver() {
        notificationReceiver = NotificationReceiver()
        val intentFilter = IntentFilter("com.example.mapsnotify.NOTIFICATION_LISTENER")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(notificationReceiver, intentFilter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(notificationReceiver, intentFilter)
        }
    }

    private fun toggleMonitoring() {
        isMonitoring = !isMonitoring
        val serviceIntent = Intent(this, MapsNotificationListener::class.java)

        if (isMonitoring) {
            startService(serviceIntent)
            tvNotifications.text = "🚀 Monitoring started...\nWaiting for Google Maps notifications...\n\n"
        } else {
            stopService(serviceIntent)
            tvNotifications.append("\n⏹️ Monitoring stopped.\n")
        }
        updateUi()
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val enabledListeners = Settings.Secure.getString(
            contentResolver,
            "enabled_notification_listeners"
        )
        return enabledListeners?.contains(componentName.flattenToString()) ?: false
    }

    private fun requestNotificationListenerPermission() {
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog)
            .setTitle("🔐 Permission Required")
            .setMessage("To relay Maps notifications to your watch, enable 'Maps Notify' in Notification Access settings.")
            .setPositiveButton("Open Settings") { _, _ ->
                val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                notificationPermissionLauncher.launch(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun createNotification(title: String, text: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setContentTitle(title)
            .setContentText("$text - 🗺️")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setTimeoutAfter(60000)
            .build()

        if (NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            notificationManager.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun updateUi() {
        when {
            !isNotificationListenerEnabled() -> {
                btnToggleMonitoring.text = "🔓 Enable Notification Access"
                btnToggleMonitoring.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_red_light))
                tvStatus.text = "❌ Permission Required"
                tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
                isMonitoring = false
            }
            isMonitoring -> {
                btnToggleMonitoring.text = "⏹️ Stop Monitoring"
                btnToggleMonitoring.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_orange_light))
                tvStatus.text = "✅ Active - Relaying to Watch"
                tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
            }
            else -> {
                btnToggleMonitoring.text = "▶️ Start Monitoring"
                btnToggleMonitoring.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_blue_light))
                tvStatus.text = "⏸️ Ready to Start"
                tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray))
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(notificationReceiver)
    }

    inner class NotificationReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let {
                val title = it.getStringExtra("Direction") ?: "Maps"
                val text = it.getStringExtra("TimeDistInfo") ?: "Navigation update"

                // Create our own notification that will appear on the watch
                createNotification(title, text)

                // Also log to the UI
                val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                    .format(java.util.Date())
                val logEntry = "[$timestamp] 📱⌚\n📍 $title\n💬 $text\n\n"

                runOnUiThread {
                    tvNotifications.append(logEntry)
                }
            }
        }
    }
}