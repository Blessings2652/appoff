package com.theblacksheep.appoff.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.theblacksheep.appoff.core.SettingsRepository

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        
        // Ensure core monitoring service is alive on any major system event
        RamMonitorService.start(context)

        when (action) {
            Intent.ACTION_BOOT_COMPLETED, "android.intent.action.QUICKBOOT_POWERON" -> {
                if (SettingsRepository.isCleanOnBootEnabled(context)) {
                    triggerQuickClean(context)
                }
            }
            @Suppress("DEPRECATION")
            Intent.ACTION_DEVICE_STORAGE_LOW -> {
                // Auto clean junk when storage is low
                triggerQuickClean(context)
            }
            Intent.ACTION_BATTERY_LOW -> {
                // Auto clean background apps to save battery
                if (SettingsRepository.isAutoCleanEnabled(context)) {
                    triggerQuickClean(context)
                }
            }
        }
    }

    private fun triggerQuickClean(context: Context) {
        val cleanIntent = Intent(context, RamMonitorService::class.java).apply {
            action = "com.theblacksheep.appoff.action.QUICK_CLEAN"
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(cleanIntent)
        } else {
            context.startService(cleanIntent)
        }
    }
}
