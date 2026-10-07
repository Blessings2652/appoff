package com.theblacksheep.appoff.core

import android.app.ActivityManager
import android.content.Context
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.util.Locale


data class RamSnapshot(
    val totalBytes: Long,
    val availBytes: Long,
    val thresholdBytes: Long,
    val lowMemory: Boolean,
    val cachedBytes: Long,
    val sReclaimableBytes: Long,
    val swapTotalBytes: Long,
    val swapFreeBytes: Long,
) {
    // True 'Used' memory = Total - Available
    val usedBytes get() = (totalBytes - availBytes).coerceAtLeast(0)
    
    // ✅ REAL Percentage (matches system settings)
    val usedPercentage get() = if (totalBytes <= 0) 0 else ((usedBytes * 100) / totalBytes).toInt()
    val freePercentage get() = 100 - usedPercentage

    // Smooth the used fraction for UI display
    val usedFraction get() = if (totalBytes <= 0) 0f else (usedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
}

object MemoryUtils {

    fun snapshot(context: Context): RamSnapshot {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)

        val proc = readProcMeminfo()
        
        // ✅ REAL Total RAM (physical hardware)
        val totalRAM = info.totalMem

        // ✅ REAL Available RAM (includes cached processes!)
        val availableRAM = info.availMem
        
        // Ensure available doesn't exceed total
        val availBytes = availableRAM.coerceAtMost(totalRAM)

        return RamSnapshot(
            totalBytes = totalRAM,
            availBytes = availBytes,
            thresholdBytes = info.threshold,
            lowMemory = info.lowMemory,
            cachedBytes = proc["Cached"] ?: 0L,
            sReclaimableBytes = proc["SReclaimable"] ?: 0L,
            swapTotalBytes = proc["SwapTotal"] ?: 0L,
            swapFreeBytes = proc["SwapFree"] ?: 0L,
        )
    }



    /** Parses /proc/meminfo. Values returned in bytes. */
    private fun readProcMeminfo(): Map<String, Long> {
        val result = mutableMapOf<String, Long>()
        val f = File("/proc/meminfo")
        if (!f.exists() || !f.canRead()) return result
        try {
            BufferedReader(FileReader(f)).use { reader ->
                reader.forEachLine { line ->
                    val parts = line.split(Regex(":\\s+"))
                    if (parts.size == 2) {
                        val key = parts[0].trim()
                        // Most values are in KB, but we check to be safe
                        val valueParts = parts[1].trim().split(Regex("\\s+"))
                        val valueRaw = valueParts[0].toLongOrNull() ?: 0L
                        val isKb = (valueParts.size > 1 && valueParts[1].lowercase() == "kb")
                        
                        result[key] = if (isKb) valueRaw * 1024L else valueRaw
                    }
                }
            }
        } catch (_: Exception) {}
        return result
    }

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 MB"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0

        return when {
            gb >= 1.0 -> String.format(Locale.US, "%.1f GB", gb)
            mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
            else -> String.format(Locale.US, "%.1f KB", kb)
        }
    }
}
