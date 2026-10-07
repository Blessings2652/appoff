package com.theblacksheep.appoff.ui

import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.Application
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.Process
import android.os.StatFs
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.theblacksheep.appoff.FreezerHelper
import com.theblacksheep.appoff.R
import com.theblacksheep.appoff.core.*
import com.theblacksheep.appoff.root.RootCleaner
import com.theblacksheep.appoff.service.AppActionHandler
import com.theblacksheep.appoff.service.MaintenanceWorker
import com.theblacksheep.appoff.service.RamMonitorService
import com.theblacksheep.appoff.shizuku.ShizukuCleaner
import com.theblacksheep.appoff.shizuku.ShizukuHelper
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

data class DeviceInfo(
    val cpuName: String = "Unknown",
    val cpuCores: Int = 0,
    val batteryHealth: String = "Unknown",
    val batteryTemp: String = "0°C",
    val batteryLevel: Int = 0,
    val batteryVoltage: String = "0V",
    val batteryCurrent: String = "0mA",
    val storageUsed: String = "0 GB",
    val storageTotal: String = "0 GB",
    val storageFraction: Float = 0f,
    val ramCapacity: String = "---",
    val androidVersion: String = "",
    val securityPatch: String = "",
    val kernelVersion: String = "Unknown"
)

data class PermissionStatus(
    val id: String,
    val name: String,
    val isGranted: Boolean
)

data class ProcessAnalysisInfo(
    val name: String,
    val label: String = "",
    val pid: Int,
    val oomScore: Int,
    val adj: String,
    val memory: String = "Active",
    val memoryBytes: Long = 0L,
    val isSystemApp: Boolean = false,
    val packageName: String? = null
)

data class SensorData(
    val values: List<Float>
)

data class CleanerUiState(
    val currentScreen: Screen = Screen.SPLASH,
    val ram: RamSnapshot? = null,
    val apps: List<CleanableApp> = emptyList(),
    val junkItems: List<JunkItem> = emptyList(),
    val isScanningJunk: Boolean = false,
    val isRefreshing: Boolean = false,
    val totalJunkSize: Long = 0,
    val shellEngine: String = "Standard",
    val binderStatus: String = "Healthy",
    val includeSystemApps: Boolean = true,
    val hasUsageAccess: Boolean = false,
    val systemPrivileged: Boolean = false,
    val hasAllFilesAccess: Boolean = false,
    val canWriteSecureSettings: Boolean = false,
    val canWriteSettings: Boolean = false,
    val isBatteryOptimizationsIgnored: Boolean = false,
    val showPermissionInstructions: Boolean = false,
    val isShizukuInstalled: Boolean = false,
    val shizukuAvailable: Boolean = false,
    val shizukuGranted: Boolean = false,
    val rootAvailable: Boolean = false,
    val hasCompletedIntro: Boolean = false,
    val isHibernateAction: Boolean = false,
    /** True while permissions are being granted / tweaks applied right after access was given. */
    val isApplyingAccess: Boolean = false,
    /** Engine is chosen automatically (Root > Shizuku > System > Standard) - there is no manual picker. */
    val mode: CleanMode = CleanMode.STANDARD,
    val theme: AppTheme = AppTheme.DARK,
    val primaryColor: Int = 0xFF00E5A0.toInt(),
    val isCleaning: Boolean = false,
    val isCooling: Boolean = false,
    val cleaningPackage: String? = null,
    val cleaningLabel: String? = null,
    val cleaningTask: String? = null,
    val lastResult: CleanResult? = null,
    val autoCleanEnabled: Boolean = false,
    val aggressiveModeEnabled: Boolean = false,
    val hibernateScreenOff: Boolean = false,
    val fastAnimationsEnabled: Boolean = false,
    val hardwareAccelerationEnabled: Boolean = false,
    val hapticFeedbackEnabled: Boolean = false,
    val autoCleanNotificationsEnabled: Boolean = true,
    val autoKillBackgroundEnabled: Boolean = false,
    val autoKillInactiveEnabled: Boolean = false,
    val cleanOnBootEnabled: Boolean = false,
    val isFloatingBubbleEnabled: Boolean = false,
    val autoCleanThreshold: Int = 80,
    val isTurboModeEnabled: Boolean = false,
    val stats: CleaningStats? = null,
    val userWhitelist: Set<String> = emptySet(),
    val whitelistedApps: List<CleanableApp> = emptyList(),
    val threats: List<VirusThreat> = emptyList(),
    val isScanningViruses: Boolean = false,
    val virusScanProgress: Float = 0f,
    val virusScanLabel: String = "",
    val topDrainers: List<CleanableApp> = emptyList(),
    val games: List<CleanableApp> = emptyList(),
    val isGameBoosterInstalled: Boolean = false,
    val isMiui: Boolean = false,
    val freezerSearchQuery: String = "",
    val selectionSearchQuery: String = "",
    val isOverheatProtectionEnabled: Boolean = true,
    val showWhatsNew: Boolean = false,
    val freezerFilter: FreezerFilter = FreezerFilter.ALL,
    val vmStats: VmStats? = null,
    val deviceInfo: DeviceInfo = DeviceInfo(),
    val liveStats: SystemStats = SystemStats(0, 0, 0, 0, 0),
    val essentialPermissions: List<PermissionStatus> = emptyList(),
    val gameModePerfEnabled: Boolean = false,
    val gameModeMsaaEnabled: Boolean = false,
    val gameModeDisableOverlaysEnabled: Boolean = false,
    val isLaunchingGame: Boolean = false,
    val showDisabledAppsDrawer: Boolean = false,
    val gameLaunchingLabel: String = "",
    val touchSensitivityEnabled: Boolean = false,
    val gpuPriorityEnabled: Boolean = false,
    val networkBoostEnabled: Boolean = false,
    val highRefreshRateEnabled: Boolean = false,
    val frozenAppCount: Int = 0,
    val processingApps: Set<String> = emptySet(),
    val selectionFilter: SelectionFilter = SelectionFilter.ALL,
    val fontScale: Float = 0.8f,
    val iconStyle: String = "ROUNDED",
    val cardColor: Int = 0xFFFFFFFF.toInt(),
    val isWirelessDebugEnabled: Boolean = false,
    val isDeveloperOptionsEnabled: Boolean = false,
    val recentlyCleaned: List<String> = emptyList(),
    val processAnalysis: List<ProcessAnalysisInfo> = emptyList(),
    val systemHealthLogs: List<String> = emptyList(),
    val isAnalyzingProcesses: Boolean = false,
    val isAuditingSystem: Boolean = false,
    val micLevel: Float = 0f,
    val activeSensors: Map<Int, SensorData> = emptyMap(),
    val screenTimeStats: List<AppUsage> = emptyList(),
    val pingMs: Long = -1,
    val splashStepLabel: String = "Initializing Core...",
    val systemTimeInfo: SystemTimeInfo = SystemTimeInfo(),
    val cleaningTotal: Int = 0,
    val cleaningCurrent: Int = 0
)

interface ActivityCallback {
    fun getActivity(): Activity?
    fun showToast(message: String, duration: Int)
}

