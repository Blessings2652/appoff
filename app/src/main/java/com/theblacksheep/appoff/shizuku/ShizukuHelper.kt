package com.theblacksheep.appoff.shizuku

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.theblacksheep.appoff.core.AppRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

object ShizukuHelper {

    enum class AppState { ENABLED, DISABLED, SUSPENDED, UNKNOWN }

    const val REQUEST_CODE = 1234

    /** The Shizuku manager ships as moe.shizuku.privileged.api (older builds: rikka.shizuku). */
    private val MANAGER_PACKAGES = listOf("moe.shizuku.privileged.api", "rikka.shizuku")

    fun isInstalled(context: Context): Boolean = MANAGER_PACKAGES.any { pkg ->
        try { context.packageManager.getPackageInfo(pkg, 0); true } catch (_: Exception) { false }
    }

    /** Returns true if Shizuku app is installed on the device but the service/binder is not running. */
    fun isNotRunning(context: Context): Boolean = isInstalled(context) && !isAvailable()

    enum class ShizukuStatus {
        NOT_INSTALLED,
        NOT_RUNNING,
        PERMISSION_REQUIRED,
        RUNNING_AND_GRANTED
    }

    fun getStatus(context: Context): ShizukuStatus = when {
        !isInstalled(context) -> ShizukuStatus.NOT_INSTALLED
        !isAvailable() -> ShizukuStatus.NOT_RUNNING
        !hasPermission() -> ShizukuStatus.PERMISSION_REQUIRED
        else -> ShizukuStatus.RUNNING_AND_GRANTED
    }

    /** Opens the Shizuku manager (to start the service). Returns false when it is not installed. */
    fun openShizuku(context: Context): Boolean {
        val pm = context.packageManager
        for (pkg in MANAGER_PACKAGES) {
            val installed = try { pm.getPackageInfo(pkg, 0); true } catch (_: Exception) { false }
            if (!installed) continue

            val candidates = mutableListOf<Intent>()
            pm.getLaunchIntentForPackage(pkg)?.let { candidates += it }
            // Fallback 1: resolve the launcher activity ourselves.
            try {
                val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(pkg)
                pm.queryIntentActivities(query, 0).firstOrNull()?.activityInfo?.let { ai ->
                    candidates += Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                        .setClassName(ai.packageName, ai.name)
                }
            } catch (_: Exception) {}
            // Fallback 2: known manager activity.
            candidates += Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .setClassName(pkg, "moe.shizuku.manager.MainActivity")

            for (intent in candidates) {
                try {
                    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    return true
                } catch (_: Exception) { /* try next */ }
            }
        }
        return false
    }

    fun isAvailable(): Boolean = ShizukuCleaner.isShizukuAvailable()

    fun hasPermission(): Boolean = ShizukuCleaner.hasPermission()

    /** Directs the user to start or install Shizuku based on its current status. */
    fun directUserToStartShizuku(context: Context): Boolean {
        return when (getStatus(context)) {
            ShizukuStatus.NOT_INSTALLED -> {
                installShizuku(context)
                false
            }
            ShizukuStatus.NOT_RUNNING, ShizukuStatus.PERMISSION_REQUIRED -> {
                openShizuku(context)
            }
            ShizukuStatus.RUNNING_AND_GRANTED -> true
        }
    }

    fun requestPermission(activity: Activity, requestCode: Int) {
        try {
            Shizuku.requestPermission(requestCode)
        } catch (_: Throwable) {}
    }

    fun installShizuku(context: Context) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=moe.shizuku.privileged.api"))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/"))
            webIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(webIntent)
        }
    }

    suspend fun runCommand(command: String): String = withContext(Dispatchers.IO) {
        try {
            ShizukuCleaner.exec(command)
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    suspend fun grantEssentialPermissions(packageName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val safePermissions = listOf(
                "android.permission.WRITE_SECURE_SETTINGS",
                "android.permission.DUMP",
                "android.permission.READ_LOGS"
            )
            val cmd = buildString {
                safePermissions.forEach { append("pm grant $packageName $it 2>/dev/null; ") }
                append("dumpsys deviceidle whitelist +$packageName 2>/dev/null; ")
                append("true")
            }
            runCommand(cmd)
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Java-compatible blocking version of runCommand */
    @JvmStatic
    fun runCommandBlocking(command: String): String {
        return try {
            ShizukuCleaner.execBlocking(command)
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    fun getDisableCommand(packageName: String): String {
        return "pm disable-user --user 0 $packageName; pm uninstall -k --user 0 $packageName"
    }

    fun getEnableCommand(packageName: String): String {
        return "pm install-existing --user 0 $packageName; pm enable --user 0 $packageName"
    }

    suspend fun getAppState(context: Context, packageName: String): AppState = withContext(Dispatchers.IO) {
        val apps = AppRepository.getCleanableApps(context, true, emptySet(), false)
        val app = apps.find { it.packageName == packageName }
        
        when {
            app == null -> AppState.UNKNOWN
            app.isDisabled -> AppState.DISABLED
            app.isSuspended -> AppState.SUSPENDED
            else -> AppState.ENABLED
        }
    }
}
