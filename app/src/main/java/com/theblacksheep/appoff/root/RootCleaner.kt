package com.theblacksheep.appoff.root

import android.content.Context
import com.theblacksheep.appoff.core.CleanResult
import com.theblacksheep.appoff.core.CleanStrategy
import com.theblacksheep.appoff.core.MemoryUtils
import com.theblacksheep.appoff.core.NativeMemoryUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataOutputStream

/**
 * True "deep clean": force-stops each target package via `am force-stop`.
 */
object RootCleaner : CleanStrategy {
    override val name = "Root"

    @Volatile private var rootCache: Boolean? = null
    @Volatile private var rootCheckedAtMs = 0L

    /**
     * `su` is probed at most once a minute when root is missing (and only once when present) -
     * previously every refresh spawned a process here, which could stall the app list for seconds.
     */
    suspend fun isRootAvailable(forceRecheck: Boolean = false): Boolean {
        val cached = rootCache
        val now = System.currentTimeMillis()
        if (!forceRecheck && cached != null && (cached || now - rootCheckedAtMs < 60_000L)) return cached
        return probeRoot().also { rootCache = it; rootCheckedAtMs = System.currentTimeMillis() }
    }

    private suspend fun probeRoot(): Boolean = withContext(Dispatchers.IO) {
        try {
            val process = ProcessBuilder("su", "-c", "id").start()
            val exit = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                if (!process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    process.destroyForcibly()
                    return@withContext false
                }
                process.exitValue()
            } else {
                process.waitFor()
            }
            exit == 0
        } catch (_: Throwable) {
            false
        }
    }

    override suspend fun clean(
        context: Context,
        packages: List<String>,
        cacheOnly: Boolean,
        onProgress: (String) -> Unit,
        onTaskUpdate: (String) -> Unit,
    ): CleanResult = withContext(Dispatchers.IO) {
        val before = try { MemoryUtils.snapshot(context).availBytes } catch (_: Throwable) { 0L }
        
        val whitelist = try { com.theblacksheep.appoff.core.SettingsRepository.getUserWhitelist(context) } catch (_: Throwable) { emptySet() }
        val filteredPackages = packages.filter { it !in whitelist && it !in com.theblacksheep.appoff.core.PROTECTED_PACKAGES }

        try {
            onTaskUpdate("Hardware RAM Pressure Injection")
            com.theblacksheep.appoff.core.triggerMemoryPressure(context)
        } catch (_: Throwable) {}
        
        val succeededPackages = mutableListOf<String>()
        
        if (!cacheOnly) {
            try { onTaskUpdate("Root deep force-stop") } catch (_: Throwable) {}
            
            // Parallelize hibernation: process in larger chunks for Root and run them as a single command string
            // to minimize su process overhead.
            filteredPackages.chunked(12).forEach { chunk ->
                val cmd = chunk.joinToString("; ") { 
                    "am force-stop --user 0 $it; am set-standby-bucket $it 45 --user 0; cmd appops set $it RUN_IN_BACKGROUND ignore" 
                }
                if (runAsRoot(cmd)) {
                    succeededPackages.addAll(chunk)
                    try { onProgress(chunk.last()) } catch (_: Throwable) {}
                }
            }
        } else {
            try { onTaskUpdate("Purging app cache data") } catch (_: Throwable) {}
            filteredPackages.chunked(8).forEach { chunk ->
                val cacheCmd = chunk.joinToString("; ") { "pm trim-caches 999999999999" } 
                if (runAsRoot(cacheCmd)) {
                    succeededPackages.addAll(chunk)
                    try { onProgress(chunk.last()) } catch (_: Throwable) {}
                }
            }
        }

        try {
            onTaskUpdate("Dropping Kernel Page Caches")
            if (!NativeMemoryUtils.dropCachesNative(3)) {
                runAsRoot("sync && echo 3 > /proc/sys/vm/drop_caches")
            }
        } catch (_: Throwable) {}
        
        try {
            onTaskUpdate("Kernel Memory Compaction")
            NativeMemoryUtils.compactMemoryNative()
        } catch (_: Throwable) {}

        try {
            onTaskUpdate("Storage block optimization (FSTRIM)")
            runAsRoot("fstrim -v /data; fstrim -v /cache; fstrim -v /system")
        } catch (_: Throwable) {}

        try {
            onTaskUpdate("Global cache purge")
            runAsRoot("pm trim-caches 999999999999")
        } catch (_: Throwable) {}

        val after = try { MemoryUtils.snapshot(context).availBytes } catch (_: Throwable) { before }
        CleanResult(
            attempted = packages.size,
            succeeded = succeededPackages.size,
            freedBytesEstimate = (after - before).coerceAtLeast(0),
            strategy = name,
            succeededPackages = succeededPackages
        )
    }

    suspend fun freeze(packageName: String): Boolean = withContext(Dispatchers.IO) {
        // One su session, no `pm uninstall -k` (slow and unnecessary once suspended + disabled).
        val results = runAsRootChain(listOf(
            "cmd package suspend --user 0 $packageName",
            "cmd package disable-user --user 0 $packageName",
            "am force-stop --user 0 $packageName"
        ))
        (results.getOrNull(0) == true) || (results.getOrNull(1) == true)
    }

    suspend fun unfreeze(packageName: String): Boolean = withContext(Dispatchers.IO) {
        // Fast path only; `pm install-existing` is a fallback the caller runs if the app is
        // still hidden after this (see CleanerViewModel.unfreezeApp).
        val results = runAsRootChain(listOf(
            "cmd package enable --user 0 $packageName",
            "cmd package unsuspend --user 0 $packageName"
        ))
        results.any { it }
    }

    suspend fun forceStop(packageName: String): Boolean = withContext(Dispatchers.IO) {
        runAsRoot("am force-stop --user 0 $packageName")
    }

    /**
     * Advanced force-stop: Chains killing, background restriction, and standby bucket
     * into a single shell session for maximum speed.
     */
    suspend fun forceStopAdvanced(packageName: String): Boolean = withContext(Dispatchers.IO) {
        val cmd = "am force-stop --user 0 $packageName; " +
                  "cmd appops set $packageName RUN_IN_BACKGROUND ignore; " +
                  "cmd appops set $packageName RUN_ANY_IN_BACKGROUND ignore; " +
                  "cmd appops set $packageName 63 1; " +
                  "dumpsys battery unplug; am set-standby-bucket $packageName 45 --user 0; dumpsys battery reset"
        runAsRoot(cmd)
    }

    suspend fun disable(packageName: String): Boolean = withContext(Dispatchers.IO) {
        val cmd = "am force-stop --user 0 $packageName; " +
                  "cmd package disable-user --user 0 $packageName; " +
                  "pm disable-user --user 0 $packageName; " +
                  "cmd package hide --user 0 $packageName; " +
                  "pm hide --user 0 $packageName; " +
                  "pm uninstall -k --user 0 $packageName"
        runAsRoot(cmd)
    }

    suspend fun enable(packageName: String): Boolean = withContext(Dispatchers.IO) {
        val cmd = "pm install-existing --user 0 $packageName; cmd package enable --user 0 $packageName; pm unhide --user 0 $packageName"
        runAsRoot(cmd)
    }

    suspend fun suspend(packageName: String, suspended: Boolean): Boolean = withContext(Dispatchers.IO) {
        val cmd = if (suspended) {
            "am force-stop --user 0 $packageName; cmd package suspend --user 0 $packageName; pm suspend --user 0 $packageName"
        } else {
            "cmd package unsuspend --user 0 $packageName; pm unsuspend --user 0 $packageName; cmd package enable --user 0 $packageName"
        }
        runAsRoot(cmd)
    }

    suspend fun setAppOp(packageName: String, op: Int, mode: Int): Boolean = withContext(Dispatchers.IO) {
        val modeStr = if (mode == 0) "allow" else "ignore"
        val results = runAsRootChain(listOf(
            "cmd appops set $packageName RUN_IN_BACKGROUND $modeStr",
            "cmd appops set $packageName RUN_ANY_IN_BACKGROUND $modeStr",
            "cmd appops set $packageName 63 $mode",
            "cmd appops set $packageName 70 $mode"
        ))
        results.any { it }
    }

    suspend fun setStandbyBucket(packageName: String, bucket: Int): Boolean = withContext(Dispatchers.IO) {
        runAsRoot("dumpsys battery unplug; am set-standby-bucket $packageName $bucket --user 0; am set-standby-bucket $packageName $bucket; dumpsys battery reset")
    }

    suspend fun getStandbyBucket(packageName: String): Int = withContext(Dispatchers.IO) {
        val output = runAsRootWithOutput("am get-standby-bucket $packageName --user 0") ?: 
                     runAsRootWithOutput("am get-standby-bucket $packageName") ?: ""
        
        try {
            val regex = Regex("(\\d+)")
            val match = regex.find(output)
            match?.groupValues?.get(1)?.toInt() ?: 10
        } catch (_: Throwable) {
            10
        }
    }

    suspend fun clearCache(packageName: String): Boolean = withContext(Dispatchers.IO) {
        runAsRoot("pm trim-caches 999999999999 && pm clear --user 0 $packageName")
    }

    suspend fun uninstall(packageName: String): Boolean = withContext(Dispatchers.IO) {
        var output = runAsRootWithOutput("pm uninstall --user 0 $packageName") ?: ""
        if (output.contains("Success", ignoreCase = true)) return@withContext true
        
        output = runAsRootWithOutput("pm uninstall -k --user 0 $packageName") ?: ""
        if (output.contains("Success", ignoreCase = true)) return@withContext true
        
        val hideCmd = "pm disable-user --user 0 $packageName; pm hide --user 0 $packageName; pm uninstall -k --user 0 $packageName; am force-stop --user 0 $packageName"
        runAsRoot(hideCmd)
    }

    suspend fun runShell(command: String): Boolean = withContext(Dispatchers.IO) {
        runAsRoot(command)
    }

    suspend fun runShellWithOutput(command: String): String? = withContext(Dispatchers.IO) {
        runAsRootWithOutput(command)
    }

    suspend fun setBatteryOptimizationExempt(packageName: String, exempt: Boolean): Boolean = withContext(Dispatchers.IO) {
        val op = if (exempt) "+" else "-"
        runAsRoot("cmd deviceidle whitelist $op$packageName")
    }

    private fun runAsRoot(command: String): Boolean {
        return try {
            val process = ProcessBuilder("su").redirectErrorStream(true).start()
            DataOutputStream(process.outputStream).use { os ->
                os.writeBytes("$command\n")
                os.writeBytes("exit\n")
                os.flush()
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                if (!process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)) {
                    process.destroyForcibly()
                    return false
                }
                process.exitValue() == 0
            } else {
                process.waitFor() == 0
            }
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Runs several commands in a single `su` session but captures each command's own exit
     * code, instead of the chain-wide exit code (which is just whatever the LAST command
     * happened to return). Returns one Boolean per input command, in order.
     */
    private fun runAsRootChain(commands: List<String>): List<Boolean> {
        return try {
            val process = ProcessBuilder("su").redirectErrorStream(true).start()
            val script = buildString {
                commands.forEachIndexed { i, cmd ->
                    append(cmd).append('\n')
                    append("echo __RC_$i:$?\n")
                }
                append("exit\n")
            }
            DataOutputStream(process.outputStream).use { os ->
                os.writeBytes(script)
                os.flush()
            }
            val output = process.inputStream.bufferedReader().readText()
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                if (!process.waitFor(15, java.util.concurrent.TimeUnit.SECONDS)) {
                    process.destroyForcibly()
                    return commands.map { false }
                }
            } else {
                process.waitFor()
            }
            commands.indices.map { i ->
                Regex("__RC_$i:(\\d+)").find(output)?.groupValues?.get(1) == "0"
            }
        } catch (_: Throwable) {
            commands.map { false }
        }
    }

    private fun runAsRootWithOutput(command: String): String? {
        return try {
            val process = ProcessBuilder("su").redirectErrorStream(true).start()
            DataOutputStream(process.outputStream).use { os ->
                os.writeBytes("$command\n")
                os.writeBytes("exit\n")
                os.flush()
            }
            val output = process.inputStream.bufferedReader().readText()
            val exitedZero = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                if (!process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)) {
                    process.destroyForcibly()
                    return null
                }
                process.exitValue() == 0
            } else {
                process.waitFor() == 0
            }
            if (exitedZero) output else null
        } catch (_: Throwable) {
            null
        }
    }
}
