package com.theblacksheep.appoff.shizuku

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.theblacksheep.appoff.core.CleanResult
import com.theblacksheep.appoff.core.CleanStrategy
import com.theblacksheep.appoff.core.MemoryUtils
import com.theblacksheep.appoff.core.PROTECTED_PACKAGES
import com.theblacksheep.appoff.core.SettingsRepository
import com.theblacksheep.appoff.core.triggerMemoryPressure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Deep clean using official Shizuku API (borrows ADB/shell privileges).
 * Spawns shell processes directly via official Shizuku API.
 */
object ShizukuCleaner : CleanStrategy {
    private const val TAG = "ShizukuCleaner"
    override val name = "Shizuku"

    fun isShizukuAvailable(): Boolean = try {
        Shizuku.pingBinder()
    } catch (_: Throwable) {
        false
    }

    fun hasPermission(): Boolean = try {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) {
        false
    }

    suspend fun exec(command: String): String = withContext(Dispatchers.IO) {
        if (!isShizukuAvailable() || !hasPermission()) {
            return@withContext "Error: Shizuku unavailable or permission denied"
        }

        try {
            Log.d(TAG, "Executing via Shizuku shell: $command")
            val newProcessMethod = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            newProcessMethod.isAccessible = true
            val process = newProcessMethod.invoke(null, arrayOf("sh", "-c", command), null, null) as Process
            
            val output = StringBuilder()
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val errorReader = BufferedReader(InputStreamReader(process.errorStream))
            
            var line: String?
            while (reader.readLine().also { line = it } != null) output.append(line).append("\n")
            while (errorReader.readLine().also { line = it } != null) output.append(line).append("\n")
            
            process.waitFor()
            output.toString().trim()
        } catch (e: Throwable) {
            Log.e(TAG, "Shizuku exec failed", e)
            "Error: ${e.message}"
        }
    }

    @JvmStatic
    fun execBlocking(command: String): String = runBlocking {
        exec(command)
    }

    suspend fun warmUp() {
        try { isShizukuAvailable() } catch (_: Throwable) {}
    }

    /** Runs several shell commands in ONE round-trip instead of one exec per command. */
    suspend fun execAll(commands: List<String>): String = exec(commands.joinToString("; "))

    override suspend fun clean(
        context: Context,
        packages: List<String>,
        cacheOnly: Boolean,
        onProgress: (String) -> Unit,
        onTaskUpdate: (String) -> Unit,
    ): CleanResult = withContext(Dispatchers.IO) {
        if (!isShizukuAvailable() || !hasPermission()) {
            return@withContext CleanResult(packages.size, 0, 0, name)
        }

        val before = try { MemoryUtils.snapshot(context).availBytes } catch (_: Throwable) { 0L }
        val whitelist = try { SettingsRepository.getUserWhitelist(context) } catch (_: Throwable) { emptySet() }
        val filteredPackages = packages.filter { it !in whitelist && it !in PROTECTED_PACKAGES }

        onTaskUpdate("Injecting RAM Pressure")
        triggerMemoryPressure(context)
        
        val succeededPackages = mutableListOf<String>()

        if (!cacheOnly) {
            onTaskUpdate("Force-hibernating apps")
            filteredPackages.chunked(15).forEach { chunk ->
                val jobs = chunk.map { pkg ->
                    async {
                        onProgress(pkg)
                        delay(25)
                        val res = exec("am force-stop --user 0 $pkg")
                        if (!res.startsWith("Error:")) pkg else null
                    }
                }
                succeededPackages.addAll(jobs.awaitAll().filterNotNull())
            }
        } else {
            onTaskUpdate("Purging app caches")
            filteredPackages.chunked(10).forEach { chunk ->
                chunk.forEach { onProgress(it) }
                succeededPackages.addAll(chunk)
            }
        }

        onTaskUpdate("Purging system package caches")
        exec("pm trim-caches 999999999999 --user 0")

        val after = try { MemoryUtils.snapshot(context).availBytes } catch (_: Throwable) { before }
        CleanResult(packages.size, succeededPackages.size, (after - before).coerceAtLeast(0), name, succeededPackages)
    }

    suspend fun freeze(packageName: String): Boolean = withContext(Dispatchers.IO) {
        if (!isShizukuAvailable() || !hasPermission()) return@withContext false
        val r = exec("cmd package suspend --user 0 $packageName; cmd package disable-user --user 0 $packageName; am force-stop --user 0 $packageName")
        !r.startsWith("Error:")
    }

