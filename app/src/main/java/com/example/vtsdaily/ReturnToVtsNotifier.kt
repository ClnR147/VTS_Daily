package com.example.vtsdaily

import android.Manifest
//noinspection SuspiciousImport
import android.R
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object ReturnToVtsNotifier {

    const val EXTRA_CALLER_VIEW = "extra_caller_view"
    private const val CHANNEL_ID = "vts_return"
    private const val CHANNEL_NAME = "Return to VTS"
    private const val NOTIF_ID = 147  // your lucky seed 😄

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    fun show(context: Context, callerView: Int) {
        ensureChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            // bring existing task to front if possible
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_CALLER_VIEW, callerView)
        }

        val piFlags =
            PendingIntent.FLAG_UPDATE_CURRENT or
                    (PendingIntent.FLAG_IMMUTABLE)

        val pendingIntent = PendingIntent.getActivity(
            context,
            callerView, // unique-ish per screen
            intent,
            piFlags
        )

        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_menu_call) // replace with your icon later
            .setContentTitle("Return to VTS Daily")
            .setContentText("Tap to return to the screen you called from")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIF_ID, notif)
    }

    fun clear(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIF_ID)
    }

    private fun ensureChannel(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val existing = nm.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH)
        )
    }
}
