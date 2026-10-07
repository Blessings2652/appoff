@file:Suppress("DEPRECATION")

package com.theblacksheep.appoff.core

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import androidx.core.net.toUri

enum class DisableSafetyLevel {
    SAFE,       // Safe to disable / uninstall (User app)
    CAUTION,    // Caution - Pre-installed system app
    DANGER      // Danger - Critical system component
}

data class CleanableApp(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
    val isSystem: Boolean,
    val lastUsedMillis: Long,
    val isFrozen: Boolean = false,
    val isHidden: Boolean = false,
    val standbyBucket: Int = 10,
    val isRestricted: Boolean = false,
    val isInactive: Boolean = false,
    val isBackgroundRestricted: Boolean = false,
    val isDisabled: Boolean = false,
    val isSuspended: Boolean = false,
    val isStopped: Boolean = false,
    val isUninstalled: Boolean = false,
    val isRunning: Boolean = false,
    val importance: Int = 1000,
    val memoryUsageBytes: Long = 0L,
    val is64Bit: Boolean = true,
    val disableSafetyLevel: DisableSafetyLevel = DisableSafetyLevel.SAFE,
    var selected: Boolean = true
)

/**
 * Default whitelist of packages we never suggest killing.
 */
val PROTECTED_PACKAGES = setOf(
    "com.theblacksheep.appoff",
    "com.android.systemui",
    "com.android.settings",
    "com.google.android.gms",
    "com.google.android.gsf",
    "rikka.shizuku",
    "moe.shizuku.privileged.api"
)

val CRITICAL_SYSTEM_PACKAGES = setOf(
    "android",
    "com.theblacksheep.appoff",
    "com.android.systemui",
    "com.android.settings",
    "com.google.android.gms",
    "com.google.android.gsf",
    "com.android.phone",
    "com.android.providers.telephony",
    "com.android.server.telecom",
    "com.google.android.webview",
    "com.android.webview",
    "com.android.vending",
    "com.android.shell",
    "com.android.keyguard",
    "com.android.packageinstaller",
    "com.google.android.packageinstaller",
    "com.android.permissioncontroller",
    "com.google.android.permissioncontroller",
    "com.android.bluetooth",
    "com.android.nfc",
    "com.android.emergency",
    "rikka.shizuku",
    "moe.shizuku.privileged.api"
)

object AppRepository {

    // ── Icon & Label LRU caches: avoids re-decoding / querying PM on every refresh ──────────
    private val iconCache = LruCache<String, Drawable>(256)
    private val labelCache = LruCache<String, String>(256)

    // ── Throttle for refreshDisabledApps / refreshSuspendedApps ─────────────
    @Volatile private var lastRefreshDisabledMs = 0L
    @Volatile private var lastRefreshSuspendedMs = 0L
    private const val REFRESH_THROTTLE_MS = 30_000L

    /** Icon if it is already decoded - never touches PackageManager. */
    fun cachedIcon(packageName: String): Drawable? = iconCache.get(packageName)

