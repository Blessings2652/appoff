package com.theblacksheep.appoff.core

import android.app.ActivityManager
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

data class CleanResult(
    val attempted: Int,
    val succeeded: Int,
    val freedBytesEstimate: Long,
    val strategy: String,
    val succeededPackages: List<String> = emptyList(),
    val succeededLabels: List<String> = emptyList()
)

interface CleanStrategy {
    val name: String
    suspend fun clean(
        context: Context,
        packages: List<String>,
        cacheOnly: Boolean = false,
        onProgress: (String) -> Unit = {},
        onTaskUpdate: (String) -> Unit = {},
    ): CleanResult
}

/**
 * The only cleaning path that works with zero extra setup. Calls the standard
 * ActivityManager#killBackgroundProcesses for each target package. 
 * Enhanced with Memory Pressure technique to force the system's LMK.
 */
object StandardCleaner : CleanStrategy {
    override val name = "Standard"

    override suspend fun clean(
        context: Context,
        packages: List<String>,
        cacheOnly: Boolean,
        onProgress: (String) -> Unit,
        onTaskUpdate: (String) -> Unit,
    ): CleanResult = withContext(Dispatchers.IO) {
        val am = try { context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager } catch (_: Throwable) { null }
        val before = try { MemoryUtils.snapshot(context).availBytes } catch (_: Throwable) { 0L }
        
        val whitelist = try { SettingsRepository.getUserWhitelist(context) } catch (_: Throwable) { emptySet() }
        val filteredPackages = packages.filter { it !in whitelist && it !in PROTECTED_PACKAGES }

        // 1. Force LMK (Low Memory Killer) pressure to purge background caches
        try {
            onTaskUpdate("Triggering LMK RAM Pressure")
            triggerMemoryPressure(context)
        } catch (_: Throwable) {}

        val succeededPackages = mutableListOf<String>()
        // 2. Standard kill for selected packages (Skip if cacheOnly)
        if (!cacheOnly) {
            try { onTaskUpdate("Closing background processes") } catch (_: Throwable) {}
            
            // Parallelize standard kill: chunk of 25 as Binder calls are very fast
            filteredPackages.chunked(25).forEach { chunk ->
                val jobs = chunk.map { pkg ->
                    async {
                        try { onProgress(pkg) } catch (_: Throwable) {}
                        delay(20) // Very small delay to keep UI reactive but much faster
                        try {
                            if (am != null) {
                                am.killBackgroundProcesses(pkg)
                                pkg
                            } else {
                                null
                            }
                        } catch (_: Throwable) {
                            null
                        }
                    }
                }
                try {
                    succeededPackages.addAll(jobs.awaitAll().filterNotNull())
                } catch (_: Throwable) {}
            }
        } else {
            try { onTaskUpdate("RAM pressure optimization") } catch (_: Throwable) {}
            succeededPackages.addAll(filteredPackages)
        }

        val after = try { MemoryUtils.snapshot(context).availBytes } catch (_: Throwable) { before }
        CleanResult(
            attempted = packages.size,
            succeeded = succeededPackages.size,
            freedBytesEstimate = (after - before).coerceAtLeast(0),
            strategy = name,
            succeededPackages = succeededPackages
        )
    }
}

/**
 * Strategy that uses privileged system permissions (FORCE_STOP_PACKAGES, CLEAR_APP_CACHE).
 * These are available if the app is granted them via ADB or installed as a System App.
 */
object SystemCleaner : CleanStrategy {
    override val name = "System"

