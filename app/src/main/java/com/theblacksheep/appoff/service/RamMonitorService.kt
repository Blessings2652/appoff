package com.theblacksheep.appoff.service

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.theblacksheep.appoff.CleanerApp
import com.theblacksheep.appoff.MainActivity
import com.theblacksheep.appoff.R
import com.theblacksheep.appoff.core.*
import com.theblacksheep.appoff.root.RootCleaner
import com.theblacksheep.appoff.shizuku.ShizukuCleaner
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

@Suppress("SameParameterValue")
class RamMonitorService : Service() {

    @Volatile private var gamingTargetPackage: String? = null
    private var tempDisabledPackages = ConcurrentHashMap.newKeySet<String>()
    private val sessionLock = Mutex()
    private var nonGameTicks = 0
    // Survives scope.cancel() so onDestroy can still restore apps.
    private val restoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun persistTempDisabled() {
        SettingsRepository.setTempDisabledApps(applicationContext, tempDisabledPackages.toSet())
    }

    // Foreground packages that must NOT be treated as "left the game": our own UI, launcher,
    // SystemUI and the keyboard. Opening this app to freeze something used to end the session
    // and re-enable everything.
    private fun isTransientForeground(pkg: String): Boolean {
        if (pkg == packageName || pkg == "com.android.systemui") return true
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val launcher = packageManager.resolveActivity(home, 0)?.activityInfo?.packageName
        if (pkg == launcher) return true
        val ime = android.provider.Settings.Secure.getString(
            contentResolver, android.provider.Settings.Secure.DEFAULT_INPUT_METHOD
        )?.substringBefore('/')
        return pkg == ime
    }

    override fun onCreate() {
        super.onCreate()
        // Recover apps left disabled if a previous session was killed before it could restore.
        tempDisabledPackages.addAll(SettingsRepository.getTempDisabledApps(applicationContext))
        if (tempDisabledPackages.isNotEmpty() && gamingTargetPackage == null) {
            restoreScope.launch { restoreBackgroundApps(silent = true) }
        }
    }

