package com.theblacksheep.appoff

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.theblacksheep.appoff.di.AppComponent
import com.theblacksheep.appoff.di.AppModule
import com.theblacksheep.appoff.di.DaggerAppComponent
import com.theblacksheep.appoff.di.PresenterModule
import com.theblacksheep.appoff.di.UseCaseModule
import com.theblacksheep.appoff.service.MaintenanceWorker

class CleanerApp : Application() {

    lateinit var appComponent: AppComponent
        private set

    companion object {
        const val CHANNEL_ID = "ram_monitor_service"
        const val ALERT_CHANNEL_ID = "cleanup_alerts"

        fun getAppComponent(context: Context): AppComponent {
            return (context.applicationContext as CleanerApp).appComponent
        }
    }

    override fun onCreate() {
        super.onCreate()

        appComponent = DaggerAppComponent.builder()
            .appModule(AppModule(this))
            .useCaseModule(UseCaseModule())
            .presenterModule(PresenterModule())
            .build()
        appComponent.inject(this)

        createNotificationChannels()
        MaintenanceWorker.schedule(this)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // Channel 1: RAM Monitor (Low Priority)
            val name = getString(R.string.notif_channel_name)
            val descriptionText = getString(R.string.notif_channel_desc)
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)

            // Channel 2: Cleanup Alerts (High Priority for Pop-ups)
            val alertChannel = NotificationChannel(
                ALERT_CHANNEL_ID,
                "Cleanup Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Urgent notifications for automatic background cleaning"
            }
            notificationManager.createNotificationChannel(alertChannel)
        }
    }
}
