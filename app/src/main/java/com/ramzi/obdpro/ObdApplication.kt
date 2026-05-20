package com.ramzi.obdpro

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

/**
 * Application class for OBD Pro.
 * Initializes notification channels required by the foreground service
 * and alert system.
 */
class ObdApplication : Application() {

    companion object {
        const val CHANNEL_SERVICE = "obd_service_channel"
        const val CHANNEL_ALERTS = "obd_alerts_channel"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_SERVICE,
                "OBD Connection Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Persistent notification while connected to ELM327 adapter"
            }

            val alertChannel = NotificationChannel(
                CHANNEL_ALERTS,
                "Vehicle Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Critical vehicle sensor alerts (overheating, low battery)"
                enableVibration(true)
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
            manager.createNotificationChannel(alertChannel)
        }
    }
}