    companion object {
        private const val NOTIF_ID = 42
        private const val ACTION_QUICK_CLEAN = "com.theblacksheep.appoff.action.QUICK_CLEAN"
        private const val ACTION_START_GAME = "com.theblacksheep.appoff.action.START_GAME"
        private const val EXTRA_PKG = "extra_package_name"
        
        private fun getRefreshMs(context: Context): Long {
            return if (SettingsRepository.isTurboModeEnabled(context)) 3000L else 10000L
        }
        
        private const val AUTO_CLEAN_COOLDOWN_MS = 60000L * 3 // 3 minutes cooldown

        @SuppressLint("ObsoleteSdkInt")
        fun start(context: Context) {
            val intent = Intent(context, RamMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun startGameSession(context: Context, pkg: String) {
            start(context)
            val intent = Intent(context, RamMonitorService::class.java).apply {
                action = ACTION_START_GAME
                putExtra(EXTRA_PKG, pkg)
            }
            context.startService(intent)
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.Default)
    private var lastAutoCleanTime = 0L

    private val ticker = object : Runnable {
        override fun run() {
            scope.launch {
                updateNotification()
                checkAutoClean()
                checkAutoKill()
                detectAndBoostGame()
            }
            handler.postDelayed(this, getRefreshMs(applicationContext))
        }
    }

    private fun checkAutoKill() {
        val context = applicationContext
        val autoKillBg = SettingsRepository.isAutoKillBackgroundEnabled(context)
        val autoKillInactive = SettingsRepository.isAutoKillInactiveEnabled(context)
        
        if (!autoKillBg && !autoKillInactive) return

        scope.launch {
            val foregroundPkg = AppRepository.getForegroundPackage(context)
            val extraWhitelist = SettingsRepository.getUserWhitelist(context)
            val allApps = AppRepository.getCleanableApps(context, false, extraWhitelist)
            val now = System.currentTimeMillis()
            
            val oneMinute = 60 * 1000L
            val fiveMinutes = 5 * 60 * 1000L
            
            val targets = allApps.filter { app ->
                if (app.packageName == context.packageName) return@filter false
                if (app.packageName == foregroundPkg) return@filter false
                if (extraWhitelist.contains(app.packageName)) return@filter false
                if (app.importance < 100) return@filter false // System essential

                val idleTime = if (app.lastUsedMillis > 0) now - app.lastUsedMillis else 0L
                
                val shouldKillBg = autoKillBg && idleTime >= oneMinute && app.importance > 100
                val shouldKillInactive = autoKillInactive && idleTime >= fiveMinutes
                
                shouldKillBg || shouldKillInactive
            }

            if (targets.isEmpty()) return@launch

            val aggressive = SettingsRepository.isAggressiveModeEnabled(context)
            val strategy = when {
                aggressive && RootCleaner.isRootAvailable() -> RootCleaner
                aggressive && ShizukuCleaner.isShizukuAvailable() && ShizukuCleaner.hasPermission() -> ShizukuCleaner
                else -> StandardCleaner
            }

            // Batch clean all targets at once
            strategy.clean(
                context = context,
                packages = targets.map { it.packageName },
                cacheOnly = false,
                onProgress = {},
                onTaskUpdate = {}
            )

            // Notifications
            if (autoKillBg && targets.isNotEmpty() && SettingsRepository.isAutoCleanNotificationsEnabled(context)) {
                if (targets.size > 3) {
                    val firstThree = targets.take(3).joinToString { it.label }
                    showAlertNotification(
                        "Background Optimization",
                        getString(R.string.cleaned_others_msg, firstThree, targets.size - 3) + " that were abusing background resources."
                    )
                } else {
                    targets.forEach { app ->
                        val idleTime = if (app.lastUsedMillis > 0) now - app.lastUsedMillis else 0L
                        if (idleTime >= oneMinute && idleTime < fiveMinutes) {
                            showAlertNotification(
                                "App Force-Stopped",
                                "${app.label} was killed for background resource abuse (1 min limit)."
                            )
                        }
                    }
                }
            }
        }
    }

    private fun showAlertNotification(title: String, text: String) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(this, CleanerApp.ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .build()
        manager.notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun detectAndBoostGame() {
        if (!SettingsRepository.isAutoCleanEnabled(applicationContext)) return
        
        scope.launch {
            val context = applicationContext
            val foregroundPkg = AppRepository.getForegroundPackage(context) ?: return@launch
            
            // Check if this package is a game
            val games = AppRepository.getInstalledGames(context)
            val currentGame = games.find { it.packageName == foregroundPkg }
            
            // Game Exit/Switch Detection (debounced; ignores our UI/launcher/keyboard/SystemUI)
            val target = gamingTargetPackage
            if (target != null && foregroundPkg != target) {
                if (isTransientForeground(foregroundPkg)) return@launch
                if (currentGame != null || ++nonGameTicks >= 3) {
                    nonGameTicks = 0
                    gamingTargetPackage = null
                    restoreBackgroundApps()
                }
            } else {
                nonGameTicks = 0
            }

            if (currentGame != null) {
                // Start a new session if none is active
                if (gamingTargetPackage == null) {
                    startGamingSession(foregroundPkg)
                }
                
                // Auto boost game priority
                when {
                    RootCleaner.isRootAvailable() -> RootCleaner.setStandbyBucket(foregroundPkg, 10)
                    ShizukuCleaner.hasPermission() -> ShizukuCleaner.setStandbyBucket(foregroundPkg, 10)
                }
            }
        }
    }

    private suspend fun restoreBackgroundApps(silent: Boolean = false) {
        sessionLock.withLock {
            if (tempDisabledPackages.isEmpty()) return@withLock

            val context = applicationContext
            val userFrozen = SettingsRepository.getUserFrozenSet(context)
            tempDisabledPackages.toList().forEach { pkg ->
                // Never undo something the user disabled/froze on purpose.
                if (!userFrozen.contains(pkg)) {
                    when {
                        RootCleaner.isRootAvailable() -> {
                            RootCleaner.enable(pkg)
                            RootCleaner.suspend(pkg, false)
                        }
                        ShizukuCleaner.hasPermission() -> {
                            ShizukuCleaner.enable(pkg)
                            ShizukuCleaner.suspend(pkg, false)
                        }
                    }
                }
                tempDisabledPackages.remove(pkg)
            }
            persistTempDisabled()
            if (!silent) showAutoCleanNotification("Gaming Session Ended", "Optimized background apps for system use")
        }
    }

    private fun checkAutoClean() {
        if (!SettingsRepository.isAutoCleanEnabled(applicationContext)) return
        
        // Don't auto-clean during gaming as it might cause lag
        if (gamingTargetPackage != null) return
        
        val now = System.currentTimeMillis()
        if (now - lastAutoCleanTime < AUTO_CLEAN_COOLDOWN_MS) return

        val snapshot = MemoryUtils.snapshot(applicationContext)
        val threshold = SettingsRepository.getAutoCleanThreshold(applicationContext) / 100f

        if (snapshot.usedFraction >= threshold || snapshot.lowMemory) {
            lastAutoCleanTime = now
            performCleanup()
        }
    }

    private fun performCleanup() {
        scope.launch {
            val context = applicationContext
            val aggressive = SettingsRepository.isAggressiveModeEnabled(context)
            
            val boosterInstalled = try { context.packageManager.getPackageInfo("com.turbomax.pro", 0); true } catch (_: Exception) { false }
            val isMiui = try { context.packageManager.getPackageInfo("com.miui.securitycenter", 0); true } catch (_: Exception) { false }
            val isTurbo = SettingsRepository.isTurboModeEnabled(context)

            val strategy: CleanStrategy = when {
                aggressive && RootCleaner.isRootAvailable() -> RootCleaner
                aggressive && ShizukuCleaner.isShizukuAvailable() && ShizukuCleaner.hasPermission() -> ShizukuCleaner
                else -> StandardCleaner
            }

            val extraWhitelist = SettingsRepository.getUserWhitelist(context)
            val allApps = AppRepository.getCleanableApps(context, includeSystem = false, extraWhitelist = extraWhitelist, requireUsage = false)
            
            val now = System.currentTimeMillis()
            val fiveMinutesAgo = now - (1000L * 60 * 5)
            
            // Turbo Mode: Target everything that isn't foreground
            // Normal Mode: Target inactive (5m) or invisible
            val targets = allApps.filter { app ->
                if (extraWhitelist.contains(app.packageName)) return@filter false
                
                val isInactive = app.lastUsedMillis < fiveMinutesAgo && app.lastUsedMillis > 0
                val isNoActivity = app.importance > 100 // IMPORTANCE_FOREGROUND
                val isRunning = app.importance < 1000
                
                if (isTurbo) {
                    isRunning && isNoActivity // Kill anything not in foreground immediately
                } else {
                    (isInactive && isRunning) || (isRunning && isNoActivity)
                }
            }.map { it.packageName }
            
            if (targets.isEmpty()) return@launch

            // Auto prevent apps from running in background (AppOps)
            if (aggressive || isTurbo) {
                targets.forEach { pkg ->
                    when {
                        RootCleaner.isRootAvailable() -> {
                            RootCleaner.setAppOp(pkg, 63, 1)
                            RootCleaner.setStandbyBucket(pkg, 45)
                        }
                        ShizukuCleaner.hasPermission() -> {
                            ShizukuCleaner.setAppOp(pkg, 63, 1)
                            ShizukuCleaner.setStandbyBucket(pkg, 45)
                        }
                    }
                }
            }

            val result = strategy.clean(
                context = context,
                packages = targets,
                onTaskUpdate = { task -> 
                    if (SettingsRepository.isAutoCleanNotificationsEnabled(context) && aggressive) {
                        showAutoCleanNotification("Auto Optimization Active", task)
                    }
                }
            )

            if (result.freedBytesEstimate > 0) {
                StatsRepository.recordCleanup(context, result.freedBytesEstimate)
                if (SettingsRepository.isAutoCleanNotificationsEnabled(context)) {
                    val apps = AppRepository.getAppsByPackageNames(context, result.succeededPackages)
                    val displayInfo = if (apps.size > 3) {
                        val firstThree = apps.take(3).joinToString { it.label }
                        getString(R.string.cleaned_others_msg, firstThree, apps.size - 3) + " · recovered ${MemoryUtils.formatBytes(result.freedBytesEstimate)}"
                    } else if (apps.isNotEmpty()) {
                        val all = apps.joinToString { it.label }
                        getString(R.string.cleaned_msg, all) + " · recovered ${MemoryUtils.formatBytes(result.freedBytesEstimate)}"
                    } else {
                        getString(R.string.processes_cleared, result.succeeded) + " · recovered ${MemoryUtils.formatBytes(result.freedBytesEstimate)}"
                    }

                    showAutoCleanNotification(
                        "Auto Clean",
                        displayInfo
                    )
                }
            }
            handler.post { 
                scope.launch { updateNotification() } 
            }
        }
    }

    private fun showAutoCleanNotification(title: String, text: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(this, CleanerApp.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIF_ID + 1, notification)
    }

    override fun onBind(intent: Intent?): IBinder? = null



    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // We need to call startForeground immediately
        val initialSnapshot = MemoryUtils.snapshot(applicationContext)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else 0
            startForeground(NOTIF_ID, buildNotification(initialSnapshot), type)
        } else {
            startForeground(NOTIF_ID, buildNotification(initialSnapshot))
        }

        if (intent?.action == ACTION_QUICK_CLEAN) {
            performCleanup()
        } else if (intent?.action == ACTION_START_GAME) {
            val pkg = intent.getStringExtra(EXTRA_PKG)
            if (pkg != null) startGamingSession(pkg)
        }

        // Apply all system and hardware tweaks on service start
        TweakRepository.applyAllTweaks(applicationContext)



        handler.removeCallbacks(ticker)
        handler.post(ticker)
        return START_STICKY
    }

    private fun startGamingSession(gamePkg: String) {
        gamingTargetPackage = gamePkg // set synchronously so two ticks can't start two sessions
        nonGameTicks = 0
        scope.launch { sessionLock.withLock {
            val context = applicationContext

            // Xiaomi Joyose Boost
            if (android.os.Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true)) {
                try {
                    SmartOpClient.triggerFullBoost(context, gamePkg)
                } catch (_: Exception) {}
            }

            val extraWhitelist = SettingsRepository.getUserWhitelist(context)
            val userFrozen = SettingsRepository.getUserFrozenSet(context)
            
            // Get all apps that are running and NOT protected/whitelisted/user-frozen
            val runningApps = AppRepository.getCleanableApps(context, includeSystem = false, extraWhitelist = extraWhitelist, requireUsage = false)
                .filter { it.packageName != gamePkg && it.packageName != context.packageName &&
                          it.importance < 1000 && !extraWhitelist.contains(it.packageName) &&
                          !userFrozen.contains(it.packageName) }
            
            // Parallelize hibernation for speed - increased chunk size and removed unnecessary delays
            runningApps.chunked(15).forEach { chunk ->
                val jobs = chunk.map { app ->
                    scope.async {
                        tempDisabledPackages.add(app.packageName)
                        persistTempDisabled() // before disabling, so a crash can't strand the app
                        when {
                            RootCleaner.isRootAvailable() -> {
                                RootCleaner.suspend(app.packageName, true)
                                RootCleaner.disable(app.packageName)
                            }
                            ShizukuCleaner.hasPermission() -> {
                                ShizukuCleaner.suspend(app.packageName, true)
                                ShizukuCleaner.disable(app.packageName)
                            }
                        }
                    }
                }
                jobs.awaitAll()
            }
            showAutoCleanNotification("Gaming Mode: $gamePkg", "System resources locked for peak gaming")
        } }
    }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        scope.cancel()
        gamingTargetPackage = null
        // restoreScope is NOT cancelled by scope.cancel(); the old code launched the restore in
        // `scope` right after cancelling it, so it never ran.
        restoreScope.launch { restoreBackgroundApps(silent = true) }

        super.onDestroy()
    }

    private suspend fun updateNotification() {
        val snapshot = withContext(Dispatchers.IO) { MemoryUtils.snapshot(applicationContext) }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIF_ID, buildNotification(snapshot))
    }

    private fun buildNotification(snapshot: RamSnapshot): android.app.Notification {
        val openAppIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val quickCleanIntent = PendingIntent.getService(
            this, 1, Intent(this, RamMonitorService::class.java).setAction(ACTION_QUICK_CLEAN),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CleanerApp.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("${MemoryUtils.formatBytes(snapshot.availBytes)} free (${(snapshot.usedFraction * 100).toInt()}% used)")
            .setContentText(if (snapshot.lowMemory) "System reports low memory" else "Tap Quick Clean to trim background apps")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent)
            .addAction(0, "Quick Clean", quickCleanIntent)
            .build()
    }
}
