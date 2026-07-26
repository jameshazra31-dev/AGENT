package com.agent.service

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import com.agent.AgentApp

class AgentForegroundService : Service() {
    override fun onBind(p0: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = Notification.Builder(this, AgentApp.CHANNEL_SERVICE)
            .setContentTitle("AGENT Active")
            .setContentText("Phone control service is running")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
        startForeground(1, notification)
        return START_STICKY
    }
}