    override suspend fun clean(
        context: Context,
        packages: List<String>,
        cacheOnly: Boolean,
        onProgress: (String) -> Unit,
        onTaskUpdate: (String) -> Unit,
    ): CleanResult = withContext(Dispatchers.IO) {
        val am = try { context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager } catch (_: Throwable) { null }
        val pm = try { context.packageManager } catch (_: Throwable) { null }
        val before = try { MemoryUtils.snapshot(context).availBytes } catch (_: Throwable) { 0L }
        val succeededPackages = mutableListOf<String>()

        val whitelist = try { SettingsRepository.getUserWhitelist(context) } catch (_: Throwable) { emptySet() }
        val filteredPackages = packages.filter { it !in whitelist && it !in PROTECTED_PACKAGES }

        try {
            onTaskUpdate("Privileged RAM Purge")
            triggerMemoryPressure(context)
        } catch (_: Throwable) {}

        // 3. Optimize System Parameters (requires WRITE_SECURE_SETTINGS)
        if (hasSecureSettingsPermission(context)) {
            try {
                onTaskUpdate("Optimizing System Parameters")
                optimizeSystemSecurely(context)
            } catch (_: Throwable) {}
        }

        // Parallelize system clean: chunk of 15 for more complex privileged calls
        filteredPackages.chunked(15).forEach { chunk ->
            val jobs = chunk.map { pkg ->
                async {
                    try { onProgress(pkg) } catch (_: Throwable) {}
                    delay(30) // Optimized delay
                    var pkgSucceeded = false
                    
                    // 1. Try Force Stop (requires FORCE_STOP_PACKAGES) - Skip if cacheOnly
                    if (!cacheOnly) {
                        try {
                            if (am != null) {
                                val method = am.javaClass.getMethod("forceStopPackage", String::class.java)
                                method.invoke(am, pkg)
                                pkgSucceeded = true
                            }
                        } catch (_: Throwable) {
                            // Fallback to standard kill
                            try { 
                                if (am != null) {
                                    am.killBackgroundProcesses(pkg)
                                    pkgSucceeded = true
                                }
                            } catch (_: Throwable) {}
                        }
                    } else {
                        pkgSucceeded = true
                    }

                    // 2. Try Clear Cache (requires CLEAR_APP_CACHE)
                    try {
                        if (pm != null) {
                            val method = pm.javaClass.getMethod("deleteApplicationCacheFiles", String::class.java, Class.forName("android.content.pm.IPackageDataObserver"))
                            method.invoke(pm, pkg, null)
                        }
                    } catch (_: Throwable) {}

                    if (pkgSucceeded) pkg else null
                }
            }
            try {
                succeededPackages.addAll(jobs.awaitAll().filterNotNull())
            } catch (_: Throwable) {}
        }

        val after = try { MemoryUtils.snapshot(context).availBytes } catch (_: Throwable) { before }
        CleanResult(packages.size, succeededPackages.size, (after - before).coerceAtLeast(0), name, succeededPackages)
    }

    fun hasPrivilegedPermissions(context: Context): Boolean {
        return try {
            context.checkSelfPermission("android.permission.FORCE_STOP_PACKAGES") == android.content.pm.PackageManager.PERMISSION_GRANTED
        } catch (_: Throwable) {
            false
        }
    }

    private fun hasSecureSettingsPermission(context: Context): Boolean {
        return try {
            context.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == android.content.pm.PackageManager.PERMISSION_GRANTED
        } catch (_: Throwable) {
            false
        }
    }

    private fun optimizeSystemSecurely(context: Context) {
        try {
            android.provider.Settings.Global.putInt(context.contentResolver, "low_power_trigger_level", 0)
            android.provider.Settings.Global.putInt(context.contentResolver, "adaptive_battery_management_enabled", 1)
        } catch (_: Throwable) {}
    }
}

/**
 * Powerful technique to force Android's Low Memory Killer (LMK) to reclaim RAM.
 * By temporarily allocating a large block of memory, the system is forced to 
 * kill cached background processes that it would otherwise keep alive.
 */
fun triggerMemoryPressure(context: Context) {
    try {
        // Trigger native-level sync first
        NativeMemoryUtils.optimizeMemoryNative()

        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        
        // Try to pressure 70% of available RAM, but cap it at 512MB to prevent Int overflow 
        // and ensure the cleaner itself doesn't get killed by the system immediately.
        val targetPressure = (memInfo.availMem * 0.7).toLong()
        val pressureSize = targetPressure.coerceAtMost(512 * 1024 * 1024L) 
        
        if (pressureSize > 10 * 1024 * 1024) { // At least 10MB
            val pressureBuffer = java.nio.ByteBuffer.allocateDirect(pressureSize.toInt())
            // Touch the buffer to ensure it's actually mapped to physical RAM
            for (i in 0 until pressureSize.toInt() step 4096) {
                pressureBuffer.put(i, 0.toByte())
            }
            // Let the kernel react to the pressure
            // No sleep here - buffer release is handled by scope
        }
    } catch (_: Throwable) {
        // Fallback if allocation fails
    }
}
