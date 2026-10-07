package com.theblacksheep.appoff

import android.content.Context
import android.os.Build
import com.theblacksheep.appoff.shizuku.ShizukuHelper

object FreezerHelper {

    /**
     * Get count of frozen/suspended apps using Shizuku.
     */
    suspend fun getFrozenAppCount(context: Context): Int {
        if (!ShizukuHelper.hasPermission()) {
            return 0
        }

        return try {
            val command = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                "pm list packages -s"
            } else {
                "pm list packages -d"
            }
            
            val result = ShizukuHelper.runCommand(command)
            if (result.startsWith("Error")) return 0
            
            result.lineSequence()
                .filter { it.trim().startsWith("package:") }
                .count()
        } catch (_: Exception) {
            0
        }
    }
    
    // Note: freezeAll and toggleFreeze were unused and relied on deprecated AsyncTask.
    // Modern freezing logic is handled in CleanerViewModel using Coroutines and Root/Shizuku cleaners directly.
}