    /**
     * Loads (and caches) an app icon off the main thread. The list no longer decodes hundreds of
     * icons up-front; rows ask for their own icon when they scroll into view.
     */
    suspend fun loadIcon(context: Context, packageName: String): Drawable? {
        iconCache.get(packageName)?.let { return it }
        return withContext(Dispatchers.IO) {
            val pm = context.packageManager
            try {
                pm.getApplicationIcon(packageName)
            } catch (_: Exception) {
                try {
                    pm.getApplicationIcon(pm.getApplicationInfo(packageName, PackageManager.MATCH_UNINSTALLED_PACKAGES))
                } catch (_: Exception) { null }
            }?.also { iconCache.put(packageName, it) }
        }
    }

    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        @Suppress("DEPRECATION")
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun writeSettingsIntent(context: Context) = android.content.Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
        data = "package:${context.packageName}".toUri()
    }

    /**
     * Best-effort list of "cleanable" apps.
     *
     * PERFORMANCE: All per-package IPC calls are batched into maps BEFORE the
     * filter/map sequence, so each Binder call happens once total instead of
     * once per installed package.
     */
    suspend fun getCleanableApps(
        context: Context,
        includeSystem: Boolean,
        extraWhitelist: Set<String>,
        requireUsage: Boolean = false,
        runningPackages: Set<String> = emptySet(),
        packageMemoryUsage: Map<String, Long> = emptyMap()
    ): List<CleanableApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val filterWhitelist = if (includeSystem) setOf("com.theblacksheep.appoff", "rikka.shizuku", "moe.shizuku.privileged.api")
                              else PROTECTED_PACKAGES

        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

        // ── 1. Usage stats (single IPC call) ────────────────────────────────
        val recentUsage: Map<String, Long> = try {
            val end = System.currentTimeMillis()
            val start = end - 1000L * 60 * 60 * 24 * 7
            usm.queryUsageStats(UsageStatsManager.INTERVAL_BEST, start, end)
                .associate { it.packageName to it.lastTimeUsed }
        } catch (_: Exception) {
            emptyMap()
        }

        val flags = PackageManager.GET_META_DATA or
                    PackageManager.MATCH_DISABLED_COMPONENTS or
                    PackageManager.MATCH_UNINSTALLED_PACKAGES or
                    0x02000000 // MATCH_KNOWN_PACKAGES
        val installed = pm.getInstalledApplications(flags)

        // ── 2. Running processes — fetched ONCE here, reused everywhere ──────
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val runningProcs: List<android.app.ActivityManager.RunningAppProcessInfo> =
            try { am.runningAppProcesses ?: emptyList() } catch (_: Exception) { emptyList() }

        // Build a fast set of running package names from runningProcs
        val runningProcPackages: Set<String> = buildSet {
            for (proc in runningProcs) {
                proc.pkgList?.forEach { add(it) }
            }
        }
        // pkg -> best (lowest) importance, built once instead of a linear search per app
        val importanceMap = HashMap<String, Int>(runningProcs.size * 2)
        for (proc in runningProcs) {
            proc.pkgList?.forEach { p ->
                val cur = importanceMap[p]
                if (cur == null || proc.importance < cur) importanceMap[p] = proc.importance
            }
        }

        // ── 3. Bulk-pre-load all per-package data in a single pass ────────────────────────────
        val labelMap = HashMap<String, String>(installed.size)
        val enabledStateMap = HashMap<String, Int>(installed.size)
        val standbyBucketMap = HashMap<String, Int>(installed.size)
        val inactiveMap = HashMap<String, Boolean>(installed.size)
        val bgRestrictedMap = HashMap<String, Boolean>(installed.size)
        val suspendedMap = HashMap<String, Boolean>(installed.size)

        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val standbyMethod = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try { usm.javaClass.getMethod("getAppStandbyBucket", String::class.java) }
            catch (_: Exception) { null }
        } else null
        val suspendedMethod = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try { pm.javaClass.getMethod("isPackageSuspended", String::class.java) }
            catch (_: Exception) { null }
        } else null

        // Each package costs ~6 Binder calls. Doing 300-600 packages one after another was the main
        // reason the list felt slow, so the work is fanned out over a few workers.
        class PkgInfo(
            val pkg: String, val label: String, val enabled: Int?, val bucket: Int?,
            val inactive: Boolean, val bgRestricted: Boolean, val suspended: Boolean
        )

        val workers = 6
        val chunkSize = installed.size / workers + 1
        val infos: List<PkgInfo> = coroutineScope {
            installed.chunked(chunkSize).map { chunk ->
                async(Dispatchers.IO) {
                    chunk.map { app ->
                        val pkg = app.packageName

                        val label = labelCache.get(pkg) ?: run {
                            val l = try { pm.getApplicationLabel(app).toString() } catch (_: Exception) { pkg }
                            labelCache.put(pkg, l)
                            l
                        }

                        val enabled = try { pm.getApplicationEnabledSetting(pkg) } catch (_: Exception) { null }

                        val bucket = if (standbyMethod != null) {
                            try { standbyMethod.invoke(usm, pkg) as? Int ?: 10 } catch (_: Exception) { 10 }
                        } else null

                        val inactive = try { usm.isAppInactive(pkg) } catch (_: Exception) { false }

                        val bgRestricted = try {
                            val m1 = appOps.checkOpNoThrow("android:run_in_background", app.uid, pkg)
                            val m2 = appOps.checkOpNoThrow("android:run_any_in_background", app.uid, pkg)
                            m1 == AppOpsManager.MODE_IGNORED || m1 == AppOpsManager.MODE_ERRORED ||
                                m2 == AppOpsManager.MODE_IGNORED || m2 == AppOpsManager.MODE_ERRORED
                        } catch (_: Exception) { false }

                        val byReflection = if (suspendedMethod != null) {
                            try { suspendedMethod.invoke(pm, pkg) as? Boolean ?: false } catch (_: Exception) { false }
                        } else false
                        val byFlag = (app.flags and ApplicationInfo.FLAG_SUSPENDED) != 0

                        PkgInfo(pkg, label, enabled, bucket, inactive, bgRestricted, byReflection || byFlag)
                    }
                }
            }.awaitAll().flatten()
        }

        for (i in infos) {
            labelMap[i.pkg] = i.label
            i.enabled?.let { enabledStateMap[i.pkg] = it }
            i.bucket?.let { standbyBucketMap[i.pkg] = it }
            inactiveMap[i.pkg] = i.inactive
            bgRestrictedMap[i.pkg] = i.bgRestricted
            suspendedMap[i.pkg] = i.suspended
        }

        // ── 4. Build the app list using pre-loaded maps ──────────────────────
        installed
            .asSequence()
            .filter { it.packageName !in filterWhitelist }
            .filter { isSystemApp(it) }
            // Use labelMap instead of calling pm.getApplicationLabel again
            .filter { labelMap[it.packageName] != it.packageName }
            .filter { !requireUsage || recentUsage.containsKey(it.packageName) || isActuallyFrozenFast(enabledStateMap, suspendedMap, it) }
            .map { appInfo ->
                val isWhitelisted = extraWhitelist.contains(appInfo.packageName)
                val enabledState = enabledStateMap[appInfo.packageName]
                val isDisabled = enabledState == PackageManager.COMPONENT_ENABLED_STATE_DISABLED ||
                                 enabledState == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER ||
                                 enabledState == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED

                createCleanableApp(
                    pm = pm,
                    appInfo = appInfo,
                    label = labelMap[appInfo.packageName] ?: appInfo.packageName,
                    lastUsed = recentUsage[appInfo.packageName] ?: 0L,
                    isRunningExplicit = runningPackages.contains(appInfo.packageName) || runningProcPackages.contains(appInfo.packageName),
                    memoryUsageExplicit = packageMemoryUsage[appInfo.packageName] ?: 0L,
                    runningProcs = runningProcs,
                    runningProcPackages = runningProcPackages,
                    isDisabled = isDisabled,
                    isSuspended = suspendedMap[appInfo.packageName] ?: false,
                    standbyBucket = standbyBucketMap[appInfo.packageName] ?: 10,
                    isInactive = inactiveMap[appInfo.packageName] ?: false,
                    isBackgroundRestricted = bgRestrictedMap[appInfo.packageName] ?: false,
                    enabledStateMap = enabledStateMap,
                    suspendedMap = suspendedMap,
                    importanceMap = importanceMap,
                    eagerIcon = false
                ).copy(selected = !isWhitelisted && !isDisabled)
            }
            .sortedByDescending { it.lastUsedMillis }
            .toList()
    }

    /**
     * Fast frozen check using pre-loaded maps — no additional IPC.
     */
    private fun isActuallyFrozenFast(
        enabledStateMap: Map<String, Int>,
        suspendedMap: Map<String, Boolean>,
        appInfo: ApplicationInfo
    ): Boolean {
        val enabledState = enabledStateMap[appInfo.packageName]
        val isDisabled = enabledState == PackageManager.COMPONENT_ENABLED_STATE_DISABLED ||
                         enabledState == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER ||
                         enabledState == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED
        val isSuspended = suspendedMap[appInfo.packageName] ?: false
        val isHidden = (appInfo.flags and ApplicationInfo.FLAG_INSTALLED) == 0
        return isDisabled || isSuspended || isHidden
    }

    private fun createCleanableApp(
        pm: PackageManager,
        appInfo: ApplicationInfo,
        label: String,
        lastUsed: Long,
        isRunningExplicit: Boolean = false,
        memoryUsageExplicit: Long = 0L,
        runningProcs: List<android.app.ActivityManager.RunningAppProcessInfo>? = null,
        runningProcPackages: Set<String> = emptySet(),
        isDisabled: Boolean,
        isSuspended: Boolean,
        standbyBucket: Int,
        isInactive: Boolean,
        isBackgroundRestricted: Boolean,
        enabledStateMap: Map<String, Int>,
        suspendedMap: Map<String, Boolean>,
        importanceMap: Map<String, Int>? = null,
        eagerIcon: Boolean = true
    ): CleanableApp {
        val packageName = appInfo.packageName

        var importance = 1000
        var isRunning = isRunningExplicit || runningProcPackages.contains(packageName) || memoryUsageExplicit > 0
        if (importanceMap != null) {
            importanceMap[packageName]?.let { importance = it; isRunning = true }
        } else {
            try {
                val procInfo = runningProcs?.find { it.pkgList.contains(packageName) }
                importance = procInfo?.importance ?: 1000
                if (procInfo != null) isRunning = true
            } catch (_: Exception) {}
        }

        // Icon: the big list passes eagerIcon=false and only reuses what is already cached -
        // rows load their own icon lazily (see rememberAppIcon). Single-app lookups stay eager.
        val icon: Drawable? = iconCache.get(packageName) ?: if (eagerIcon) try {
            pm.getApplicationIcon(appInfo).also { iconCache.put(packageName, it) }
        } catch (_: Exception) { null } else null

        val isFrozen = isActuallyFrozenFast(enabledStateMap, suspendedMap, appInfo)
        val isHidden = (appInfo.flags and ApplicationInfo.FLAG_INSTALLED) == 0

        // isStopped: use pre-fetched runningProcPackages, no extra IPC
        val isFlagStopped = (appInfo.flags and ApplicationInfo.FLAG_STOPPED) != 0
        val isStopped = isFlagStopped && !runningProcPackages.contains(packageName)

        val isUninstalled = (appInfo.flags and ApplicationInfo.FLAG_INSTALLED) == 0

        val isRestricted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            standbyBucket >= 45
        } else false

        val isSystem = isSystemApp(appInfo)
        val isPersistent = (appInfo.flags and ApplicationInfo.FLAG_PERSISTENT) != 0
        val is64Bit = computeIs64Bit(appInfo)
        val disableSafetyLevel = calculateDisableSafetyLevel(packageName, isSystem, isPersistent)

        return CleanableApp(
            packageName = packageName,
            label = label,
            icon = icon,
            isSystem = isSystem,
            lastUsedMillis = lastUsed,
            isFrozen = isFrozen,
            isHidden = isHidden,
            standbyBucket = standbyBucket,
            isRestricted = isRestricted,
            isInactive = isInactive,
            isBackgroundRestricted = isBackgroundRestricted,
            isDisabled = isDisabled,
            isSuspended = isSuspended,
            isStopped = isStopped,
            isUninstalled = isUninstalled,
            isRunning = isRunning,
            importance = importance,
            memoryUsageBytes = memoryUsageExplicit,
            is64Bit = is64Bit,
            disableSafetyLevel = disableSafetyLevel
        )
    }

    private fun computeIs64Bit(appInfo: ApplicationInfo): Boolean {
        val primaryCpuAbi = try {
            val field = appInfo.javaClass.getField("primaryCpuAbi")
            field.get(appInfo) as? String
        } catch (_: Exception) {
            null
        }
        return when {
            !primaryCpuAbi.isNullOrEmpty() -> primaryCpuAbi.contains("64")
            appInfo.nativeLibraryDir?.let { it.contains("64") || it.contains("arm64") || it.contains("x86_64") } == true -> true
            else -> Process.is64Bit()
        }
    }

    private fun calculateDisableSafetyLevel(packageName: String, isSystem: Boolean, isPersistent: Boolean): DisableSafetyLevel {
        return when {
            packageName in CRITICAL_SYSTEM_PACKAGES || packageName in PROTECTED_PACKAGES -> DisableSafetyLevel.DANGER
            isSystem && isPersistent -> DisableSafetyLevel.DANGER
            isSystem -> DisableSafetyLevel.CAUTION
            else -> DisableSafetyLevel.SAFE
        }
    }

    private fun isActuallyUninstalled(pm: PackageManager, packageName: String): Boolean {
        return try {
            val info = pm.getApplicationInfo(packageName, PackageManager.MATCH_UNINSTALLED_PACKAGES or 0x02000000)
            (info.flags and ApplicationInfo.FLAG_INSTALLED) == 0
        } catch (_: Exception) {
            true
        }
    }

    private fun isActuallyInactive(usm: UsageStatsManager, packageName: String): Boolean {
        return try {
            usm.isAppInactive(packageName)
        } catch (_: Exception) {
            false
        }
    }

    private fun isActuallyBackgroundRestricted(context: Context, packageName: String, uid: Int): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode1 = appOps.checkOpNoThrow("android:run_in_background", uid, packageName)
            val mode2 = appOps.checkOpNoThrow("android:run_any_in_background", uid, packageName)
            mode1 == AppOpsManager.MODE_IGNORED || mode1 == AppOpsManager.MODE_ERRORED ||
            mode2 == AppOpsManager.MODE_IGNORED || mode2 == AppOpsManager.MODE_ERRORED
        } catch (_: Exception) {
            false
        }
    }

    private fun isActuallyRestrictedStandby(usm: UsageStatsManager, packageName: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val bucket = getStandbyBucket(usm, packageName)
                bucket >= 45
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun isActuallyHidden(pm: PackageManager, packageName: String, appInfo: ApplicationInfo? = null): Boolean {
        return try {
            val info = appInfo ?: pm.getApplicationInfo(packageName, PackageManager.MATCH_UNINSTALLED_PACKAGES or 0x02000000)
            (info.flags and ApplicationInfo.FLAG_INSTALLED) == 0
        } catch (_: Exception) {
            false
        }
    }

    private fun isActuallyFrozen(pm: PackageManager, packageName: String, appInfo: ApplicationInfo? = null): Boolean {
        return try {
            val info = appInfo ?: pm.getApplicationInfo(packageName, PackageManager.MATCH_DISABLED_COMPONENTS or PackageManager.MATCH_UNINSTALLED_PACKAGES or 0x02000000)
            val isPkgDisabled = isActuallyDisabled(pm, packageName)
            val isHidden = isActuallyHidden(pm, packageName, info)
            val isSuspended = isActuallySuspended(pm, packageName, info)
            isPkgDisabled || isSuspended || isHidden
        } catch (_: Exception) {
            false
        }
    }

    private fun isActuallyStopped(context: Context, appInfo: ApplicationInfo): Boolean {
        val isFlagStopped = (appInfo.flags and ApplicationInfo.FLAG_STOPPED) != 0
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val isRunning = am.runningAppProcesses?.any { it.pkgList.contains(appInfo.packageName) } == true
        return isFlagStopped && !isRunning
    }

    private fun isActuallyDisabled(pm: PackageManager, packageName: String): Boolean {
        return try {
            val state = pm.getApplicationEnabledSetting(packageName)
            state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED ||
            state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER ||
            state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Cheap "is this package frozen right now" check (disabled / suspended / hidden) - a few
     * PackageManager binder calls, no usage-stats query and no full app-list load. Used to
     * verify freeze/unfreeze/disable/suspend actions instead of rebuilding the whole app list.
     */
    fun isPackageFrozenNow(context: Context, packageName: String): Boolean {
        return isActuallyFrozen(context.packageManager, packageName)
    }

    suspend fun getAppByPackageName(context: Context, packageName: String): CleanableApp? = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        try {
            val appInfo = pm.getApplicationInfo(packageName, PackageManager.MATCH_DISABLED_COMPONENTS or PackageManager.MATCH_UNINSTALLED_PACKAGES or 0x02000000)

            val end = System.currentTimeMillis()
            val start = end - 1000L * 60 * 60 * 24
            val lastUsed = try {
                usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end)
                    ?.find { it.packageName == packageName }?.lastTimeUsed ?: 0L
            } catch (_: Exception) { 0L }

            val label = try { pm.getApplicationLabel(appInfo).toString() } catch (_: Exception) { packageName }
            val enabledState = try { pm.getApplicationEnabledSetting(packageName) } catch (_: Exception) { PackageManager.COMPONENT_ENABLED_STATE_DEFAULT }
            val isDisabled = enabledState == PackageManager.COMPONENT_ENABLED_STATE_DISABLED ||
                             enabledState == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER ||
                             enabledState == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED

            val suspendedMethod = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                try { pm.javaClass.getMethod("isPackageSuspended", String::class.java) } catch (_: Exception) { null }
            } else null
            val byReflection = if (suspendedMethod != null) {
                try { suspendedMethod.invoke(pm, packageName) as? Boolean ?: false } catch (_: Exception) { false }
            } else false
            val isSuspended = byReflection || (appInfo.flags and ApplicationInfo.FLAG_SUSPENDED) != 0

            val standbyMethod = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                try { usm.javaClass.getMethod("getAppStandbyBucket", String::class.java) } catch (_: Exception) { null }
            } else null
            val standbyBucket = try { standbyMethod?.invoke(usm, packageName) as? Int ?: 10 } catch (_: Exception) { 10 }
            val isInactive = try { usm.isAppInactive(packageName) } catch (_: Exception) { false }
            val isBackgroundRestricted = isActuallyBackgroundRestricted(context, packageName, appInfo.uid)

            val enabledStateMap = mapOf(packageName to enabledState)
            val suspendedMap = mapOf(packageName to isSuspended)

            createCleanableApp(
                pm = pm,
                appInfo = appInfo,
                label = label,
                lastUsed = lastUsed,
                isDisabled = isDisabled,
                isSuspended = isSuspended,
                standbyBucket = standbyBucket,
                isInactive = isInactive,
                isBackgroundRestricted = isBackgroundRestricted,
                enabledStateMap = enabledStateMap,
                suspendedMap = suspendedMap
            )
        } catch (_: Exception) {
            null
        }
    }

    suspend fun getAllFrozenPackages(context: Context): List<String> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val flags = PackageManager.MATCH_DISABLED_COMPONENTS or
                    PackageManager.MATCH_UNINSTALLED_PACKAGES or
                    0x02000000 // MATCH_KNOWN_PACKAGES

        pm.getInstalledApplications(flags)
            .asSequence()
            .filter { isActuallyFrozen(pm, it.packageName, it) }
            .map { it.packageName }
            .toList()
    }

    suspend fun getAppsByPackageNames(context: Context, packages: List<String>): List<CleanableApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val flags = PackageManager.MATCH_DISABLED_COMPONENTS or
                    PackageManager.MATCH_UNINSTALLED_PACKAGES or
                    0x00400000 // PackageManager.MATCH_ANY_USER

        packages.mapNotNull { pkg ->
            try {
                val appInfo = pm.getApplicationInfo(pkg, flags)
                val label = try { pm.getApplicationLabel(appInfo).toString() } catch (_: Exception) { pkg }
                val enabledState = try { pm.getApplicationEnabledSetting(pkg) } catch (_: Exception) { PackageManager.COMPONENT_ENABLED_STATE_DEFAULT }
                val isDisabled = enabledState == PackageManager.COMPONENT_ENABLED_STATE_DISABLED ||
                                 enabledState == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER ||
                                 enabledState == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED
                val isSuspended = (appInfo.flags and ApplicationInfo.FLAG_SUSPENDED) != 0
                val enabledStateMap = mapOf(pkg to enabledState)
                val suspendedMap = mapOf(pkg to isSuspended)
                createCleanableApp(
                    pm = pm, appInfo = appInfo, label = label, lastUsed = 0L,
                    isDisabled = isDisabled, isSuspended = isSuspended,
                    standbyBucket = 10, isInactive = false, isBackgroundRestricted = false,
                    enabledStateMap = enabledStateMap, suspendedMap = suspendedMap
                )
            } catch (_: Exception) { null }
        }
    }

    suspend fun getTopDrainers(context: Context): List<CleanableApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        val start = end - 1000L * 60 * 60 * 24

        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end)

        stats.asSequence()
            .filter { it.packageName !in PROTECTED_PACKAGES }
            .filter { it.totalTimeInForeground < 1000L * 60 * 10 }
            .sortedByDescending { it.lastTimeUsed }
            .take(6)
            .mapNotNull { stat ->
                try {
                    val appInfo = pm.getApplicationInfo(stat.packageName, PackageManager.MATCH_DISABLED_COMPONENTS or 0x02000000)
                    if (!isSystemApp(appInfo)) return@mapNotNull null
                    val label = try { pm.getApplicationLabel(appInfo).toString() } catch (_: Exception) { stat.packageName }
                    val enabledState = try { pm.getApplicationEnabledSetting(stat.packageName) } catch (_: Exception) { PackageManager.COMPONENT_ENABLED_STATE_DEFAULT }
                    val isDisabled = enabledState == PackageManager.COMPONENT_ENABLED_STATE_DISABLED ||
                                     enabledState == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER ||
                                     enabledState == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED
                    val isSuspended = (appInfo.flags and ApplicationInfo.FLAG_SUSPENDED) != 0
                    createCleanableApp(
                        pm = pm, appInfo = appInfo, label = label, lastUsed = stat.lastTimeUsed,
                        isDisabled = isDisabled, isSuspended = isSuspended, standbyBucket = 10,
                        isInactive = false, isBackgroundRestricted = false,
                        enabledStateMap = mapOf(stat.packageName to enabledState),
                        suspendedMap = mapOf(stat.packageName to isSuspended)
                    )
                } catch (_: Exception) { null }
            }
            .toList()
    }

    /**
     * Finds installed games using system metadata and advanced pattern matching.
     */
    suspend fun getInstalledGames(context: Context): List<CleanableApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val flags = PackageManager.GET_META_DATA or PackageManager.MATCH_DISABLED_COMPONENTS
        val installed = pm.getInstalledApplications(flags)
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

        val gameKeywords = listOf("game", "ninja", "pubg", "freefire", "codm", "Roblox", "Minecraft", "unity", "unreal", "supercell", "battle", "war", "arena", "clash")
        val blacklist = setOf(
            "com.google.android.apps.paidtasks",
            "com.epicgames.portal",
            "com.valvesoftware.android.steam.community",
            "com.nvidia.geforcenow",
            "com.android.vending",
            "com.gsmarena.android",
            "com.miui.enbbs",
            "com.theblacksheep.appoff",
            "com.sec.android.app.myfiles",
            "com.android.documentsui",
            "com.mi.android.globalFileexplorer",
            "com.google.android.apps.nbu.files",
            "com.gamekiller",
            "com.g19mobile.gamebooster",
            "com.turbomax.pro"
        )

        val userGames = SettingsRepository.getUserGames(context)
        val hiddenGames = SettingsRepository.getHiddenGames(context)

        // Pre-build label map for game detection
        val labelMap: Map<String, String> = buildMap {
            for (app in installed) {
                put(app.packageName, try { pm.getApplicationLabel(app).toString() } catch (_: Exception) { app.packageName })
            }
        }

        installed.asSequence()
            .filter { it.packageName !in blacklist && it.packageName !in hiddenGames }
            .filter { info ->
                val isCategorizedAsGame = info.category == ApplicationInfo.CATEGORY_GAME
                val label = labelMap[info.packageName]?.lowercase() ?: ""
                val pkg = info.packageName.lowercase()
                val isStoreOrTool = pkg.contains("market") || pkg.contains("store") || pkg.contains("community") || pkg.contains("booster") || label.contains("booster")
                val hasGameKeyword = gameKeywords.any { label.contains(it) || pkg.contains(it) }
                val isUnity = pkg.contains("unity") || label.contains("unity")
                val isManuallyAdded = pkg in userGames
                (isCategorizedAsGame || (hasGameKeyword && !isStoreOrTool)) || isUnity || isManuallyAdded
            }
            .filter { it.packageName !in PROTECTED_PACKAGES }
            .map { appInfo ->
                val label = labelMap[appInfo.packageName] ?: appInfo.packageName
                val enabledState = try { pm.getApplicationEnabledSetting(appInfo.packageName) } catch (_: Exception) { PackageManager.COMPONENT_ENABLED_STATE_DEFAULT }
                val isDisabled = enabledState == PackageManager.COMPONENT_ENABLED_STATE_DISABLED ||
                                 enabledState == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER ||
                                 enabledState == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED
                val isSuspended = (appInfo.flags and ApplicationInfo.FLAG_SUSPENDED) != 0
                createCleanableApp(
                    pm = pm, appInfo = appInfo, label = label, lastUsed = 0L,
                    isDisabled = isDisabled, isSuspended = isSuspended, standbyBucket = 10,
                    isInactive = false, isBackgroundRestricted = false,
                    enabledStateMap = mapOf(appInfo.packageName to enabledState),
                    suspendedMap = mapOf(appInfo.packageName to isSuspended)
                )
            }
            .toList()
    }

    fun getForegroundPackage(context: Context): String? {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return null
        val time = System.currentTimeMillis()
        val stats = try {
            usm.queryUsageStats(UsageStatsManager.INTERVAL_BEST, time - 10000, time)
        } catch (_: Exception) {
            null
        }

        if (stats.isNullOrEmpty()) {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
            return try {
                am?.runningAppProcesses?.firstOrNull {
                    it.importance == android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
                }?.pkgList?.firstOrNull()
            } catch (_: Exception) {
                null
            }
        }

        return stats.maxByOrNull { it.lastTimeUsed }?.packageName
    }

    private fun getStandbyBucket(usm: UsageStatsManager, packageName: String): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return 10

        try {
            val method = usm.javaClass.getMethod("getAppStandbyBucket", String::class.java)
            return method.invoke(usm, packageName) as Int
        } catch (_: Exception) {}

        try {
            val method = usm.javaClass.getMethod("getAppStandbyBucket", String::class.java, Int::class.javaPrimitiveType)
            return method.invoke(usm, packageName, 0) as Int
        } catch (_: Exception) {}

        return 10
    }

    private fun isActuallySuspended(pm: PackageManager, packageName: String, appInfo: ApplicationInfo? = null): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val method = pm.javaClass.getMethod("isPackageSuspended", String::class.java)
                if (method.invoke(pm, packageName) as Boolean) return true
            } catch (_: Exception) {}
        }

        return try {
            val info = appInfo ?: pm.getApplicationInfo(packageName, PackageManager.MATCH_DISABLED_COMPONENTS or 0x02000000)
            (info.flags and ApplicationInfo.FLAG_SUSPENDED) != 0
        } catch (_: Exception) {
            false
        }
    }

    fun isSystemApp(info: ApplicationInfo): Boolean {
        val isSystemFlag = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        val isUpdatedSystem = (info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        val sourceDir = info.sourceDir ?: ""
        val isPreinstalledPath = sourceDir.startsWith("/system") ||
                sourceDir.startsWith("/vendor") ||
                sourceDir.startsWith("/product") ||
                sourceDir.startsWith("/system_ext") ||
                sourceDir.startsWith("/odm") ||
                sourceDir.startsWith("/oem")
        val pkg = info.packageName ?: ""
        val isSystemPackage = pkg.startsWith("android") ||
                pkg.startsWith("com.android.") ||
                pkg.startsWith("com.google.android.") ||
                pkg.startsWith("com.miui.") ||
                pkg.startsWith("com.xiaomi.") ||
                pkg.startsWith("com.sec.android.") ||
                pkg.startsWith("com.samsung.")
        return isSystemFlag || isUpdatedSystem || isPreinstalledPath || isSystemPackage
    }

    /**
     * Throttled refresh of disabled apps — at most once per 30 seconds.
     * Avoids expensive Shizuku IPC on every ViewModel refresh().
     */
    suspend fun refreshDisabledAppsThrottled(context: Context) {
        val now = System.currentTimeMillis()
        if (now - lastRefreshDisabledMs < REFRESH_THROTTLE_MS) return
        lastRefreshDisabledMs = now
        SettingsRepository.refreshDisabledApps(context)
    }

    /**
     * Throttled refresh of suspended apps — at most once per 30 seconds.
     */
    suspend fun refreshSuspendedAppsThrottled(context: Context) {
        val now = System.currentTimeMillis()
        if (now - lastRefreshSuspendedMs < REFRESH_THROTTLE_MS) return
        lastRefreshSuspendedMs = now
        SettingsRepository.refreshSuspendedApps(context)
    }
}
