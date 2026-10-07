package com.theblacksheep.appoff.core

import android.annotation.SuppressLint
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.SharedPreferences
import java.net.InetAddress

data class CleaningStats(
    val totalMemoryFreedBytes: Long,
    val totalCleanups: Int
)

data class AppUsage(
    val packageName: String,
    val totalTimeInForeground: Long,
    val lastTimeUsed: Long
)

object StatsRepository {
    private const val PREFS_NAME = "cleaning_stats"
    private const val KEY_TOTAL_FREED = "total_memory_freed"
    private const val KEY_TOTAL_CLEANUPS = "total_cleanups"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getStats(context: Context): CleaningStats {
        val prefs = getPrefs(context)
        return CleaningStats(
            totalMemoryFreedBytes = prefs.getLong(KEY_TOTAL_FREED, 0L),
            totalCleanups = prefs.getInt(KEY_TOTAL_CLEANUPS, 0)
        )
    }

    @SuppressLint("UseKtx")
    fun recordCleanup(context: Context, bytesFreed: Long) {
        val prefs = getPrefs(context)
        val currentCleanups = prefs.getInt(KEY_TOTAL_CLEANUPS, 0)

        // Reset and overwrite to show only the last session's freed RAM as requested
        prefs.edit()
            .putLong(KEY_TOTAL_FREED, bytesFreed)
            .putInt(KEY_TOTAL_CLEANUPS, currentCleanups + 1)
            .apply()
    }

    fun resetStats(context: Context) {
        getPrefs(context).edit().clear().apply()
    }

    // --- SCREEN TIME ---

    fun getScreenTimeStats(context: Context): List<AppUsage> {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val endTime = System.currentTimeMillis()
        val startTime = endTime - (24 * 60 * 60 * 1000) // Last 24 hours
        
        val stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime)
        return stats.filter { it.totalTimeInForeground > 0 }
            .map { AppUsage(it.packageName, it.totalTimeInForeground, it.lastTimeUsed) }
            .sortedByDescending { it.totalTimeInForeground }
    }

    // --- NETWORK TOOLS ---

    fun ping(host: String = "8.8.8.8"): Long {
        return try {
            val startTime = System.currentTimeMillis()
            val address = InetAddress.getByName(host)
            if (address.isReachable(3000)) {
                System.currentTimeMillis() - startTime
            } else -1L
        } catch (e: Exception) {
            -1L
        }
    }
}
