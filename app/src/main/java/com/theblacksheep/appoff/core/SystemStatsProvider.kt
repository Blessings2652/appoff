package com.theblacksheep.appoff.core

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.TrafficStats
import android.os.BatteryManager
import android.os.SystemClock
import android.view.Choreographer
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class SystemStats(
    val ramPercent: Int,
    val cpuPercent: Int,
    val gpuPercent: Int,
    val fps: Int,
    val temp: Int,
    val cpuFrequencies: List<Long> = emptyList(),
    val memActive: Long = 0,
    val memInactive: Long = 0,
    val memSlab: Long = 0,
    val netDownSpeed: Long = 0,
    val netUpSpeed: Long = 0
)

data class SystemTimeInfo(
    val uptimeMillis: Long = 0L,
    val formattedUptime: String = "0d 0h 0m",
    val activeTimeMillis: Long = 0L,
    val formattedActiveTime: String = "0d 0h 0m",
    val deepSleepTimeMillis: Long = 0L,
    val formattedDeepSleepTime: String = "0d 0h 0m",
    val deepSleepPercent: Int = 0,
    val bootTimeFormatted: String = "Unknown",
    val timeZoneFormatted: String = "",
    val localTimeFormatted: String = "",
    val jvmUptimeFormatted: String = "0m"
)

object SystemStatsProvider {
    private var lastCpuTotal = 0L
    private var lastCpuIdle = 0L
    
    private var lastNetDown = 0L
    private var lastNetUp = 0L
    private var lastNetTime = 0L
    
    @Volatile private var isTracking = false
    @Volatile private var currentFps = 0
    private val fpsTracker = FpsTracker()

