package com.theblacksheep.appoff.core

import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class ThreatLevel { LOW, MEDIUM, HIGH }

data class VirusThreat(
    val packageName: String,
    val appLabel: String,
    val threatLevel: ThreatLevel,
    val description: String
)

/**
 * A heuristic-based scanner that looks for "suspicious" patterns in installed apps.
 * In a real production app, this would query a remote cloud signature database.
 */
object VirusScanner {

    suspend fun scanInstalledApps(context: Context): List<VirusThreat> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val threats = mutableListOf<VirusThreat>()

        for (app in installedApps) {
            // Pattern 1: Apps with "System" or "Android" in their name but not signed by system
            val label = pm.getApplicationLabel(app).toString().lowercase()
            val isSystem = (app.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
            
            if (!isSystem && (label.contains("system update") || label.contains("android core"))) {
                threats.add(VirusThreat(
                    packageName = app.packageName,
                    appLabel = pm.getApplicationLabel(app).toString(),
                    threatLevel = ThreatLevel.HIGH,
                    description = "Possible system impersonation detected."
                ))
            }

            // Pattern 2: Apps with excessive permission combinations
            // (Simplified for this tech demo)
        }

        // Simulating some processing time for the "premium" feel
        kotlinx.coroutines.delay(2000)
        
        threats
    }
}
