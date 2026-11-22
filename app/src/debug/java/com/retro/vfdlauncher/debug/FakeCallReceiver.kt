package com.retro.vfdlauncher.debug

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Process
import android.service.notification.StatusBarNotification
import com.retro.vfdlauncher.data.phone.PhoneCalls

/**
 * Debug builds only. Posts a call-style notification so the call bar can be tested without a
 * real call:
 * see [FakeCallActivity] for the adb command. The ANSWER / DECLINE / HANG UP buttons come back
 * here and move the fake call along.
 */
class FakeCallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val nm = context.getSystemService(NotificationManager::class.java)
        runCatching { nm.createNotificationChannel(NotificationChannel(CHANNEL, "Fake call (debug)", NotificationManager.IMPORTANCE_LOW)) }
        val stage = intent.getStringExtra("stage") ?: "ringing"
        if (stage == "end") {
            nm.cancel(ID)
            PhoneCalls.clear()
            return
        }
        fun action(title: String, next: String) = Notification.Action.Builder(
            null, title,
            PendingIntent.getBroadcast(
                context.applicationContext, next.hashCode(),
                Intent(context.applicationContext, FakeCallReceiver::class.java).putExtra("stage", next),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        ).build()
        val b = Notification.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.sym_call_incoming)
            .setCategory(Notification.CATEGORY_CALL)
            .setOngoing(true)
            .setContentTitle("SARAH")
        when (stage) {
            "ringing" -> b.setContentText("Incoming call").addAction(action("Answer", "active")).addAction(action("Decline", "end"))
            "nobuttons" -> b.setContentText("Incoming call")
            "calling" -> b.setContentText("Calling…").addAction(action("Hang up", "end"))
            else -> b.setContentText("+32 470 12 34 56").addAction(action("Hang up", "end"))
        }
        val n = b.build()
        // Post it for real, and also hand it straight to PhoneCalls: some phones block this app's
        // notifications, and the listener path itself is covered by CallParserTest.
        runCatching { nm.notify(ID, n) }
        @Suppress("DEPRECATION")
        PhoneCalls.onPosted(
            StatusBarNotification(
                context.packageName, context.packageName, ID, null, Process.myUid(), Process.myPid(), 0,
                n, Process.myUserHandle(), System.currentTimeMillis(),
            ),
        )
    }

    private companion object {
        const val CHANNEL = "fakecall"
        const val ID = 4242
    }
}