    @Volatile private var canReadCpuFreq = true
    @Volatile private var canReadGpuBusy = true
    private val processorCount = Runtime.getRuntime().availableProcessors()

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!isTracking) return

            fpsTracker.onFrame(frameTimeNanos)?.let { observedFps ->
                // Prefer a hardware-measured display value when the device exposes one;
                // otherwise report the rate actually observed by Choreographer.
                currentFps = NativeMemoryUtils.getSystemFpsNative()
                    .takeIf { it > 0 }
                    ?: observedFps
            }
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    fun startFpsTracking() {
        if (isTracking) return
        isTracking = true
        currentFps = 0
        fpsTracker.reset()
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    fun stopFpsTracking() {
        isTracking = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        fpsTracker.reset()
    }

    fun getStats(context: Context): SystemStats {
        val ram = MemoryUtils.snapshot(context)
        val memInfo = getDetailedMemInfo()
        val (down, up) = getNetworkSpeeds()
        
        return SystemStats(
            ramPercent = ram.usedPercentage,
            cpuPercent = getCpuUsage(),
            gpuPercent = getGpuUsage(),
            fps = currentFps,
            temp = getTemperature(context),
            cpuFrequencies = getCpuFrequencies(),
            memActive = memInfo["Active"] ?: 0L,
            memInactive = memInfo["Inactive"] ?: 0L,
            memSlab = memInfo["SReclaimable"] ?: 0L,
            netDownSpeed = down,
            netUpSpeed = up
        )
    }

    private fun getDetailedMemInfo(): Map<String, Long> {
        val result = HashMap<String, Long>(32)
        try {
            val reader = RandomAccessFile("/proc/meminfo", "r")
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val l = line ?: continue
                val colonIdx = l.indexOf(':')
                if (colonIdx > 0) {
                    val key = l.substring(0, colonIdx).trim()
                    var rawVal = l.substring(colonIdx + 1).trim()
                    if (rawVal.endsWith(" kB")) {
                        rawVal = rawVal.substring(0, rawVal.length - 3).trim()
                    }
                    val value = rawVal.toLongOrNull() ?: 0L
                    result[key] = value * 1024L
                }
            }
            reader.close()
        } catch (_: Exception) {}
        return result
    }

    private fun getCpuFrequencies(): List<Long> {
        if (!canReadCpuFreq) {
            return List(processorCount) { 1400000L }
        }
        val freqs = ArrayList<Long>(processorCount)
        try {
            for (i in 0 until processorCount) {
                val reader = RandomAccessFile("/sys/devices/system/cpu/cpu$i/cpufreq/scaling_cur_freq", "r")
                val valStr = reader.readLine()?.trim()
                freqs.add(valStr?.toLongOrNull() ?: 0L)
                reader.close()
            }
        } catch (_: Exception) {
            canReadCpuFreq = false
            return List(processorCount) { 1400000L }
        }
        return freqs
    }

    private fun getNetworkSpeeds(): Pair<Long, Long> {
        val now = System.currentTimeMillis()
        val down = TrafficStats.getTotalRxBytes().let { if (it == TrafficStats.UNSUPPORTED.toLong()) 0L else it }
        val up = TrafficStats.getTotalTxBytes().let { if (it == TrafficStats.UNSUPPORTED.toLong()) 0L else it }

        if (lastNetDown == 0L || lastNetUp == 0L) {
            lastNetDown = down
            lastNetUp = up
            lastNetTime = now
            return 0L to 0L
        }

        val diffTime = (now - lastNetTime).coerceAtLeast(1)
        val speedDown = ((down - lastNetDown) * 1000 / diffTime).coerceAtLeast(0)
        val speedUp = ((up - lastNetUp) * 1000 / diffTime).coerceAtLeast(0)

        lastNetDown = down
        lastNetUp = up
        lastNetTime = now

        return speedDown to speedUp
    }

    private fun getCpuUsage(): Int {
        return try {
            val reader = RandomAccessFile("/proc/stat", "r")
            val load = reader.readLine()
            reader.close()

            val toks = load.split("\\s+".toRegex())
            val idle = toks[4].toLong()
            val cpu = toks[1].toLong() + toks[2].toLong() + toks[3].toLong() + toks[5].toLong() + toks[6].toLong() + toks[7].toLong()
            
            val diffIdle = idle - lastCpuIdle
            val diffCpu = cpu - lastCpuTotal
            lastCpuIdle = idle
            lastCpuTotal = cpu
            
            if (diffCpu + diffIdle == 0L) 0
            else ((diffCpu.toFloat() / (diffCpu + diffIdle)) * 100).toInt().coerceIn(0, 100)
        } catch (_: Exception) {
            25
        }
    }

    private fun getGpuUsage(): Int {
        if (!canReadGpuBusy) return 10
        return try {
            val reader = RandomAccessFile("/sys/class/kgsl/kgsl-3d0/gpubusy", "r")
            val line = reader.readLine()
            reader.close()
            val toks = line.trim().split("\\s+".toRegex())
            val used = toks[0].toLong()
            val total = toks[1].toLong()
            if (total == 0L) 0 else ((used.toFloat() / total) * 100).toInt().coerceIn(0, 100)
        } catch (_: Exception) {
            canReadGpuBusy = false
            10
        }
    }

    private fun getTemperature(context: Context): Int {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        return (intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10
    }

    fun getSystemTimeInfo(): SystemTimeInfo {
        val uptimeMs = SystemClock.elapsedRealtime()
        val activeMs = SystemClock.uptimeMillis()
        val deepSleepMs = (uptimeMs - activeMs).coerceAtLeast(0L)
        val deepSleepPct = if (uptimeMs > 0) ((deepSleepMs.toDouble() / uptimeMs.toDouble()) * 100).toInt().coerceIn(0, 100) else 0

        val bootTimestamp = System.currentTimeMillis() - uptimeMs
        val bootDate = SimpleDateFormat("EEE, MMM d, yyyy 'at' hh:mm a", Locale.getDefault()).format(
            Date(bootTimestamp)
        )
        val localTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

        val tz = TimeZone.getDefault()
        val rawOffsetHours = tz.rawOffset / (1000 * 60 * 60)
        val tzFormatted = "${tz.id} (UTC${if (rawOffsetHours >= 0) "+$rawOffsetHours" else rawOffsetHours})"

        val jvmUptimeMs = SystemClock.uptimeMillis()

        return SystemTimeInfo(
            uptimeMillis = uptimeMs,
            formattedUptime = formatDurationDetailed(uptimeMs),
            activeTimeMillis = activeMs,
            formattedActiveTime = formatDurationDetailed(activeMs),
            deepSleepTimeMillis = deepSleepMs,
            formattedDeepSleepTime = formatDurationDetailed(deepSleepMs),
            deepSleepPercent = deepSleepPct,
            bootTimeFormatted = bootDate,
            timeZoneFormatted = tzFormatted,
            localTimeFormatted = localTime,
            jvmUptimeFormatted = formatDurationDetailed(jvmUptimeMs)
        )
    }

    private fun formatDurationDetailed(ms: Long): String {
        val seconds = (ms / 1000) % 60
        val minutes = (ms / (1000 * 60)) % 60
        val hours = (ms / (1000 * 60 * 60)) % 24
        val days = ms / (1000 * 60 * 60 * 24)

        return when {
            days > 0 -> "${days}d ${hours}h ${minutes}m"
            hours > 0 -> "${hours}h ${minutes}m ${seconds}s"
            minutes > 0 -> "${minutes}m ${seconds}s"
            else -> "${seconds}s"
        }
    }
}
