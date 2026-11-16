package com.nebula.assistant.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/** Tiene viva l'app mentre il navigatore guida (altrimenti Android la fermerebbe a schermo spento). */
class NavService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel("nav", "Navigazione", NotificationManager.IMPORTANCE_LOW))
        val n: Notification = NotificationCompat.Builder(this, "nav")
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setContentTitle("Navigazione in corso")
            .setContentText("Nebula ti sta guidando")
            .setOngoing(true)
            .build()
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            else startForeground(1, n)
        } catch (e: Exception) {
            stopSelf() // senza permesso di posizione il servizio non può partire: la guida continua finché l'app è aperta
        }
        return START_STICKY
    }

    companion object {
        fun set(ctx: Context, on: Boolean) {
            val i = Intent(ctx, NavService::class.java)
            if (on) ContextCompat.startForegroundService(ctx, i) else ctx.stopService(i)
        }
    }
}