    suspend fun unfreeze(packageName: String): Boolean = withContext(Dispatchers.IO) {
        if (!isShizukuAvailable() || !hasPermission()) return@withContext false
        val r = exec("cmd package enable --user 0 $packageName; cmd package unsuspend --user 0 $packageName")
        !r.startsWith("Error:")
    }

    suspend fun forceStop(packageName: String): Boolean = withContext(Dispatchers.IO) {
        if (!isShizukuAvailable() || !hasPermission()) return@withContext false
        val result = exec("am force-stop --user 0 $packageName")
        !result.startsWith("Error:")
    }

    suspend fun disable(packageName: String): Boolean = withContext(Dispatchers.IO) {
        if (!isShizukuAvailable() || !hasPermission()) return@withContext false
        val cmd = "am force-stop --user 0 $packageName; " +
                  "cmd package disable-user --user 0 $packageName; " +
                  "pm disable-user --user 0 $packageName; " +
                  "cmd package hide --user 0 $packageName; " +
                  "pm hide --user 0 $packageName; " +
                  "pm uninstall -k --user 0 $packageName"
        val result = exec(cmd)
        !result.startsWith("Error:")
    }

    suspend fun enable(packageName: String): Boolean = withContext(Dispatchers.IO) {
        if (!isShizukuAvailable() || !hasPermission()) return@withContext false
        val cmd = "pm install-existing --user 0 $packageName; cmd package enable --user 0 $packageName; pm unhide --user 0 $packageName"
        val result = exec(cmd)
        !result.startsWith("Error:")
    }

    suspend fun clearCache(@Suppress("UNUSED_PARAMETER") packageName: String): Boolean = withContext(Dispatchers.IO) {
        if (!isShizukuAvailable() || !hasPermission()) return@withContext false
        val result = exec("pm trim-caches 999999999999 --user 0")
        !result.startsWith("Error:")
    }

    suspend fun suspend(packageName: String, suspended: Boolean): Boolean = withContext(Dispatchers.IO) {
        if (!isShizukuAvailable() || !hasPermission()) return@withContext false
        val cmd = if (suspended) {
            "am force-stop --user 0 $packageName; cmd package suspend --user 0 $packageName; pm suspend --user 0 $packageName"
        } else {
            "cmd package unsuspend --user 0 $packageName; pm unsuspend --user 0 $packageName; cmd package enable --user 0 $packageName"
        }
        val result = exec(cmd)
        !result.startsWith("Error:")
    }

    suspend fun setAppOp(packageName: String, op: Int, mode: Int): Boolean = withContext(Dispatchers.IO) {
        if (!isShizukuAvailable() || !hasPermission()) return@withContext false
        val modeStr = when (mode) {
            0 -> "allow"
            1 -> "ignore"
            else -> "deny"
        }
        exec("cmd appops set $packageName RUN_IN_BACKGROUND $modeStr")
        exec("cmd appops set $packageName RUN_ANY_IN_BACKGROUND $modeStr")
        val result = exec("cmd appops set $packageName $op $modeStr")
        !result.startsWith("Error:")
    }

    suspend fun setStandbyBucket(packageName: String, bucket: Int): Boolean = withContext(Dispatchers.IO) {
        if (!isShizukuAvailable() || !hasPermission()) return@withContext false
        val result = exec("am set-standby-bucket $packageName $bucket --user 0")
        !result.startsWith("Error:")
    }

    suspend fun setBatteryOptimizationExempt(packageName: String, exempt: Boolean): Boolean = withContext(Dispatchers.IO) {
        if (!isShizukuAvailable() || !hasPermission()) return@withContext false
        val op = if (exempt) "+" else "-"
        val result = exec("cmd deviceidle whitelist $op$packageName")
        !result.startsWith("Error:")
    }

    suspend fun uninstall(packageName: String): Boolean = withContext(Dispatchers.IO) {
        if (!isShizukuAvailable() || !hasPermission()) return@withContext false
        var result = exec("pm uninstall --user 0 $packageName")
        if (result.contains("Success", ignoreCase = true)) return@withContext true
        
        result = exec("pm uninstall -k --user 0 $packageName")
        if (result.contains("Success", ignoreCase = true)) return@withContext true
        
        exec("pm disable-user --user 0 $packageName")
        exec("pm hide --user 0 $packageName")
        val r3 = exec("pm uninstall -k --user 0 $packageName")
        forceStop(packageName)
        r3.contains("Success", ignoreCase = true)
    }
}