@Suppress("unused", "DEPRECATION")
class CleanerViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(CleanerUiState())
    val state: StateFlow<CleanerUiState> = _state.asStateFlow()
    private val refreshMutex = Mutex()
    var activityCallback: ActivityCallback? = null

    // ---- state used by refresh() / Shizuku handling. Declared up here on purpose: they must exist
    // before the init block below runs (init starts listeners and the first refresh).
    @Volatile private var lastRefreshFinishedMs = 0L

    private val appsMutex = Mutex()

    private val privilegeMutex = Mutex()

    private val shizukuPermissionListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode != ShizukuHelper.REQUEST_CODE) return@OnRequestPermissionResultListener
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                onPrivilegeChanged(justGranted = true)
            } else {
                val ctx = getApplication<Application>()
                activityCallback?.showToast(ctx.getString(R.string.shizuku_access_denied), Toast.LENGTH_SHORT)
                onPrivilegeChanged()
            }
        }

    private val essentialGrantPermissions = listOf(
        "android.permission.WRITE_SECURE_SETTINGS", "android.permission.WRITE_SETTINGS",
        "android.permission.PACKAGE_USAGE_STATS", "android.permission.DUMP", "android.permission.READ_LOGS",
        "android.permission.INTERACT_ACROSS_USERS", "android.permission.INTERACT_ACROSS_USERS_FULL",
        "android.permission.FORCE_STOP_PACKAGES", "android.permission.DELETE_CACHE_FILES",
        "android.permission.CLEAR_APP_CACHE", "android.permission.CLEAR_APP_USER_DATA",
        "android.permission.BATTERY_STATS", "android.permission.ACCESS_CACHE_FILESYSTEM",
        "android.permission.SYSTEM_ALERT_WINDOW",
        "android.permission.MANAGE_EXTERNAL_STORAGE", "android.permission.QUERY_ALL_PACKAGES",
        "android.permission.KILL_BACKGROUND_PROCESSES", "android.permission.GET_PACKAGE_SIZE",
        "android.permission.POST_NOTIFICATIONS"
    )
    private val essentialGrantAppOps = listOf(
        "WRITE_SETTINGS", "GET_USAGE_STATS", "SYSTEM_ALERT_WINDOW", "MANAGE_EXTERNAL_STORAGE", "FORCE_STOP_PACKAGES"
    )

    private var bannerHideJob: Job? = null

    init {
        val context = app.applicationContext

        _state.value = _state.value.copy(
            autoCleanEnabled = SettingsRepository.isAutoCleanEnabled(context),
            aggressiveModeEnabled = SettingsRepository.isAggressiveModeEnabled(context),
            fastAnimationsEnabled = SettingsRepository.isFastAnimationsEnabled(context),
            hardwareAccelerationEnabled = SettingsRepository.isHardwareAccelerationEnabled(context),
            hibernateScreenOff = SettingsRepository.isHibernateScreenOffEnabled(context),
            autoCleanNotificationsEnabled = SettingsRepository.isAutoCleanNotificationsEnabled(context),
            autoKillBackgroundEnabled = SettingsRepository.isAutoKillBackgroundEnabled(context),
            autoKillInactiveEnabled = SettingsRepository.isAutoKillInactiveEnabled(context),
            autoCleanThreshold = SettingsRepository.getAutoCleanThreshold(context),
            isTurboModeEnabled = SettingsRepository.isTurboModeEnabled(context),
            gameModePerfEnabled = SettingsRepository.isGameModePerfEnabled(context),
            gameModeMsaaEnabled = SettingsRepository.isGameModeMsaaEnabled(context),
            gameModeDisableOverlaysEnabled = SettingsRepository.isGameModeOverlaysDisabled(context),
            touchSensitivityEnabled = SettingsRepository.isTouchSensitivityEnabled(context),
            gpuPriorityEnabled = SettingsRepository.isGpuPriorityEnabled(context),
            networkBoostEnabled = SettingsRepository.isNetworkBoostEnabled(context),
            highRefreshRateEnabled = SettingsRepository.isHighRefreshRateEnabled(context),
            stats = try { StatsRepository.getStats(context) } catch (_: Exception) { CleaningStats(0, 0) },
            userWhitelist = SettingsRepository.getUserWhitelist(context),
            cleanOnBootEnabled = SettingsRepository.isCleanOnBootEnabled(context),
            isOverheatProtectionEnabled = SettingsRepository.isOverheatProtectionEnabled(context),
            mode = CleanMode.STANDARD,
            theme = try { AppTheme.valueOf(SettingsRepository.getAppTheme(context)) } catch (_: Exception) { AppTheme.DARK },
            fontScale = SettingsRepository.getFontScale(context),
            iconStyle = SettingsRepository.getIconStyle(context),
            hasAllFilesAccess = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager() else true,
            deviceInfo = fetchDeviceInfo(),
            liveStats = SystemStatsProvider.getStats(context),
            showWhatsNew = SettingsRepository.getLastVersionCode(context) < 6 // Current version code
        )

        if (_state.value.showWhatsNew) {
            SettingsRepository.setLastVersionCode(context, 6)
        }

        viewModelScope.launch(Dispatchers.IO) {
            // Apply all tweaks on startup
            TweakRepository.applyAllTweaks(context)
        }

        registerShizukuListeners()
        registerPackageReceiver()

        viewModelScope.launch {
            withContext(Dispatchers.IO) { checkPermissions() }
            refresh()
        }
        SystemStatsProvider.startFpsTracking()

        viewModelScope.launch {
            _state.update { it.copy(splashStepLabel = "Initializing AppOff Core...") }
            delay(300.milliseconds)
            _state.update { it.copy(splashStepLabel = "Verifying System Privileges...") }

            delay(400.milliseconds)
            _state.update { it.copy(splashStepLabel = "Scanning Active Memory...") }

            delay(400.milliseconds)
            _state.update { it.copy(splashStepLabel = "Optimization Core Ready") }

            delay(300.milliseconds)
            if (_state.value.currentScreen == Screen.SPLASH) {
                if (SettingsRepository.isIntroCompleted(context)) {
                    navigateTo(Screen.MAIN)
                } else {
                    navigateTo(Screen.INTRO)
                }
            }
        }

        viewModelScope.launch {
            var cachedDevice = fetchDeviceInfo()
            var tickCount = 0
            while (true) {
                delay(1.seconds)
                val ctx = getApplication<Application>()
                withContext(Dispatchers.IO) {
                    tickCount++
                    val snapshot = MemoryUtils.snapshot(ctx)
                    val stats = SystemStatsProvider.getStats(ctx)
                    val vms = VmRepository.getVmStats()
                    val timeInfo = SystemStatsProvider.getSystemTimeInfo()
                    if (tickCount % 10 == 0) {
                        cachedDevice = fetchDeviceInfo()
                    }
                    _state.update {
                        it.copy(
                            ram = snapshot,
                            deviceInfo = cachedDevice,
                            liveStats = stats,
                            vmStats = vms,
                            systemTimeInfo = timeInfo
                        )
                    }
                }
            }
        }

        viewModelScope.launch {
            while (true) {
                delay(30.seconds)
                if (!_state.value.isCleaning) refresh()
                // Periodic native heap trimming
                withContext(Dispatchers.IO) {
                    NativeMemoryUtils.trimMallocNative()
                }
            }
        }
    }

    private fun fetchDeviceInfo(): DeviceInfo {
        val context = getApplication<Application>()
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = context.registerReceiver(null, filter)

        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, 100).let { if (it == null || it == 0) 100 else it }
        val batteryPct = (level * 100 / scale.toFloat()).toInt().coerceIn(0, 100)
        val temp = batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val voltage = batteryStatus?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0

        val health = when (batteryStatus?.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)) {
            BatteryManager.BATTERY_HEALTH_GOOD -> context.getString(R.string.health_good)
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> context.getString(R.string.health_overheat)
            BatteryManager.BATTERY_HEALTH_DEAD -> context.getString(R.string.health_dead)
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> context.getString(R.string.health_over_voltage)
            else -> context.getString(R.string.health_unknown)
        }

        val stat = try { StatFs(Environment.getDataDirectory().path) } catch (_: Exception) { null }
        val totalBytes = stat?.totalBytes ?: 1L
        val availBytes = stat?.availableBytes ?: 0L
        val usedBytes = (totalBytes - availBytes).coerceAtLeast(0)

        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        val ramCapacity = MemoryUtils.formatBytes(memInfo.totalMem)

        return DeviceInfo(
            cpuName = Build.HARDWARE.uppercase(),
            cpuCores = Runtime.getRuntime().availableProcessors(),
            batteryHealth = health,
            batteryTemp = "${temp / 10f}°C",
            batteryLevel = batteryPct,
            batteryVoltage = "${String.format(Locale.US, "%.1f", voltage / 1000.0)}V",
            batteryCurrent = "---",
            storageUsed = MemoryUtils.formatBytes(usedBytes),
            storageTotal = MemoryUtils.formatBytes(totalBytes),
            storageFraction = (usedBytes.toFloat() / totalBytes).coerceIn(0f, 1f),
            ramCapacity = ramCapacity,
            androidVersion = "Android ${Build.VERSION.RELEASE}",
            securityPatch = "Patch: ${Build.VERSION.SECURITY_PATCH}",
            kernelVersion = System.getProperty("os.version") ?: context.getString(R.string.cpu_unknown)
        )
    }

    fun checkPermissions() {
        val context = getApplication<Application>()
        val appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager

        val list = listOf(
            "android.permission.WRITE_SECURE_SETTINGS" to "Secure Settings",
            "android.permission.WRITE_SETTINGS" to "System Settings",
            "android.permission.PACKAGE_USAGE_STATS" to "Usage Stats",
            "android.permission.DUMP" to "Process Analysis (DUMP)",
            "android.permission.READ_LOGS" to "System Health (READ_LOGS)",
            "android.permission.INTERACT_ACROSS_USERS_FULL" to "Interact Across Users Full",
            "android.permission.INTERACT_ACROSS_USERS" to "Interact Across Users",
            "android.permission.FORCE_STOP_PACKAGES" to "Force Stop Packages",
            "android.permission.DELETE_CACHE_FILES" to "Delete Cache Files",
            "android.permission.CLEAR_APP_USER_DATA" to "Clear App User Data",
            "android.permission.CLEAR_APP_CACHE" to "Clear App Cache",
            "android.permission.BATTERY_STATS" to "Battery Stats",
            "android.permission.ACCESS_CACHE_FILESYSTEM" to "Access Cache File System",
            "android.permission.SYSTEM_ALERT_WINDOW" to "System Alert Window",
            "android.permission.MANAGE_EXTERNAL_STORAGE" to "All Files Access",
            "android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" to "Ignore Battery Optimizations",
            "android.permission.QUERY_ALL_PACKAGES" to "Query All Packages",
            "android.permission.KILL_BACKGROUND_PROCESSES" to "Kill Background Processes",
            "android.permission.GET_PACKAGE_SIZE" to "Get Package Size",
            "android.permission.POST_NOTIFICATIONS" to "Post Notifications",
            "android.permission.RECEIVE_BOOT_COMPLETED" to "Start on Boot",
            "android.permission.FOREGROUND_SERVICE" to "Foreground Service",
            "android.permission.WAKE_LOCK" to "Wake Lock (Prevent Sleep)",
            "android.permission.ACCESS_NETWORK_STATE" to "View Network Connections",
            "appop.RUN_IN_BACKGROUND" to "Allow All Broadcasts (Background)",
            "appop.RUN_ANY_IN_BACKGROUND" to "Background Activity (Infinite)"
        ).map { (id, name) ->
            val granted = when (id) {
                "android.permission.WRITE_SETTINGS" -> Settings.System.canWrite(context) || checkAppOp(appOpsManager, AppOpsManager.OPSTR_WRITE_SETTINGS)
                "android.permission.PACKAGE_USAGE_STATS" -> checkAppOp(appOpsManager, AppOpsManager.OPSTR_GET_USAGE_STATS) || _state.value.shizukuGranted || _state.value.rootAvailable
                "android.permission.SYSTEM_ALERT_WINDOW" -> Settings.canDrawOverlays(context) || checkAppOp(appOpsManager, AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW)
                "android.permission.INTERACT_ACROSS_USERS" -> context.checkSelfPermission(id) == PackageManager.PERMISSION_GRANTED || checkAppOp(appOpsManager, "android:interact_across_users") || _state.value.shizukuGranted || _state.value.rootAvailable
                "android.permission.INTERACT_ACROSS_USERS_FULL" -> context.checkSelfPermission(id) == PackageManager.PERMISSION_GRANTED || checkAppOp(appOpsManager, "android:interact_across_users_full") || _state.value.shizukuGranted || _state.value.rootAvailable
                "android.permission.FORCE_STOP_PACKAGES" -> context.checkSelfPermission(id) == PackageManager.PERMISSION_GRANTED || checkAppOp(appOpsManager, "android:force_stop_packages") || _state.value.shizukuGranted || _state.value.rootAvailable || _state.value.systemPrivileged
                "android.permission.DELETE_CACHE_FILES" -> context.checkSelfPermission(id) == PackageManager.PERMISSION_GRANTED || checkAppOp(appOpsManager, "android:delete_cache_files") || _state.value.shizukuGranted || _state.value.rootAvailable || _state.value.systemPrivileged
                "android.permission.CLEAR_APP_CACHE" -> context.checkSelfPermission(id) == PackageManager.PERMISSION_GRANTED || checkAppOp(appOpsManager, "android:clear_app_cache") || _state.value.shizukuGranted || _state.value.rootAvailable || _state.value.systemPrivileged
                "android.permission.CLEAR_APP_USER_DATA" -> context.checkSelfPermission(id) == PackageManager.PERMISSION_GRANTED || checkAppOp(appOpsManager, "android:clear_app_user_data") || _state.value.shizukuGranted || _state.value.rootAvailable || _state.value.systemPrivileged
                "android.permission.ACCESS_CACHE_FILESYSTEM" -> context.checkSelfPermission(id) == PackageManager.PERMISSION_GRANTED || checkAppOp(appOpsManager, "android:access_cache_filesystem") || _state.value.shizukuGranted || _state.value.rootAvailable || _state.value.systemPrivileged
                "android.permission.DUMP" -> context.checkSelfPermission(id) == PackageManager.PERMISSION_GRANTED || _state.value.shizukuGranted || _state.value.rootAvailable || _state.value.systemPrivileged
                "android.permission.READ_LOGS" -> context.checkSelfPermission(id) == PackageManager.PERMISSION_GRANTED || _state.value.shizukuGranted || _state.value.rootAvailable || _state.value.systemPrivileged
                "android.permission.BATTERY_STATS" -> context.checkSelfPermission(id) == PackageManager.PERMISSION_GRANTED || _state.value.shizukuGranted || _state.value.rootAvailable || _state.value.systemPrivileged
                "android.permission.MANAGE_EXTERNAL_STORAGE" -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager()
                    else context.checkSelfPermission(id) == PackageManager.PERMISSION_GRANTED
                }
                "android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" -> {
                    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                    pm.isIgnoringBatteryOptimizations(context.packageName)
                }
                "appop.RUN_IN_BACKGROUND" -> checkAppOp(appOpsManager, "android:run_in_background")
                "appop.RUN_ANY_IN_BACKGROUND" -> checkAppOp(appOpsManager, "android:run_any_in_background")
                else -> context.checkSelfPermission(id) == PackageManager.PERMISSION_GRANTED
            }
            PermissionStatus(id, name, granted)
        }

        _state.update { it.copy(
            essentialPermissions = list,
            canWriteSecureSettings = context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED,
            canWriteSettings = Settings.System.canWrite(context),
            hasUsageAccess = checkAppOp(appOpsManager, AppOpsManager.OPSTR_GET_USAGE_STATS),
            isDeveloperOptionsEnabled = Settings.Global.getInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) != 0,
            isWirelessDebugEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Settings.Global.getInt(context.contentResolver, "adb_wifi_enabled", 0) != 0 else false
        ) }
    }

    private fun checkAppOp(appOpsManager: AppOpsManager, op: String): Boolean {
        return try {
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                @Suppress("DEPRECATION")
                appOpsManager.unsafeCheckOpNoThrow(op, Process.myUid(), getApplication<Application>().packageName)
            } else {
                @Suppress("DEPRECATION")
                appOpsManager.checkOpNoThrow(op, Process.myUid(), getApplication<Application>().packageName)
            }
            mode == AppOpsManager.MODE_ALLOWED
        } catch (_: Exception) {
            false
        }
    }

    fun grantAllPermissions() {
        viewModelScope.launch {
            val context = getApplication<Application>()
            val root = _state.value.rootAvailable
            val shizuku = _state.value.shizukuAvailable && _state.value.shizukuGranted

            if (root || shizuku) {
                withContext(Dispatchers.IO) { runGrantBatch(root) }
                // Saved tweaks need WRITE_SECURE_SETTINGS - re-apply them right away
                TweakRepository.applyAllTweaks(context)
                withContext(Dispatchers.IO) { checkPermissions() }
                activityCallback?.showToast(context.getString(R.string.permissions_granted), Toast.LENGTH_SHORT)
            } else {
                if (ShizukuHelper.isNotRunning(context)) {
                    val activity = activityCallback?.getActivity()
                    if (activity != null) {
                        AppActionHandler(activity).showShizukuNotRunningDialog()
                    } else {
                        activityCallback?.showToast(context.getString(R.string.shizuku_not_running_toast), Toast.LENGTH_LONG)
                        ShizukuHelper.openShizuku(context)
                    }
                } else {
                    activityCallback?.showToast(context.getString(R.string.requires_engine_root), Toast.LENGTH_SHORT)
                }
            }
        }
    }

    fun refresh(manual: Boolean = false, force: Boolean = false) {
        viewModelScope.launch {
            if (_state.value.isCleaning && !manual) return@launch
            // Automatic refreshes (resume, 30s timer, binder events) are skipped while one is running
            if (!manual && !force && refreshMutex.isLocked) return@launch
            // Only a user-initiated refresh (pull-to-refresh / refresh button) may show the spinner.
            // Automatic refreshes (resume, 30s timer, binder events, post-action) stay silent.
            if (manual) _state.update { it.copy(isRefreshing = true) }
            try {
                refreshMutex.withLock {
                    val context = getApplication<Application>()

                    // The app list loads in its own job and is published the moment PackageManager
                    // answers - it no longer waits for `ps` / `dumpsys meminfo` / stats below.
                    val appsJob = launch {
                        try { loadApps() } catch (e: CancellationException) { throw e } catch (_: Exception) {}
                    }

                    // Use coroutineScope to run heavy data gathering in parallel
                    val data = withContext(Dispatchers.IO) {
                        coroutineScope {
                            val pm = context.packageManager

                            val boosterInstalledAsync = async { try { pm.getPackageInfo("com.turbomax.pro", 0); true } catch (_: Exception) { false } }
                            val isMiuiAsync = async { try { pm.getPackageInfo("com.miui.securitycenter", 0); true } catch (_: Exception) { false } }
                            val isTurboModeEnabledAsync = async { SettingsRepository.isTurboModeEnabled(context) }
                            val isShizukuInstalledAsync = async { ShizukuHelper.isInstalled(context) }

                            val hasUsageAsync = async { AppRepository.hasUsageAccess(context) }
                            val currentWhitelist = SettingsRepository.getUserWhitelist(context)

                            val rootAvailableAsync = async { RootCleaner.isRootAvailable() }
                            val shizukuAvailableAsync = async { ShizukuCleaner.isShizukuAvailable() }
                            val shizukuGrantedAsync = async { ShizukuCleaner.hasPermission() }

                            val boosterInstalled = boosterInstalledAsync.await()
                            val isMiui = isMiuiAsync.await()
                            val isTurboModeEnabled = isTurboModeEnabledAsync.await()
                            val isShizukuInstalled = isShizukuInstalledAsync.await()
                            val hasUsage = hasUsageAsync.await()
                            val rootAvailable = rootAvailableAsync.await()
                            val shizukuAvailable = shizukuAvailableAsync.await()
                            val shizukuGranted = shizukuGrantedAsync.await()

                            val ramSnapshotAsync = async { MemoryUtils.snapshot(context) }
                            val vmStatsAsync = async { VmRepository.getVmStats() }
                            val topDrainersAsync = async { if (hasUsage) AppRepository.getTopDrainers(context) else emptyList() }
                            val gamesAsync = async { AppRepository.getInstalledGames(context) }
                            val whitelistedAppsAsync = async { AppRepository.getAppsByPackageNames(context, currentWhitelist.toList()) }
                            val frozenCountAsync = async { FreezerHelper.getFrozenAppCount(context) }

                            val hasAllFilesAccess = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager() else true

                            ensureBatteryExemption(context, rootAvailable, shizukuGranted)

                            val isBatteryOptimizationsIgnored = checkBatteryExemption(context)
                            val systemPrivileged = SystemCleaner.hasPrivilegedPermissions(context)
                            val canWriteSecureSettings = context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED
                            val canWriteSettings = Settings.System.canWrite(context)

                            AppRepository.refreshDisabledAppsThrottled(context)
                            AppRepository.refreshSuspendedAppsThrottled(context)

                            val shellEngine = when {
                                rootAvailable -> context.getString(R.string.shell_mode_root)
                                shizukuGranted -> context.getString(R.string.shell_mode_shizuku)
                                else -> context.getString(R.string.shell_mode_standard)
                            }

                            val binderStatus = when {
                                rootAvailable -> context.getString(R.string.binder_healthy)
                                shizukuAvailable -> {
                                    if (ShizukuHelper.isAvailable()) context.getString(R.string.binder_healthy)
                                    else context.getString(R.string.binder_disconnected)
                                }
                                else -> context.getString(R.string.not_connected)
                            }

                            RefreshData(
                                ram = ramSnapshotAsync.await(),
                                hasUsage = hasUsage,
                                systemPrivileged = systemPrivileged,
                                canWriteSecureSettings = canWriteSecureSettings,
                                canWriteSettings = canWriteSettings,
                                isShizukuInstalled = isShizukuInstalled,
                                shizukuAvailable = shizukuAvailable,
                                shizukuGranted = shizukuGranted,
                                hasAllFilesAccess = hasAllFilesAccess,
                                rootAvailable = rootAvailable,
                                isBatteryOptimizationsIgnored = isBatteryOptimizationsIgnored,
                                vmStats = vmStatsAsync.await(),
                                topDrainers = topDrainersAsync.await(),
                                games = gamesAsync.await(),
                                userWhitelist = currentWhitelist,
                                whitelistedApps = whitelistedAppsAsync.await(),
                                boosterInstalled = boosterInstalled,
                                isMiui = isMiui,
                                isTurboModeEnabled = isTurboModeEnabled,
                                frozenCount = frozenCountAsync.await(),
                                shellEngine = shellEngine,
                                binderStatus = binderStatus
                            )
                        }
                    }

                    _state.update { s ->
                        s.copy(
                            ram = data.ram,
                            hasUsageAccess = data.hasUsage,
                            mode = pickMode(data.rootAvailable, data.shizukuAvailable && data.shizukuGranted, data.systemPrivileged),
                            systemPrivileged = data.systemPrivileged,
                            canWriteSecureSettings = data.canWriteSecureSettings,
                            canWriteSettings = data.canWriteSettings,
                            isShizukuInstalled = data.isShizukuInstalled,
                            shizukuAvailable = data.shizukuAvailable,
                            shizukuGranted = data.shizukuGranted,
                            hasAllFilesAccess = data.hasAllFilesAccess,
                            rootAvailable = data.rootAvailable,
                            isBatteryOptimizationsIgnored = data.isBatteryOptimizationsIgnored,
                            vmStats = data.vmStats,
                            topDrainers = data.topDrainers,
                            games = data.games,
                            userWhitelist = data.userWhitelist,
                            whitelistedApps = data.whitelistedApps,
                            isGameBoosterInstalled = data.boosterInstalled,
                            isMiui = data.isMiui,
                            isTurboModeEnabled = data.isTurboModeEnabled,
                            frozenAppCount = data.frozenCount,
                            shellEngine = data.shellEngine,
                            binderStatus = data.binderStatus
                        )
                    }

                    // Update essential permissions based on new Shizuku/Root status
                    checkPermissions()

                    // Sync active background services according to settings
                    if (SettingsRepository.isAutoCleanEnabled(context) || SettingsRepository.isTurboModeEnabled(context) || SettingsRepository.isAutoKillBackgroundEnabled(context) || SettingsRepository.isAutoKillInactiveEnabled(context)) {
                        try { RamMonitorService.start(context) } catch (_: Exception) {}
                    }

                    appsJob.join()

                    if (manual) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, context.getString(R.string.engine_refreshed), Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } finally {
                lastRefreshFinishedMs = System.currentTimeMillis()
                if (manual) _state.update { it.copy(isRefreshing = false) }
            }
        }
    }

    /**
     * Two-phase app loading so the list appears as fast as possible:
     *  1. build it from PackageManager only (no shell commands) and publish straight away;
     *  2. fetch "running" + memory info (ps / dumpsys meminfo through Shizuku/root - slow) and patch it in.
     */
    private suspend fun loadApps() {
        appsMutex.withLock {
            val context = getApplication<Application>()
            val whitelist = SettingsRepository.getUserWhitelist(context)

            val base = AppRepository.getCleanableApps(
                context, includeSystem = true, extraWhitelist = whitelist, requireUsage = false
            )
            publishApps(base, whitelist)

            val screen = _state.value.currentScreen
            val wantMemory = screen == Screen.MAIN || screen == Screen.SPLASH
            val (running, memory) = coroutineScope {
                val r = async { getRunningPackages() }
                val m = async { if (wantMemory) getPackageMemoryUsage() else emptyMap() }
                r.await() to m.await()
            }
            if (running.isEmpty() && memory.isEmpty()) return@withLock

            val enriched = base.map { app ->
                val mem = memory[app.packageName] ?: 0L
                val isRunning = app.isRunning || app.packageName in running || mem > 0L
                if (isRunning == app.isRunning && mem == app.memoryUsageBytes) app
                else app.copy(isRunning = isRunning, memoryUsageBytes = mem)
            }
            publishApps(enriched, whitelist)
        }
    }

    /** Merges a freshly loaded list into state, keeping the user's current selection. */
    private fun publishApps(newApps: List<CleanableApp>, whitelist: Set<String>) {
        _state.update { s ->
            val existingByPkg = s.apps.associateBy { it.packageName }
            val merged = newApps.map { newApp ->
                val isWhitelisted = whitelist.contains(newApp.packageName)
                val existing = existingByPkg[newApp.packageName]
                if (existing != null) {
                    if (isWhitelisted || newApp.isDisabled) newApp.copy(selected = false)
                    else newApp.copy(selected = existing.selected)
                } else {
                    newApp.copy(selected = !isWhitelisted && !newApp.isDisabled)
                }
            }
            s.copy(apps = merged)
        }
    }

    /**
     * Updates one app's disabled/suspended flags in every list immediately after a verified
     * action, so the switch flips right away instead of waiting for a PackageManager re-query.
     */
    private fun patchAppState(packageName: String, disabled: Boolean? = null, suspended: Boolean? = null, hidden: Boolean? = null, uninstalled: Boolean? = null) {
        fun CleanableApp.patched(): CleanableApp {
            val d = disabled ?: isDisabled
            val sp = suspended ?: isSuspended
            val h = hidden ?: isHidden
            val u = uninstalled ?: (isHidden || isUninstalled)
            val finalUninstalled = uninstalled ?: (u || h)
            val finalFrozen = d || sp || h || finalUninstalled
            return copy(
                isDisabled = d,
                isSuspended = sp,
                isHidden = h,
                isUninstalled = finalUninstalled,
                isFrozen = finalFrozen,
                selected = if (d || finalUninstalled) false else selected
            )
        }
        _state.update { st ->
            st.copy(
                apps = st.apps.map { if (it.packageName == packageName) it.patched() else it },
                topDrainers = st.topDrainers.map { if (it.packageName == packageName) it.patched() else it },
                games = st.games.map { if (it.packageName == packageName) it.patched() else it },
                whitelistedApps = st.whitelistedApps.map { if (it.packageName == packageName) it.patched() else it }
            )
        }
    }

    private fun refreshApp(packageName: String) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            delay(150.milliseconds)
            val updatedApp = AppRepository.getAppByPackageName(context, packageName)

            _state.update { s ->
                val existingApp = s.apps.find { it.packageName == packageName }
                val mergedApp = if (updatedApp == null) {
                    existingApp?.copy(
                        isUninstalled = true,
                        isHidden = true,
                        isDisabled = true,
                        isFrozen = true,
                        selected = false
                    )
                } else if (existingApp != null) {
                    // Respect optimistic patches if PackageManager's IPC cache hasn't updated yet
                    val disabled = if (existingApp.isDisabled != updatedApp.isDisabled) existingApp.isDisabled else updatedApp.isDisabled
                    val suspended = if (existingApp.isSuspended != updatedApp.isSuspended) existingApp.isSuspended else updatedApp.isSuspended
                    val uninstalled = if (existingApp.isUninstalled != updatedApp.isUninstalled) existingApp.isUninstalled else updatedApp.isUninstalled
                    val hidden = if (existingApp.isHidden != updatedApp.isHidden) existingApp.isHidden else updatedApp.isHidden
                    val frozen = disabled || suspended || hidden || uninstalled

                    updatedApp.copy(
                        isDisabled = disabled,
                        isSuspended = suspended,
                        isUninstalled = uninstalled,
                        isHidden = hidden,
                        isFrozen = frozen,
                        selected = existingApp.selected && !disabled && !uninstalled
                    )
                } else updatedApp

                if (mergedApp == null) return@update s

                val newApps = s.apps.map { if (it.packageName == packageName) mergedApp else it }
                val newDrainers = s.topDrainers.map { if (it.packageName == packageName) mergedApp else it }
                val newGames = s.games.map { if (it.packageName == packageName) mergedApp else it }
                val newWhitelisted = s.whitelistedApps.map { if (it.packageName == packageName) mergedApp else it }

                s.copy(
                    apps = newApps,
                    topDrainers = newDrainers,
                    games = newGames,
                    whitelistedApps = newWhitelisted
                )
            }
        }
    }

    fun navigateTo(screen: Screen) {
        _state.update { it.copy(currentScreen = screen) }
        if (screen == Screen.MAIN) {
            refresh()
        } else if (screen == Screen.JUNK_CLEANER) {
            refresh()
            scanJunk()
        }
    }

    fun completeIntro() {
        val context = getApplication<Application>()
        SettingsRepository.setIntroCompleted(context, true)
        _state.update { it.copy(hasCompletedIntro = true) }
        navigateTo(Screen.MAIN)
    }

    fun reopenIntro() {
        navigateTo(Screen.INTRO)
    }

    // 🔴 FIXED: toggleApp with processing guard and whitelist check
    fun toggleApp(pkg: String) {
        if (_state.value.processingApps.contains(pkg)) return
        val isWhitelisted = _state.value.userWhitelist.contains(pkg)
        if (isWhitelisted) return // Don't allow selecting whitelisted apps for optimization

        _state.update { s -> s.copy(apps = s.apps.map { if (it.packageName == pkg) it.copy(selected = !it.selected) else it }) }
    }

    // 🔴 FIXED: toggleAllApps to respect disabled state and whitelist
    fun toggleAllApps() {
        val targetApps = _state.value.apps.filter { !it.isDisabled && !_state.value.userWhitelist.contains(it.packageName) }
        val anyUnselected = targetApps.any { !it.selected }
        _state.update { s ->
            s.copy(apps = s.apps.map {
                if (!it.isDisabled && !s.userWhitelist.contains(it.packageName)) {
                    it.copy(selected = anyUnselected)
                } else it
            })
        }
    }

    fun cleanSelected() {
        val targets = _state.value.apps
            .filter { it.selected && !_state.value.userWhitelist.contains(it.packageName) }
            .map { it.packageName }

        if (targets.isEmpty() || _state.value.isCleaning) return

        viewModelScope.launch {
            val context = getApplication<Application>()
            _state.update { it.copy(
                isCleaning = true,
                cleaningTask = context.getString(R.string.initializing_task),
                recentlyCleaned = emptyList(),
                cleaningTotal = targets.size,
                cleaningCurrent = 0
            ) }

            val strategy = when (_state.value.mode) {
                CleanMode.STANDARD -> StandardCleaner
                CleanMode.SYSTEM -> SystemCleaner
                CleanMode.SHIZUKU -> ShizukuCleaner
                CleanMode.ROOT -> RootCleaner
            }

            var processed = 0
            val result = strategy.clean(
                context, targets,
                cacheOnly = false,
                onProgress = { pkg ->
                    processed++
                    val app = _state.value.apps.find { a -> a.packageName == pkg }
                    _state.update { s -> s.copy(
                        cleaningPackage = pkg,
                        cleaningLabel = app?.label,
                        cleaningCurrent = processed,
                        recentlyCleaned = (s.recentlyCleaned + pkg).takeLast(10)
                    ) }
                },
                onTaskUpdate = { task -> _state.update { it.copy(cleaningTask = task) } }
            )

            if (result.freedBytesEstimate > 0) StatsRepository.recordCleanup(context, result.freedBytesEstimate)

            _state.update { it.copy(
                isCleaning = false, cleaningPackage = null, cleaningLabel = null, cleaningTask = null,
                lastResult = result.withLabels(), ram = MemoryUtils.snapshot(context), stats = StatsRepository.getStats(context)
            ) }
            startBannerTimeout()
        }
    }

    private fun startBannerTimeout() {
        bannerHideJob?.cancel()
        bannerHideJob = viewModelScope.launch {
            delay(1500.milliseconds) // Keep visible for 1.5 seconds
            _state.update { it.copy(lastResult = null) }
        }
    }

    fun scanJunk() {
        if (_state.value.isScanningJunk) return
        viewModelScope.launch {
            val context = getApplication<Application>()
            _state.update { it.copy(isScanningJunk = true, cleaningTask = context.getString(R.string.scanning_storage_task)) }
            val junk = JunkRepository.scanJunk(getApplication()) { file -> _state.update { it.copy(cleaningPackage = file) } }
            _state.update { it.copy(isScanningJunk = false, junkItems = junk, totalJunkSize = junk.sumOf { item -> item.size }, cleaningPackage = null, cleaningTask = null) }
        }
    }

    fun cleanJunk() {
        val items = _state.value.junkItems
        if (items.isEmpty()) return
        viewModelScope.launch {
            val context = getApplication<Application>()
            _state.update { it.copy(isCleaning = true, cleaningTask = context.getString(R.string.purging_junk_task), recentlyCleaned = emptyList()) }
            val freed = JunkRepository.cleanJunk(items) { file ->
                _state.update { s -> s.copy(
                    cleaningPackage = file,
                    recentlyCleaned = (s.recentlyCleaned + file).takeLast(10)
                ) }
            }
            StatsRepository.recordCleanup(getApplication(), freed)
            _state.update { it.copy(isCleaning = false, junkItems = emptyList(), totalJunkSize = 0, cleaningPackage = null, cleaningTask = null, recentlyCleaned = emptyList(), stats = StatsRepository.getStats(getApplication())) }
        }
    }

    fun coolCpu() {
        viewModelScope.launch {
            val context = getApplication<Application>()
            _state.update { it.copy(isCooling = true, cleaningTask = context.getString(R.string.cooling_cpu_task), recentlyCleaned = emptyList()) }
            val extraWhitelist = _state.value.userWhitelist
            val targets = withContext(Dispatchers.IO) {
                AppRepository.getCleanableApps(context, includeSystem = false, extraWhitelist = extraWhitelist, requireUsage = false)
                    .filter { !it.isFrozen && !it.isSuspended && !extraWhitelist.contains(it.packageName) }
                    .take(12)
                    .map { it.packageName }
            }

            if (targets.isNotEmpty()) {
                val strategy = when {
                    RootCleaner.isRootAvailable() -> RootCleaner
                    ShizukuCleaner.isShizukuAvailable() && ShizukuCleaner.hasPermission() -> ShizukuCleaner
                    else -> StandardCleaner
                }

                strategy.clean(context, targets,
                    cacheOnly = false,
                    onProgress = { p ->
                        val app = _state.value.apps.find { a -> a.packageName == p }
                        _state.update { s -> s.copy(
                            cleaningPackage = p,
                            cleaningLabel = app?.label,
                            recentlyCleaned = (s.recentlyCleaned + p).takeLast(10)
                        ) }
                    },
                    onTaskUpdate = { task -> _state.update { it.copy(cleaningTask = task) } }
                )
            }

            delay(3000.milliseconds)
            _state.update { it.copy(isCooling = false, cleaningPackage = null, cleaningLabel = null, cleaningTask = null, recentlyCleaned = emptyList(), ram = MemoryUtils.snapshot(getApplication())) }
        }
    }

    fun scanForViruses() {
        viewModelScope.launch {
            _state.update { it.copy(isScanningViruses = true, virusScanProgress = 0f, virusScanLabel = "Initializing...") }

            val pm = getApplication<Application>().packageManager
            val installedApps = pm.getInstalledApplications(0)
            val total = installedApps.size

            installedApps.forEachIndexed { index, appInfo ->
                val progress = (index + 1).toFloat() / total
                val label = pm.getApplicationLabel(appInfo).toString()
                _state.update { it.copy(virusScanProgress = progress, virusScanLabel = label) }
                delay(1.milliseconds) // Simulate scan delay faster
            }

            _state.update { it.copy(virusScanLabel = "Analyzing signatures...") }
            val detectedThreats = VirusScanner.scanInstalledApps(getApplication())
            _state.update { it.copy(threats = detectedThreats, isScanningViruses = false, virusScanProgress = 1f) }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────────────────────
    //  Privileged access (Root / Shizuku). The engine is picked automatically and everything that
    //  needs the new access is applied the moment it is granted - no manual mode switching.
    // ──────────────────────────────────────────────────────────────────────────────────────────

    private data class Access(
        val shizukuInstalled: Boolean,
        val shizukuAvailable: Boolean,
        val shizukuGranted: Boolean,
        val root: Boolean,
        val system: Boolean
    )

    private fun pickMode(root: Boolean, shizuku: Boolean, system: Boolean): CleanMode = when {
        root -> CleanMode.ROOT
        shizuku -> CleanMode.SHIZUKU
        system -> CleanMode.SYSTEM
        else -> CleanMode.STANDARD
    }

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            val pkg = intent.data?.schemeSpecificPart ?: return
            if (pkg.isBlank()) return

            when (action) {
                Intent.ACTION_PACKAGE_REMOVED, Intent.ACTION_PACKAGE_FULLY_REMOVED -> {
                    patchAppState(pkg, uninstalled = true, hidden = true, disabled = true)
                    refreshApp(pkg)
                }
                Intent.ACTION_PACKAGE_ADDED, Intent.ACTION_PACKAGE_CHANGED, Intent.ACTION_PACKAGE_REPLACED -> {
                    refreshApp(pkg)
                }
            }
        }
    }

    private fun registerPackageReceiver() {
        try {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED)
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_CHANGED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addDataScheme("package")
            }
            getApplication<Application>().registerReceiver(packageReceiver, filter)
        } catch (_: Throwable) {}
    }

    private fun registerShizukuListeners() {
        try {
            // Sticky: fires immediately if Shizuku is already running when the app starts
            Shizuku.addBinderReceivedListenerSticky { onPrivilegeChanged() }
            Shizuku.addBinderDeadListener { onPrivilegeChanged() }
            Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)
        } catch (_: Throwable) {}
    }

    override fun onCleared() {
        try { Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener) } catch (_: Throwable) {}
        try { getApplication<Application>().unregisterReceiver(packageReceiver) } catch (_: Throwable) {}
    }

    /**
     * Single entry point for "access level changed" (Shizuku binder up/down, permission granted,
     * root detected):
     *  1. publish the new flags + auto-selected engine immediately (cheap checks only);
     *  2. if we just became privileged: pre-bind the shell service, grant our own permissions in ONE
     *     command, then apply saved tweaks (they need WRITE_SECURE_SETTINGS) while the app list reloads.
     */
    private fun onPrivilegeChanged(justGranted: Boolean = false) {
        viewModelScope.launch { privilegeMutex.withLock {
            val context = getApplication<Application>()
            val before = _state.value
            val wasPrivileged = before.shizukuGranted || before.rootAvailable

            val access = withContext(Dispatchers.IO) {
                Access(
                    shizukuInstalled = ShizukuHelper.isInstalled(context),
                    shizukuAvailable = ShizukuCleaner.isShizukuAvailable(),
                    shizukuGranted = ShizukuCleaner.hasPermission(),
                    root = RootCleaner.isRootAvailable(),
                    system = SystemCleaner.hasPrivilegedPermissions(context)
                )
            }
            val shizuku = access.shizukuAvailable && access.shizukuGranted
            val privileged = shizuku || access.root
            val levelChanged = privileged != wasPrivileged || shizuku != before.shizukuGranted || access.root != before.rootAvailable

            _state.update {
                it.copy(
                    isShizukuInstalled = access.shizukuInstalled,
                    shizukuAvailable = access.shizukuAvailable,
                    shizukuGranted = shizuku,
                    rootAvailable = access.root,
                    systemPrivileged = access.system,
                    mode = pickMode(access.root, shizuku, access.system),
                    isApplyingAccess = privileged && (justGranted || !wasPrivileged)
                )
            }

            if (privileged && (justGranted || !wasPrivileged)) {
                withContext(Dispatchers.IO) {
                    if (!access.root) ShizukuCleaner.warmUp()
                    checkPermissions()
                    if (justGranted || _state.value.essentialPermissions.any { !it.isGranted }) {
                        runGrantBatch(access.root)
                    }
                    ensureBatteryExemption(context, access.root, shizuku)
                    // Permissions are in place now, so tweaks that need them take effect immediately
                    TweakRepository.applyAllTweaks(context)
                    checkPermissions()
                }
                if (SettingsRepository.isAutoCleanEnabled(context) || SettingsRepository.isTurboModeEnabled(context) ||
                    SettingsRepository.isAutoKillBackgroundEnabled(context) || SettingsRepository.isAutoKillInactiveEnabled(context)) {
                    try { RamMonitorService.start(context) } catch (_: Exception) {}
                }
                if (justGranted) {
                    activityCallback?.showToast(context.getString(R.string.shizuku_ready_toast), Toast.LENGTH_SHORT)
                }
            }

            _state.update { it.copy(isApplyingAccess = false) }
            if (levelChanged || justGranted) refresh(force = justGranted)
        } }
    }

    /** Cheap check used on resume: only does real work when the access level actually changed. */
    fun onAppResumed() {
        viewModelScope.launch {
            val s = _state.value
            val (granted, root) = withContext(Dispatchers.IO) {
                (ShizukuCleaner.isShizukuAvailable() && ShizukuCleaner.hasPermission()) to RootCleaner.isRootAvailable()
            }
            if (granted != s.shizukuGranted || root != s.rootAvailable) {
                onPrivilegeChanged()
            } else if (System.currentTimeMillis() - lastRefreshFinishedMs > 3_000L) {
                refresh()
            }
        }
    }

    /**
     * One-tap Shizuku setup - always does whichever step is next:
     * not installed -> store, installed but stopped -> open Shizuku, running -> system permission dialog.
     * The result arrives through the listeners above, so nothing has to be refreshed by hand.
     */
    fun requestShizukuAccess() {
        val context = getApplication<Application>()
        val s = _state.value
        when {
            s.shizukuGranted -> Unit
            s.shizukuAvailable -> {
                try {
                    if (Shizuku.shouldShowRequestPermissionRationale()) {
                        // User picked "deny and don't ask again": the dialog can't show any more
                        activityCallback?.showToast(context.getString(R.string.shizuku_allow_in_app), Toast.LENGTH_LONG)
                        ShizukuHelper.openShizuku(context)
                    } else {
                        Shizuku.requestPermission(ShizukuHelper.REQUEST_CODE)
                    }
                } catch (_: Throwable) {
                    ShizukuHelper.openShizuku(context)
                }
            }
            s.isShizukuInstalled -> {
                val activity = activityCallback?.getActivity()
                if (activity != null) {
                    AppActionHandler(activity).showShizukuNotRunningDialog()
                } else {
                    activityCallback?.showToast(context.getString(R.string.shizuku_not_running_toast), Toast.LENGTH_LONG)
                    if (!ShizukuHelper.openShizuku(context)) ShizukuHelper.installShizuku(context)
                }
            }
            else -> ShizukuHelper.installShizuku(context)
        }
    }

    /** Grants essential system permissions safely without triggering process termination. */
    private suspend fun runGrantBatch(root: Boolean) {
        val context = getApplication<Application>()
        val pkg = context.packageName
        val safePermissions = listOf(
            "android.permission.WRITE_SECURE_SETTINGS",
            "android.permission.DUMP",
            "android.permission.READ_LOGS"
        )
        val needed = safePermissions.filter {
            context.checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (needed.isEmpty()) return

        val cmd = buildString {
            needed.forEach { append("pm grant $pkg $it 2>/dev/null; ") }
            append("true")
        }
        if (root) RootCleaner.runShell(cmd) else ShizukuCleaner.exec(cmd)
    }

    /** Called by the "Shizuku Service Not Running" dialog: open the Shizuku app directly. */
    fun openShizukuApp() {
        val context = getApplication<Application>()
        if (!ShizukuHelper.openShizuku(context)) {
            if (ShizukuHelper.isInstalled(context)) {
                activityCallback?.showToast(context.getString(R.string.shizuku_not_running_toast), Toast.LENGTH_LONG)
            } else {
                ShizukuHelper.installShizuku(context)
            }
        }
    }

    fun launchShizuku() {
        val context = getApplication<Application>()
        if (ShizukuHelper.isNotRunning(context)) {
            val activity = activityCallback?.getActivity()
            if (activity != null) {
                AppActionHandler(activity).showShizukuNotRunningDialog()
                return
            }
            activityCallback?.showToast(context.getString(R.string.shizuku_not_running_toast), Toast.LENGTH_LONG)
        }
        if (!ShizukuHelper.openShizuku(context)) ShizukuHelper.installShizuku(context)
    }

    fun launchPerformanceMode() {
        val context = getApplication<Application>()
        val intents = listOf(
            // MIUI
            Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.powercenter.PowerCenterRecentTaskActivity")),
            Intent("miui.intent.action.POWER_MANAGER"),
            // One UI (Samsung)
            Intent().setComponent(ComponentName("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity")),
            // ColorOS / ReadmeUI
            Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity")),
            // Stock / Pixel
            Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS),
            // Generic fallback
            Intent(Settings.ACTION_SETTINGS)
        )

        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return
            } catch (_: Exception) {}
        }
    }

    fun setGameMode(packageName: String, mode: Int) {
        // mode: 1 = Battery, 2 = Performance, 3 = Standard
        viewModelScope.launch {
            val cmd = "cmd game mode set $mode $packageName"
            when {
                RootCleaner.isRootAvailable() -> RootCleaner.runShell(cmd)
                ShizukuCleaner.hasPermission() -> ShizukuCleaner.exec(cmd)
            }
            // Grant high priority
            if (ShizukuCleaner.hasPermission()) {
                ShizukuCleaner.setStandbyBucket(packageName, 10) // Active
                ShizukuCleaner.setAppOp(packageName, 63, 0) // Allow
            } else if (RootCleaner.isRootAvailable()) {
                RootCleaner.setStandbyBucket(packageName, 10)
                RootCleaner.setAppOp(packageName, 63, 0)
            }
            Toast.makeText(getApplication(), getApplication<Application>().getString(R.string.hardware_optimized, packageName), Toast.LENGTH_SHORT).show()
        }
    }

    fun toggleGameModePerf() {
        val newState = !_state.value.gameModePerfEnabled
        SettingsRepository.setGameModePerfEnabled(getApplication(), newState)
        _state.update { it.copy(gameModePerfEnabled = newState) }
        applyGameTweaks()
    }

    fun toggleGameModeMsaa() {
        val newState = !_state.value.gameModeMsaaEnabled
        SettingsRepository.setGameModeMsaaEnabled(getApplication(), newState)
        _state.update { it.copy(gameModeMsaaEnabled = newState) }
        applyGameTweaks()
    }

    fun toggleGameModeDisableOverlays() {
        val newState = !_state.value.gameModeDisableOverlaysEnabled
        SettingsRepository.setGameModeOverlaysDisabled(getApplication(), newState)
        _state.update { it.copy(gameModeDisableOverlaysEnabled = newState) }
        applyGameTweaks()
    }

    fun toggleTouchSensitivity() {
        val newState = !_state.value.touchSensitivityEnabled
        SettingsRepository.setTouchSensitivityEnabled(getApplication(), newState)
        _state.update { it.copy(touchSensitivityEnabled = newState) }
        applyGameTweaks()
    }

    fun toggleGpuPriority() {
        val newState = !_state.value.gpuPriorityEnabled
        SettingsRepository.setGpuPriorityEnabled(getApplication(), newState)
        _state.update { it.copy(gpuPriorityEnabled = newState) }
        applyGameTweaks()
    }

    fun toggleNetworkBoost() {
        val newState = !_state.value.networkBoostEnabled
        SettingsRepository.setNetworkBoostEnabled(getApplication(), newState)
        _state.update { it.copy(networkBoostEnabled = newState) }
        applyGameTweaks()
    }

    fun toggleHighRefreshRate() {
        val newState = !_state.value.highRefreshRateEnabled
        SettingsRepository.setHighRefreshRateEnabled(getApplication(), newState)
        _state.update { it.copy(highRefreshRateEnabled = newState) }
        applyGameTweaks()
    }

    fun addUserGame(packageName: String) {
        SettingsRepository.addUserGame(getApplication(), packageName)
        refresh()
    }

    fun removeGame(packageName: String) {
        // If it's in user manual list, remove from there
        // Otherwise, add to hidden list to hide detected game
        val context = getApplication<Application>()
        if (SettingsRepository.getUserGames(context).contains(packageName)) {
            SettingsRepository.removeUserGame(context, packageName)
        } else {
            SettingsRepository.addHiddenGame(context, packageName)
        }
        refresh()
    }

    private fun applyGameTweaks() {
        viewModelScope.launch {
            TweakRepository.applyAllTweaks(getApplication())
        }
    }

    fun launchApp(pkg: String) {
        viewModelScope.launch {
            val app = _state.value.apps.find { it.packageName == pkg } ?: return@launch
            val context = getApplication<Application>()

            // Ensure target app is active
            if (app.isFrozen || app.isDisabled || app.isSuspended) {
                _state.update { it.copy(cleaningTask = context.getString(R.string.waking_app_task)) }
                unfreezeApp(pkg)
                // PM needs time to update component state before it can be launched
                delay(300.milliseconds)
            }

            val pm = context.packageManager

            // Re-fetch intent after unfreezing as it might have been null while disabled
            val intent = pm.getLaunchIntentForPackage(pkg)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                try {
                    context.startActivity(intent)
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, context.getString(R.string.system_blocked_launch, e.message), Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                val fallbackIntent = pm.getLeanbackLaunchIntentForPackage(pkg)

                if (fallbackIntent != null) {
                    context.startActivity(fallbackIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, context.getString(R.string.launcher_not_found, app.label), Toast.LENGTH_SHORT).show()
                    }
                }
            }

            delay(300.milliseconds)
            refresh()
        }
    }

    fun toggleIncludeSystem() { _state.update { it.copy(includeSystemApps = !it.includeSystemApps) }; refresh() }

    fun toggleAutoClean() {
        val n = !_state.value.autoCleanEnabled
        val context = getApplication<Application>()
        SettingsRepository.setAutoCleanEnabled(context, n)
        _state.update { it.copy(autoCleanEnabled = n) }
        if (n) {
            RamMonitorService.start(context)
            MaintenanceWorker.schedule(context)
        } else {
            MaintenanceWorker.cancel(context)
        }
    }

    fun toggleAggressiveMode() {
        val n = !_state.value.aggressiveModeEnabled
        val context = getApplication<Application>()
        SettingsRepository.setAggressiveModeEnabled(context, n)
        _state.update { it.copy(aggressiveModeEnabled = n) }
        RamMonitorService.start(context)
    }



    fun toggleAutoCleanNotifications() { val n = !_state.value.autoCleanNotificationsEnabled; SettingsRepository.setAutoCleanNotificationsEnabled(getApplication(), n); _state.update { it.copy(autoCleanNotificationsEnabled = n) } }

    fun toggleAutoKillBackground() {
        val n = !_state.value.autoKillBackgroundEnabled
        val context = getApplication<Application>()
        SettingsRepository.setAutoKillBackgroundEnabled(context, n)
        _state.update { it.copy(autoKillBackgroundEnabled = n) }
        if (n) {
            RamMonitorService.start(context)
        }
    }

    fun toggleAutoKillInactive() {
        val n = !_state.value.autoKillInactiveEnabled
        val context = getApplication<Application>()
        SettingsRepository.setAutoKillInactiveEnabled(context, n)
        _state.update { it.copy(autoKillInactiveEnabled = n) }
        if (n) {
            RamMonitorService.start(context)
        }
    }

    fun toggleCleanOnBoot() { val n = !_state.value.cleanOnBootEnabled; SettingsRepository.setCleanOnBootEnabled(getApplication(), n); _state.update { it.copy(cleanOnBootEnabled = n) } }

    fun toggleHibernateScreenOff() {
        val n = !_state.value.hibernateScreenOff
        val context = getApplication<Application>()
        SettingsRepository.setHibernateScreenOffEnabled(context, n)
        _state.update { it.copy(hibernateScreenOff = n) }
        if (n) {
            RamMonitorService.start(context)
        }
    }

    fun setAutoCleanThreshold(t: Int) {
        val context = getApplication<Application>()
        SettingsRepository.setAutoCleanThreshold(context, t)
        _state.update { it.copy(autoCleanThreshold = t) }
        RamMonitorService.start(context)
    }

    fun toggleTurboMode() {
        val newState = !_state.value.isTurboModeEnabled
        val context = getApplication<Application>()
        SettingsRepository.setTurboModeEnabled(context, newState)
        _state.update { it.copy(isTurboModeEnabled = newState) }
        RamMonitorService.start(context)
        applyGameTweaks()
    }

    fun dismissInstructions() { _state.update { it.copy(showPermissionInstructions = false) } }
    fun dismissWhatsNew() { _state.update { it.copy(showWhatsNew = false) } }

    fun toggleDisabledAppsDrawer(show: Boolean) { _state.update { it.copy(showDisabledAppsDrawer = show) } }
    fun setSelectionFilter(f: SelectionFilter) {  _state.update { it.copy(selectionFilter = f) } }

    fun requestBatteryOptimizationExemption() {
        val context = getApplication<Application>()
        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (_: Exception) {}
    }

    fun requestAllFilesAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, "package:${getApplication<Application>().packageName}".toUri())
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                getApplication<Application>().startActivity(intent)
            } catch (_: Exception) {}
        }
    }

    fun requestUsageAccess() {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            getApplication<Application>().startActivity(intent)
        } catch (_: Exception) {
            // Some devices might not support this directly or need a different approach
            try {
                intent.data = "package:${getApplication<Application>().packageName}".toUri()
                getApplication<Application>().startActivity(intent)
            } catch (_: Exception) {
                activityCallback?.showToast(getApplication<Application>().getString(R.string.usage_settings_error), Toast.LENGTH_SHORT)
            }
        }
    }

    private fun checkBatteryExemption(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    private fun ensureBatteryExemption(context: Context, root: Boolean, shizuku: Boolean) {
        if (checkBatteryExemption(context)) return

        viewModelScope.launch(Dispatchers.IO) {
            val pkg = context.packageName
            when {
                root -> RootCleaner.setBatteryOptimizationExempt(pkg, true)
                shizuku -> ShizukuCleaner.setBatteryOptimizationExempt(pkg, true)
            }
        }
    }
    fun requestWriteSettings() { try { getApplication<Application>().startActivity(AppRepository.writeSettingsIntent(getApplication()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {} }
    fun setFreezerSearchQuery(q: String) {  _state.update { it.copy(freezerSearchQuery = q) } }
    fun setFreezerFilter(f: FreezerFilter) {  _state.update { it.copy(freezerFilter = f) } }
    fun setSelectionSearchQuery(q: String) {  _state.update { it.copy(selectionSearchQuery = q) } }
    fun setTheme(t: AppTheme) {  SettingsRepository.setAppTheme(getApplication(), t.name); _state.update { it.copy(theme = t) } }

    fun optimizeVm() {
        viewModelScope.launch {
            val context = getApplication<Application>()
            _state.update { it.copy(isCleaning = true, cleaningTask = context.getString(R.string.native_vm_opt_task)) }
            withContext(Dispatchers.IO) {
                // Native level optimization
                NativeMemoryUtils.optimizeMemoryNative()
                NativeMemoryUtils.compactMemoryNative()
                NativeMemoryUtils.trimMallocNative()

                // VM level optimization
                VmRepository.triggerVmOptimization()
            }
            delay(100.milliseconds)
            _state.update { it.copy(isCleaning = false, cleaningTask = null) }
            refresh()
        }
    }

    fun launchDeveloperOptions() {
        try { getApplication<Application>().startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (_: Exception) { try { getApplication<Application>().startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {} }
    }

    fun toggleOverheatProtection() {
        val newState = !_state.value.isOverheatProtectionEnabled
        val context = getApplication<Application>()
        SettingsRepository.setOverheatProtectionEnabled(context, newState)
        _state.update { it.copy(isOverheatProtectionEnabled = newState) }
        if (newState) {
            RamMonitorService.start(context)
        }
    }

    /**
     * Polls PackageManager (a few cheap binder calls) until the package reaches the expected
     * frozen state. Replaces the old check that rebuilt the ENTIRE app list (usage stats,
     * icons, standby buckets...) after a fixed sleep - twice on the fallback path.
     */
    private suspend fun awaitFrozenState(pkg: String, expectedFrozen: Boolean, timeoutMs: Long = 1500L): Boolean =
        withContext(Dispatchers.IO) {
            val context = getApplication<Application>()
            val deadline = System.currentTimeMillis() + timeoutMs
            var ok = AppRepository.isPackageFrozenNow(context, pkg) == expectedFrozen
            while (!ok && System.currentTimeMillis() < deadline) {
                delay(50)
                ok = AppRepository.isPackageFrozenNow(context, pkg) == expectedFrozen
            }
            ok
        }

    // Freeze: fast command first, verify cheaply, escalate to the heavy chain only if needed
    private suspend fun freezeApp(pkg: String): Boolean {
        val ctx = getApplication<Application>()
        // Mark BEFORE running the command so Game Mode's restore step can never undo it.
        SettingsRepository.markUserFrozen(ctx, pkg, true)
        val ok = freezeAppRaw(pkg)
        if (!ok) SettingsRepository.markUserFrozen(ctx, pkg, false)
        return ok
    }

    private suspend fun freezeAppRaw(pkg: String): Boolean {
        val heavy = "cmd package suspend --user 0 $pkg; pm suspend --user 0 $pkg; cmd package disable-user --user 0 $pkg; pm disable-user --user 0 $pkg; am force-stop --user 0 $pkg"
        return when {
            RootCleaner.isRootAvailable() -> {
                try {
                    RootCleaner.freeze(pkg)
                    if (awaitFrozenState(pkg, true, 1500L)) true
                    else {
                        RootCleaner.runShell(heavy)
                        awaitFrozenState(pkg, true, 1500L)
                    }
                } catch (e: Exception) {
                    Log.e("CleanerVM", "Freeze failed for $pkg", e)
                    false
                }
            }
            ShizukuCleaner.isShizukuAvailable() && ShizukuCleaner.hasPermission() -> {
                try {
                    ShizukuCleaner.freeze(pkg)
                    if (awaitFrozenState(pkg, true, 1500L)) true
                    else {
                        ShizukuCleaner.exec(heavy)
                        awaitFrozenState(pkg, true, 1500L)
                    }
                } catch (e: Exception) {
                    Log.e("CleanerVM", "Shizuku freeze failed for $pkg", e)
                    false
                }
            }
            else -> {
                withContext(Dispatchers.Main) {
                    val context = getApplication<Application>()
                    val activity = activityCallback?.getActivity()
                    if (ShizukuHelper.isNotRunning(context) && activity != null) {
                        AppActionHandler(activity).showShizukuNotRunningDialog()
                    } else {
                        activityCallback?.showToast(context.getString(R.string.freeze_requires_access), Toast.LENGTH_LONG)
                    }
                }
                false
            }
        }
    }

    // Unfreeze: fast enable+unsuspend first; `pm install-existing` only if the app is still hidden
    private suspend fun unfreezeApp(pkg: String): Boolean {
        val ok = unfreezeAppRaw(pkg)
        if (ok) SettingsRepository.markUserFrozen(getApplication<Application>(), pkg, false)
        return ok
    }

    private suspend fun unfreezeAppRaw(pkg: String): Boolean {
        val restore = "pm install-existing --user 0 $pkg; cmd package enable --user 0 $pkg; pm enable --user 0 $pkg; cmd package unsuspend --user 0 $pkg; pm unsuspend --user 0 $pkg"
        return when {
            RootCleaner.isRootAvailable() -> {
                try {
                    RootCleaner.unfreeze(pkg)
                    if (awaitFrozenState(pkg, false, 1500L)) true
                    else {
                        RootCleaner.runShell(restore)
                        awaitFrozenState(pkg, false, 1500L)
                    }
                } catch (e: Exception) {
                    Log.e("CleanerVM", "Unfreeze failed for $pkg", e)
                    false
                }
            }
            ShizukuCleaner.isShizukuAvailable() && ShizukuCleaner.hasPermission() -> {
                try {
                    ShizukuCleaner.unfreeze(pkg)
                    if (awaitFrozenState(pkg, false, 1500L)) true
                    else {
                        ShizukuCleaner.exec(restore)
                        awaitFrozenState(pkg, false, 1500L)
                    }
                } catch (e: Exception) {
                    Log.e("CleanerVM", "Shizuku unfreeze failed for $pkg", e)
                    false
                }
            }
            else -> {
                withContext(Dispatchers.Main) {
                    val context = getApplication<Application>()
                    val activity = activityCallback?.getActivity()
                    if (ShizukuHelper.isNotRunning(context) && activity != null) {
                        AppActionHandler(activity).showShizukuNotRunningDialog()
                    } else {
                        activityCallback?.showToast(context.getString(R.string.unfreeze_requires_access), Toast.LENGTH_SHORT)
                    }
                }
                false
            }
        }
    }

    // 🔴 FIXED: toggleFreeze with proper error handling and UI updates
    fun toggleFreeze(pkg: String) {
        if (_state.value.processingApps.contains(pkg)) {
            activityCallback?.showToast(getApplication<Application>().getString(R.string.op_in_progress), Toast.LENGTH_SHORT)
            return
        }
        viewModelScope.launch {
            val context = getApplication<Application>()
            _state.update { it.copy(processingApps = it.processingApps + pkg) }

            // Get fresh app state before toggling
            val freshApp = AppRepository.getAppByPackageName(context, pkg)
            if (freshApp == null) {
                _state.update { it.copy(processingApps = it.processingApps - pkg) }
                activityCallback?.showToast(context.getString(R.string.app_not_found), Toast.LENGTH_SHORT)
                return@launch
            }

            val isActuallyFrozen = freshApp.isFrozen

            val activity = activityCallback?.getActivity()
            if (!isActuallyFrozen && activity != null) {
                val actionHandler = AppActionHandler(activity)
                if (actionHandler.isCriticalSystemApp(pkg)) {
                    val confirmed = actionHandler.showCriticalWarningDialog(pkg, "freeze")
                    if (!confirmed) {
                        _state.update { it.copy(processingApps = it.processingApps - pkg) }
                        return@launch
                    }
                }
            }

            val success = if (isActuallyFrozen) {
                unfreezeApp(pkg)
            } else {
                freezeApp(pkg)
            }

            // Update UI based on actual result
            withContext(Dispatchers.Main) {
                if (success) {
                    activityCallback?.showToast(
                        if (isActuallyFrozen) context.getString(R.string.app_unfrozen) else context.getString(R.string.app_frozen),
                        Toast.LENGTH_SHORT
                    )
                } else {
                    activityCallback?.showToast(
                        context.getString(if (isActuallyFrozen) R.string.unfreeze_failed else R.string.freeze_failed),
                        Toast.LENGTH_LONG
                    )
                }
            }

            // Flip the UI right away, then re-query in the background
            if (success) {
                // freeze = disabled + suspended; unfreeze clears both (and any "hidden" state)
                patchAppState(pkg, disabled = !isActuallyFrozen, suspended = !isActuallyFrozen,
                    hidden = if (isActuallyFrozen) false else null)
            }
            _state.update { it.copy(processingApps = it.processingApps - pkg) }
            refreshApp(pkg)
        }
    }

    // 🔴 FIXED: freezeAll with verification
    fun freezeAll() {
        if (_state.value.isCleaning) {
            activityCallback?.showToast(getApplication<Application>().getString(R.string.op_in_progress), Toast.LENGTH_SHORT)
            return
        }
        val context = getApplication<Application>()
        val whitelist = SettingsRepository.getUserWhitelist(context)
        val activity = activityCallback?.getActivity()
        val actionHandler = activity?.let { AppActionHandler(it) }

        val targets = _state.value.apps.filter { app ->
            !app.isFrozen &&
            app.packageName !in com.theblacksheep.appoff.core.PROTECTED_PACKAGES &&
            app.packageName !in whitelist &&
            (actionHandler == null || !actionHandler.isCriticalSystemApp(app.packageName))
        }.map { it.packageName }

        if (targets.isEmpty()) {
            activityCallback?.showToast(context.getString(R.string.all_apps_frozen), Toast.LENGTH_SHORT)
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isCleaning = true, cleaningTask = context.getString(R.string.freezing_apps_task)) }

            var successCount = 0
            val total = targets.size

            // Parallelize freezing: chunks of 10
            targets.chunked(10).forEach { chunk ->
                val jobs = chunk.map { pkg ->
                    async {
                        _state.update { it.copy(cleaningPackage = pkg) }
                        val result = freezeApp(pkg)
                        if (result) successCount++
                        result
                    }
                }
                jobs.awaitAll()
            }

            _state.update { it.copy(isCleaning = false, cleaningPackage = null, cleaningTask = null) }

            withContext(Dispatchers.Main) {
                activityCallback?.showToast(context.getString(R.string.apps_frozen_count, successCount, total), Toast.LENGTH_SHORT)
            }

            delay(100.milliseconds)
            refresh()
        }
    }

    // 🔴 FIXED: unfreezeAll with verification
    fun unfreezeAll() {
        if (_state.value.isCleaning) {
            activityCallback?.showToast(getApplication<Application>().getString(R.string.op_in_progress), Toast.LENGTH_SHORT)
            return
        }
        viewModelScope.launch {
            val context = getApplication<Application>()
            val targets = AppRepository.getAllFrozenPackages(context)
            if (targets.isEmpty()) {
                withContext(Dispatchers.Main) {
                    activityCallback?.showToast(context.getString(R.string.no_frozen_apps_found), Toast.LENGTH_SHORT)
                }
                return@launch
            }

            _state.update { it.copy(isCleaning = true, cleaningTask = context.getString(R.string.unfreezing_apps_task)) }

            var successCount = 0
            val total = targets.size

            // Parallelize unfreezing: chunks of 12
            targets.chunked(12).forEach { chunk ->
                val jobs = chunk.map { pkg ->
                    async {
                        _state.update { it.copy(cleaningPackage = pkg) }
                        val result = unfreezeApp(pkg)
                        if (result) successCount++
                        result
                    }
                }
                jobs.awaitAll()
            }

            _state.update { it.copy(isCleaning = false, cleaningPackage = null, cleaningTask = null) }

            withContext(Dispatchers.Main) {
                activityCallback?.showToast(context.getString(R.string.apps_unfrozen_count, successCount, total), Toast.LENGTH_SHORT)
            }

            delay(100.milliseconds)
            refresh()
        }
    }

    // 🔴 FIXED: suspendApp with error handling
    fun suspendApp(packageName: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            activityCallback?.showToast(getApplication<Application>().getString(R.string.suspend_requires_pie), Toast.LENGTH_LONG)
            return
        }

        val activity = activityCallback?.getActivity() ?: run {
            activityCallback?.showToast(getApplication<Application>().getString(R.string.activity_not_available), Toast.LENGTH_SHORT)
            return
        }

        val context = getApplication<Application>()

        viewModelScope.launch {
            val result = AppActionHandler(activity).suspendApp(packageName)
            if (result.success) {
                SettingsRepository.markAppSuspended(context, packageName, true)
                patchAppState(packageName, suspended = true)
                activityCallback?.showToast(context.getString(R.string.app_suspended), Toast.LENGTH_SHORT)
            } else if (result.message != "Canceled by user") {
                val msg = result.message ?: context.getString(R.string.operation_failed)
                activityCallback?.showToast("✗ $msg", Toast.LENGTH_LONG)
            }
            refreshApp(packageName)
        }
    }

    fun unsuspendApp(packageName: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            activityCallback?.showToast(getApplication<Application>().getString(R.string.unsuspend_requires_pie), Toast.LENGTH_LONG)
            return
        }

        val activity = activityCallback?.getActivity() ?: run {
            activityCallback?.showToast(getApplication<Application>().getString(R.string.activity_not_available), Toast.LENGTH_SHORT)
            return
        }

        val context = getApplication<Application>()

        viewModelScope.launch {
            val result = AppActionHandler(activity).unsuspendApp(packageName)
            if (result.success) {
                SettingsRepository.markAppSuspended(context, packageName, false)
                patchAppState(packageName, suspended = false)
                activityCallback?.showToast(context.getString(R.string.app_unsuspended), Toast.LENGTH_SHORT)
            } else if (result.message != "Canceled by user") {
                val msg = result.message ?: context.getString(R.string.operation_failed)
                activityCallback?.showToast("✗ $msg", Toast.LENGTH_LONG)
            }
            refreshApp(packageName)
        }
    }

    fun disableApp(packageName: String) {
        val activity = activityCallback?.getActivity() ?: run {
            activityCallback?.showToast(getApplication<Application>().getString(R.string.activity_not_available), Toast.LENGTH_SHORT)
            return
        }

        val context = getApplication<Application>()

        viewModelScope.launch {
            SettingsRepository.markUserFrozen(context, packageName, true) // before the command
            val result = AppActionHandler(activity).disableApp(packageName)
            if (!result.success) SettingsRepository.markUserFrozen(context, packageName, false)
            if (result.success) {
                SettingsRepository.markAppDisabled(context, packageName, true)
                patchAppState(packageName, disabled = true)
                activityCallback?.showToast(context.getString(R.string.app_disabled), Toast.LENGTH_SHORT)
            } else if (result.message != "Canceled by user") {
                val msg = result.message ?: context.getString(R.string.operation_failed)
                activityCallback?.showToast("✗ $msg", Toast.LENGTH_LONG)
            }
            refreshApp(packageName)
        }
    }

    fun enableApp(packageName: String) {
        val activity = activityCallback?.getActivity() ?: run {
            activityCallback?.showToast(getApplication<Application>().getString(R.string.activity_not_available), Toast.LENGTH_SHORT)
            return
        }

        val context = getApplication<Application>()

        viewModelScope.launch {
            val result = AppActionHandler(activity).enableApp(packageName)
            if (result.success) {
                SettingsRepository.markUserFrozen(context, packageName, false)
                SettingsRepository.markAppDisabled(context, packageName, false)
                patchAppState(packageName, disabled = false)
                activityCallback?.showToast(context.getString(R.string.app_enabled), Toast.LENGTH_SHORT)
            } else if (result.message != "Canceled by user") {
                val msg = result.message ?: context.getString(R.string.operation_failed)
                activityCallback?.showToast("✗ $msg", Toast.LENGTH_LONG)
            }
            refreshApp(packageName)
        }
    }

    fun toggleAppSuspension(pkg: String, s: Boolean) {
        if (s) suspendApp(pkg) else unsuspendApp(pkg)
    }

    // 🔴 FIXED: forceStopApp with error handling and whitelist check
    fun forceStopApp(pkg: String) {
        val isWhitelisted = _state.value.userWhitelist.contains(pkg)
        if (isWhitelisted) {
            val context = getApplication<Application>()
            activityCallback?.showToast(context.getString(R.string.shielded_protection), Toast.LENGTH_SHORT)
            return
        }

        val context = getApplication<Application>()
        // Optimistically remove force-stopped app from topDrainers immediately (0ms)
        _state.update { s ->
            s.copy(topDrainers = s.topDrainers.filter { it.packageName != pkg })
        }

        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) {
                try {
                    when {
                        RootCleaner.isRootAvailable() -> RootCleaner.forceStopAdvanced(pkg)
                        ShizukuCleaner.hasPermission() -> ShizukuCleaner.forceStop(pkg)
                        else -> StandardCleaner.clean(context, listOf(pkg), cacheOnly = false, onProgress = {}, onTaskUpdate = {}).succeeded > 0
                    }
                } catch (e: Exception) {
                    Log.e("CleanerVM", "Force stop failed for $pkg", e)
                    false
                }
            }

            withContext(Dispatchers.Main) {
                if (success) {
                    activityCallback?.showToast(context.getString(R.string.app_force_stopped), Toast.LENGTH_SHORT)
                } else {
                    activityCallback?.showToast(context.getString(R.string.force_stop_failed), Toast.LENGTH_LONG)
                }
            }
            refreshApp(pkg)
        }
    }

    // 🔴 FIXED: setAppOp with error handling
    fun setAppOp(p: String, o: Int, m: Int) {
        if (_state.value.processingApps.contains(p)) {
            activityCallback?.showToast(getApplication<Application>().getString(R.string.op_in_progress), Toast.LENGTH_SHORT)
            return
        }
        viewModelScope.launch {
            val context = getApplication<Application>()
            _state.update { it.copy(processingApps = it.processingApps + p) }

            val success = withContext(Dispatchers.IO) {
                try {
                    when {
                        RootCleaner.isRootAvailable() -> RootCleaner.setAppOp(p, o, m)
                        ShizukuCleaner.hasPermission() -> ShizukuCleaner.setAppOp(p, o, m)
                        else -> false
                    }
                } catch (e: Exception) {
                    Log.e("CleanerVM", "SetAppOp failed for $p", e)
                    false
                }
            }

            refreshApp(p)

            withContext(Dispatchers.Main) {
                if (success) {
                    val message = if (m != 0) context.getString(R.string.bg_run_ignored) else context.getString(R.string.bg_run_allowed)
                    activityCallback?.showToast(message, Toast.LENGTH_SHORT)
                } else {
                    val activity = activityCallback?.getActivity()
                    if (activity != null && !RootCleaner.isRootAvailable() && !ShizukuCleaner.hasPermission()) {
                        AppActionHandler(activity).showShizukuRequiredDialog()
                    } else {
                        activityCallback?.showToast(context.getString(R.string.bg_update_failed), Toast.LENGTH_LONG)
                    }
                }
            }
            _state.update { it.copy(processingApps = it.processingApps - p) }
        }
    }

    // 🔴 FIXED: setStandbyBucket with error handling
    fun setStandbyBucket(p: String, b: Int) {
        if (_state.value.processingApps.contains(p)) {
            activityCallback?.showToast(getApplication<Application>().getString(R.string.op_in_progress), Toast.LENGTH_SHORT)
            return
        }
        viewModelScope.launch {
            val context = getApplication<Application>()
            _state.update { it.copy(processingApps = it.processingApps + p) }

            val success = withContext(Dispatchers.IO) {
                try {
                    when {
                        RootCleaner.isRootAvailable() -> RootCleaner.setStandbyBucket(p, b)
                        ShizukuCleaner.hasPermission() -> ShizukuCleaner.setStandbyBucket(p, b)
                        else -> false
                    }
                } catch (e: Exception) {
                    Log.e("CleanerVM", "SetStandbyBucket failed for $p", e)
                    false
                }
            }

            withContext(Dispatchers.Main) {
                if (success) {
                    val label = when (b) {
                        10 -> "Active"
                        40 -> "Rare"
                        45 -> "Restricted"
                        else -> "Updated"
                    }
                    activityCallback?.showToast(context.getString(R.string.standby_label, label), Toast.LENGTH_SHORT)
                } else {
                    activityCallback?.showToast(context.getString(R.string.standby_update_failed), Toast.LENGTH_LONG)
                }
            }

            refreshApp(p)
            _state.update { it.copy(processingApps = it.processingApps - p) }
        }
    }

    // 🔴 FIXED: clearCacheApp with error handling
    fun clearCacheApp(pkg: String) {
        if (_state.value.processingApps.contains(pkg)) {
            activityCallback?.showToast(getApplication<Application>().getString(R.string.op_in_progress), Toast.LENGTH_SHORT)
            return
        }
        viewModelScope.launch {
            val context = getApplication<Application>()
            _state.update { it.copy(isCleaning = true, cleaningTask = context.getString(R.string.clearing_cache_task), processingApps = it.processingApps + pkg) }

            val success = withContext(Dispatchers.IO) {
                try {
                    when {
                        RootCleaner.isRootAvailable() -> RootCleaner.clearCache(pkg)
                        ShizukuCleaner.hasPermission() -> ShizukuCleaner.clearCache(pkg)
                        else -> {
                            // Try standard cache clear via package manager
                            val pm = context.packageManager
                            try {
                                @Suppress("DEPRECATION", "JAVA_CLASS_ON_COMPARED_CLASSES")
                                val method = pm.javaClass.getMethod("deleteApplicationCacheFiles", String::class.java, Class.forName("android.content.pm.IPackageDataObserver"))
                                method.invoke(pm, pkg, null)
                                true
                            } catch (_: Exception) { false }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("CleanerVM", "Clear cache failed for $pkg", e)
                    false
                }
            }

            withContext(Dispatchers.Main) {
                if (success) {
                    activityCallback?.showToast(context.getString(R.string.cache_cleared, pkg), Toast.LENGTH_SHORT)
                } else {
                    activityCallback?.showToast(context.getString(R.string.cache_clear_failed, pkg), Toast.LENGTH_LONG)
                }
            }

            _state.update { it.copy(isCleaning = false, cleaningTask = null, processingApps = it.processingApps - pkg) }
            refresh()
        }
    }

    fun uninstallApp(pkg: String) {
        val activity = activityCallback?.getActivity()
        val context = getApplication<Application>()
        viewModelScope.launch {
            _state.update { it.copy(isCleaning = true, cleaningTask = context.getString(R.string.uninstalling_app_task)) }
            val result = if (activity != null) {
                AppActionHandler(activity).uninstallApp(pkg)
            } else {
                val success = when {
                    RootCleaner.isRootAvailable() -> RootCleaner.uninstall(pkg)
                    ShizukuCleaner.hasPermission() -> ShizukuCleaner.uninstall(pkg)
                    else -> {
                        try {
                            val intent = Intent(Intent.ACTION_DELETE, "package:$pkg".toUri())
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                            true
                        } catch (_: Exception) {
                            false
                        }
                    }
                }
                AppActionHandler.ActionResult(success, if (success) "Uninstall initiated" else "Uninstall failed")
            }

            _state.update { it.copy(isCleaning = false, cleaningTask = null) }

            if (result.success) {
                SettingsRepository.markUserFrozen(context, pkg, true)
                SettingsRepository.markAppDisabled(context, pkg, true)
                patchAppState(pkg, uninstalled = true, hidden = true, disabled = true)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, context.getString(R.string.uninstall_success), Toast.LENGTH_SHORT).show()
                }
            } else if (result.message != "Canceled by user") {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, context.getString(R.string.uninstall_failed), Toast.LENGTH_SHORT).show()
                }
            }
            refreshApp(pkg)
        }
    }

    fun reinstallApp(pkg: String) {
        val activity = activityCallback?.getActivity()
        val context = getApplication<Application>()
        viewModelScope.launch {
            _state.update { it.copy(isCleaning = true, cleaningTask = context.getString(R.string.reinstalling_app_task)) }
            val result = if (activity != null) {
                AppActionHandler(activity).reinstallApp(pkg)
            } else {
                val cmd = "cmd package install-existing --user 0 $pkg; pm install-existing --user 0 $pkg; cmd package enable --user 0 $pkg; cmd package unhide --user 0 $pkg"
                val ok = when {
                    RootCleaner.isRootAvailable() -> RootCleaner.runShell(cmd)
                    ShizukuCleaner.hasPermission() -> !ShizukuCleaner.exec(cmd).startsWith("Error:")
                    else -> false
                }
                AppActionHandler.ActionResult(ok, if (ok) "App reinstalled successfully" else "Failed to reinstall app")
            }

            _state.update { it.copy(isCleaning = false, cleaningTask = null) }

            if (result.success) {
                SettingsRepository.markUserFrozen(context, pkg, false)
                SettingsRepository.markAppDisabled(context, pkg, false)
                patchAppState(pkg, disabled = false, hidden = false, uninstalled = false)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, context.getString(R.string.app_reinstalled), Toast.LENGTH_SHORT).show()
                }
            } else if (result.message != "Canceled by user") {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, result.message ?: context.getString(R.string.reinstall_failed), Toast.LENGTH_SHORT).show()
                }
            }
            refreshApp(pkg)
        }
    }

    // 🔴 FIXED: toggleWhitelist with processing guard
    fun toggleWhitelist(pkg: String) {
        if (_state.value.processingApps.contains(pkg)) return
        val context = getApplication<Application>()
        SettingsRepository.toggleWhitelistApp(context, pkg)
        _state.update { it.copy(userWhitelist = SettingsRepository.getUserWhitelist(context)) }
        refresh()
    }

    fun applyBeginnerSafeList() {
        viewModelScope.launch {
            val context = getApplication<Application>()
            val installedApps = _state.value.apps
            val safePackages = BeginnerSafeList.getInstalledBeginnerSafePackages(context, installedApps)

            if (safePackages.isEmpty()) {
                activityCallback?.showToast(context.getString(R.string.beginner_safelist_no_apps_found), Toast.LENGTH_SHORT)
                return@launch
            }

            val addedCount = SettingsRepository.addAppsToWhitelist(context, safePackages)
            val updatedWhitelist = SettingsRepository.getUserWhitelist(context)

            val updatedWhitelistedApps = AppRepository.getAppsByPackageNames(context, updatedWhitelist.toList())

            _state.update { s ->
                s.copy(
                    userWhitelist = updatedWhitelist,
                    whitelistedApps = updatedWhitelistedApps,
                    apps = s.apps.map { app ->
                        if (updatedWhitelist.contains(app.packageName)) app.copy(selected = false) else app
                    }
                )
            }

            val msg = if (addedCount > 0) {
                context.getString(R.string.beginner_safelist_added_count, addedCount)
            } else {
                context.getString(R.string.beginner_safelist_already_applied)
            }
            activityCallback?.showToast(msg, Toast.LENGTH_LONG)
        }
    }

    fun toggleFastAnimations() {
        val context = getApplication<Application>()
        val newState = !_state.value.fastAnimationsEnabled
        val s = if (newState) 0.5f else 1.0f
        try {
            Settings.Global.putFloat(context.contentResolver, Settings.Global.WINDOW_ANIMATION_SCALE, s)
            Settings.Global.putFloat(context.contentResolver, Settings.Global.TRANSITION_ANIMATION_SCALE, s)
            Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, s)
        } catch (_: Exception) {}
        SettingsRepository.setFastAnimationsEnabled(context, newState)
        _state.update { it.copy(fastAnimationsEnabled = newState) }
        applyGameTweaks()
    }

    fun toggleHardwareAcceleration() {
        val context = getApplication<Application>()
        val newState = !_state.value.hardwareAccelerationEnabled
        try {
            Settings.Global.putString(context.contentResolver, "debug.hwui.renderer", if (newState) "OpenGL" else "default")
        } catch (_: Exception) {}
        SettingsRepository.setHardwareAccelerationEnabled(context, newState)
        _state.update { it.copy(hardwareAccelerationEnabled = newState) }
        applyGameTweaks()
    }

    private suspend fun getRunningPackages(): Set<String> = withContext(Dispatchers.IO) {
        val running = mutableSetOf<String>()

        // 1. Try Privileged Methods (Best Accuracy)
        val psOutput = when {
            RootCleaner.isRootAvailable() -> RootCleaner.runShellWithOutput("ps -A")
            ShizukuCleaner.hasPermission() -> ShizukuCleaner.exec("ps -A")
            else -> null
        }

        if (psOutput != null && psOutput != "Error: Service not available") {
            val lines = psOutput.split("\n")
            lines.forEach { line ->
                val columns = line.trim().split(Regex("\\s+"))
                if (columns.size > 8) {
                    val name = columns.last()
                    // Process names like com.example.app:remote -> com.example.app
                    val pkg = if (name.contains(":")) name.substringBefore(":") else name
                    if (pkg.contains(".")) running.add(pkg)
                }
            }
            if (running.isNotEmpty()) return@withContext running
        }

        // 2. Fallback: UsageStatsManager (Accurate recently used apps)
        val context = getApplication<Application>()
        if (AppRepository.hasUsageAccess(context)) {
            try {
                val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
                val now = System.currentTimeMillis()
                // Check last 15 minutes of activity
                val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_BEST, now - 900000, now)
                stats?.forEach {
                    if (it.lastTimeUsed > (now - 900000)) {
                        running.add(it.packageName)
                    }
                }
            } catch (_: Exception) {}
        }

        // 3. Fallback: Running Services (Works for background apps with services)
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            @Suppress("DEPRECATION")
            am.getRunningServices(512)?.forEach {
                running.add(it.service.packageName)
            }

            // Also include self-reported running processes (usually just our own, but good to have)
            am.runningAppProcesses?.forEach { proc ->
                proc.pkgList?.forEach { running.add(it) }
            }
        } catch (_: Exception) {}

        running
    }

    private suspend fun getPackageMemoryUsage(): Map<String, Long> = withContext(Dispatchers.IO) {
        val memoryMap = mutableMapOf<String, Long>()

        // 1. Try Privileged Methods (Comprehensive list)
        val output = when {
            RootCleaner.isRootAvailable() -> RootCleaner.runShellWithOutput("dumpsys meminfo --packages")
            ShizukuCleaner.hasPermission() -> ShizukuCleaner.exec("dumpsys meminfo --packages")
            else -> null
        }

        if (output != null) {
            val lines = output.split("\n")
            // More robust regex to match: "  123,456K: com.example.app (pid 123)" or "  123,456K: com.example.app"
            val regex = Regex("\\s*([\\d,]+)K:\\s+([^\\s(]+)")

            for (line in lines) {
                val match = regex.find(line)
                if (match != null) {
                    val pssStr = match.groupValues[1].replace(",", "")
                    val pkg = match.groupValues[2]
                    val pss = pssStr.toLongOrNull() ?: 0L
                    // If multiple processes for same package, add them up
                    val current = memoryMap[pkg] ?: 0L
                    memoryMap[pkg] = current + (pss * 1024L)
                }
            }
        }

        // 2. Fallback: Own app memory usage (Always available) + limited process info if available
        val am = getApplication<Application>().getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

        if (!memoryMap.containsKey(getApplication<Application>().packageName)) {
            try {
                val ownMem = am.getProcessMemoryInfo(intArrayOf(Process.myPid()))
                if (ownMem.isNotEmpty()) {
                    memoryMap[getApplication<Application>().packageName] = ownMem[0].totalPss.toLong() * 1024L
                }
            } catch (_: Exception) {}
        }

        // On older Android or for visible processes, try to fill more
        if (memoryMap.size <= 1) {
            try {
                am.runningAppProcesses?.forEach { proc ->
                    if (!memoryMap.containsKey(proc.processName)) {
                        val memInfo = am.getProcessMemoryInfo(intArrayOf(proc.pid))
                        if (memInfo.isNotEmpty()) {
                            memoryMap[proc.processName] = memInfo[0].totalPss.toLong() * 1024L
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        memoryMap
    }

    // --- SYSTEM ANALYSIS & AUDIT ---

    fun analyzeProcesses() {
        viewModelScope.launch {
            _state.update { it.copy(isAnalyzingProcesses = true) }
            val context = getApplication<Application>()
            val pm = context.packageManager
            val analysis = withContext(Dispatchers.IO) {
                val output = when {
                    RootCleaner.isRootAvailable() -> RootCleaner.runShellWithOutput("dumpsys activity processes")
                    ShizukuCleaner.hasPermission() -> ShizukuCleaner.exec("dumpsys activity processes")
                    else -> null
                }

                if (output != null && !output.contains("Error")) {
                    parseProcessAnalysis(output, pm)
                } else {
                    fallbackProcessAnalysis(context, pm)
                }
            }
            _state.update { it.copy(processAnalysis = analysis, isAnalyzingProcesses = false) }
        }
    }

    private data class RawProcessRecord(val name: String, val pid: Int, val oom: Int, val adj: String)

    private fun parseProcessAnalysis(output: String, pm: PackageManager): List<ProcessAnalysisInfo> {
        val rawList = mutableListOf<RawProcessRecord>()
        val lines = output.split("\n")
        var currentName = ""
        var currentPid = 0
        var currentOom = 0
        var currentAdj = ""

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("Process record for Process{")) {
                val match = Regex("Process\\{[^ ]+ (\\d+):([^/]+)/").find(trimmed)
                if (match != null) {
                    if (currentName.isNotEmpty()) {
                        rawList.add(RawProcessRecord(currentName, currentPid, currentOom, currentAdj))
                    }
                    currentPid = match.groupValues[1].toIntOrNull() ?: 0
                    currentName = match.groupValues[2]
                    currentOom = 0
                    currentAdj = ""
                }
            } else if (trimmed.startsWith("oom:")) {
                val match = Regex("cur=(\\d+)").find(trimmed)
                if (match != null) currentOom = match.groupValues[1].toIntOrNull() ?: 0
            } else if (trimmed.startsWith("adj:")) {
                val match = Regex("cur=(\\d+)").find(trimmed)
                if (match != null) currentAdj = match.groupValues[1]
            }
        }
        if (currentName.isNotEmpty()) {
            rawList.add(RawProcessRecord(currentName, currentPid, currentOom, currentAdj))
        }

        return rawList.map { raw ->
            buildProcessInfo(raw.name, raw.pid, raw.oom, raw.adj, pm)
        }.sortedBy { it.oomScore }
    }

    private fun fallbackProcessAnalysis(context: Context, pm: PackageManager): List<ProcessAnalysisInfo> {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val processes = am.runningAppProcesses ?: return emptyList()
        return processes.map {
            buildProcessInfo(it.processName, it.pid, it.importance, it.importance.toString(), pm)
        }.sortedBy { it.oomScore }
    }

    private fun buildProcessInfo(
        processName: String,
        pid: Int,
        oomScore: Int,
        adjStr: String,
        pm: PackageManager
    ): ProcessAnalysisInfo {
        val pkgName = processName.split(":").first()
        var label = processName
        var isSystem = processName.startsWith("com.android") || processName.startsWith("android") || processName.startsWith("system")

        try {
            val appInfo = pm.getApplicationInfo(pkgName, 0)
            label = pm.getApplicationLabel(appInfo).toString()
            isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        } catch (_: Exception) {}

        val humanAdj = when {
            adjStr.contains("FORE") || oomScore == 0 || oomScore == 100 -> "FOREGROUND"
            adjStr.contains("VIS") || oomScore in 101..200 -> "VISIBLE"
            adjStr.contains("PERC") || oomScore in 201..400 -> "PERCEPTIBLE"
            adjStr.contains("SVC") || oomScore in 401..700 -> "SERVICE"
            adjStr.contains("CACH") || oomScore >= 700 -> "CACHED"
            else -> adjStr.ifBlank { "BACKGROUND" }
        }

        return ProcessAnalysisInfo(
            name = processName,
            label = label,
            pid = pid,
            oomScore = oomScore,
            adj = humanAdj,
            memory = "Active",
            memoryBytes = 0L,
            isSystemApp = isSystem,
            packageName = pkgName
        )
    }

    fun killProcess(pid: Int, packageName: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            try {
                if (!packageName.isNullOrBlank()) {
                    forceStopApp(packageName)
                }
                if (RootCleaner.isRootAvailable()) {
                    RootCleaner.runShell("kill -9 $pid")
                } else if (ShizukuCleaner.hasPermission()) {
                    ShizukuCleaner.exec("kill -9 $pid")
                } else {
                    val am = app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                    if (!packageName.isNullOrBlank()) am.killBackgroundProcesses(packageName)
                    Process.killProcess(pid)
                }
            } catch (_: Exception) {}
            delay(300)
            analyzeProcesses()
        }
    }

    fun killSelectedProcesses(items: List<Pair<Int, String?>>) {
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            val am = app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            items.forEach { (pid, pkg) ->
                try {
                    if (!pkg.isNullOrBlank()) {
                        forceStopApp(pkg)
                    }
                    if (RootCleaner.isRootAvailable()) {
                        RootCleaner.runShell("kill -9 $pid")
                    } else if (ShizukuCleaner.hasPermission()) {
                        ShizukuCleaner.exec("kill -9 $pid")
                    } else {
                        if (!pkg.isNullOrBlank()) am.killBackgroundProcesses(pkg)
                        Process.killProcess(pid)
                    }
                } catch (_: Exception) {}
            }
            delay(400)
            analyzeProcesses()
        }
    }

    fun auditSystemHealth() {
        viewModelScope.launch {
            _state.update { it.copy(isAuditingSystem = true) }
            val logs = withContext(Dispatchers.IO) {
                val output = when {
                    RootCleaner.isRootAvailable() -> RootCleaner.runShellWithOutput("logcat -d -t 100 *:E")
                    ShizukuCleaner.hasPermission() -> ShizukuHelper.runCommand("logcat -d -t 100 *:E")
                    else -> {
                        try {
                            val process = Runtime.getRuntime().exec("logcat -d -t 100 *:E")
                            val reader = BufferedReader(InputStreamReader(process.inputStream))
                            val result = mutableListOf<String>()
                            var line: String?
                            while (reader.readLine().also { line = it } != null) {
                                line?.let { result.add(it) }
                            }
                            result.joinToString("\n")
                        } catch (_: Exception) { "" }
                    }
                }
                output?.split("\n")?.filter { it.isNotBlank() } ?: emptyList()
            }
            _state.update { it.copy(systemHealthLogs = logs, isAuditingSystem = false) }
        }
    }

    fun updateNetworkPing() {
        viewModelScope.launch(Dispatchers.IO) {
            val ping = StatsRepository.ping()
            _state.update { it.copy(pingMs = ping) }
        }
    }

    fun refreshScreenTime() {
        viewModelScope.launch(Dispatchers.IO) {
            val stats = StatsRepository.getScreenTimeStats(getApplication())
            _state.update { it.copy(screenTimeStats = stats) }
        }
    }

    fun setFontScale(scale: Float) {
        SettingsRepository.setFontScale(getApplication(), scale)
        _state.update { it.copy(fontScale = scale) }
    }

    fun setIconStyle(style: String) {
        SettingsRepository.setIconStyle(getApplication(), style)
        _state.update { it.copy(iconStyle = style) }
    }

    fun toggleDeveloperOptions() {
        val context = getApplication<Application>()
        val st = _state.value
        if (!st.canWriteSecureSettings && !st.rootAvailable && !st.shizukuGranted) {
            _state.update { it.copy(showPermissionInstructions = true) }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val newState = !_state.value.isDeveloperOptionsEnabled
            val cmd = "settings put global development_settings_enabled ${if (newState) 1 else 0}"
            val success = when {
                _state.value.canWriteSecureSettings -> {
                    try {
                        Settings.Global.putInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, if (newState) 1 else 0)
                        true
                    } catch (_: Exception) { false }
                }
                _state.value.rootAvailable -> { RootCleaner.runShell(cmd); true }
                _state.value.shizukuGranted -> { ShizukuHelper.runCommand(cmd); true }
                else -> false
            }
            if (success) {
                _state.update { it.copy(isDeveloperOptionsEnabled = newState) }
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, context.getString(R.string.operation_failed), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun toggleWirelessDebug() {
        val context = getApplication<Application>()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Toast.makeText(context, context.getString(R.string.requires_android_11), Toast.LENGTH_SHORT).show()
            return
        }
        val st = _state.value
        if (!st.canWriteSecureSettings && !st.rootAvailable && !st.shizukuGranted) {
            _state.update { it.copy(showPermissionInstructions = true) }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val newState = !_state.value.isWirelessDebugEnabled
            val cmd = "settings put global adb_wifi_enabled ${if (newState) 1 else 0}"
            val success = when {
                _state.value.canWriteSecureSettings -> {
                    try {
                        Settings.Global.putInt(context.contentResolver, "adb_wifi_enabled", if (newState) 1 else 0)
                        true
                    } catch (_: Exception) { false }
                }
                _state.value.rootAvailable -> { RootCleaner.runShell(cmd); true }
                _state.value.shizukuGranted -> { ShizukuHelper.runCommand(cmd); true }
                else -> false
            }
            if (success) {
                _state.update { it.copy(isWirelessDebugEnabled = newState) }
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, context.getString(R.string.operation_failed), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun CleanResult.withLabels(): CleanResult {
        val currentApps = _state.value.apps
        val labels = succeededPackages.map { pkg ->
            currentApps.find { it.packageName == pkg }?.label ?: pkg.split(".").last()
        }
        return copy(succeededLabels = labels)
    }
}

private data class RefreshData(
    val ram: RamSnapshot,
    val hasUsage: Boolean,
    val systemPrivileged: Boolean,
    val canWriteSecureSettings: Boolean,
    val canWriteSettings: Boolean,
    val isShizukuInstalled: Boolean,
    val shizukuAvailable: Boolean,
    val shizukuGranted: Boolean,
    val hasAllFilesAccess: Boolean,
    val rootAvailable: Boolean,
    val isBatteryOptimizationsIgnored: Boolean,
    val vmStats: VmStats?,
    val topDrainers: List<CleanableApp>,
    val games: List<CleanableApp>,
    val userWhitelist: Set<String>,
    val whitelistedApps: List<CleanableApp>,
    val boosterInstalled: Boolean,
    val isMiui: Boolean,
    val isTurboModeEnabled: Boolean,
    val frozenCount: Int,
    val shellEngine: String,
    val binderStatus: String
)
