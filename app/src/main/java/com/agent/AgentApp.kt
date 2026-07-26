package com.agent

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.agent.data.PreferencesManager

class AgentApp : Application() {

    companion object {
        const val CHANNEL_SERVICE = "agent_service"
        const val CHANNEL_COMMANDS = "agent_commands"
        lateinit var instance: AgentApp
            private set
        lateinit var prefs: PreferencesManager
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        prefs = PreferencesManager(this)
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_SERVICE,
                "Agent Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background service notification"
            }
            val channel2 = NotificationChannel(
                CHANNEL_COMMANDS,
                "Agent Commands",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Command execution notifications"
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
            nm.createNotificationChannel(channel2)
        }
    }
}
