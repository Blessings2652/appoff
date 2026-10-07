package com.theblacksheep.appoff.service

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import com.theblacksheep.appoff.R
import com.theblacksheep.appoff.root.RootCleaner
import com.theblacksheep.appoff.shizuku.ShizukuCleaner
import com.theblacksheep.appoff.shizuku.ShizukuHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.milliseconds

class AppActionHandler(private val activity: Activity) {

    private val context: Context = activity.applicationContext

    data class ActionResult(val success: Boolean, val message: String?)

    // ========== UNINSTALL APP ==========
    suspend fun uninstallApp(packageName: String): ActionResult = withContext(Dispatchers.Main) {
        if (isCriticalSystemApp(packageName)) {
            val confirmed = showCriticalWarningDialog(packageName, "uninstall")
            if (!confirmed) return@withContext ActionResult(false, "Canceled by user")
        }

        withContext(Dispatchers.IO) {
            try {
                val ok = when {
                    RootCleaner.isRootAvailable() -> RootCleaner.uninstall(packageName)
                    ShizukuCleaner.hasPermission() -> ShizukuCleaner.uninstall(packageName)
                    else -> {
                        try {
                            val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                            true
                        } catch (_: Exception) {
                            false
                        }
                    }
                }
                if (ok) ActionResult(true, "Uninstall initiated")
                else ActionResult(false, "Uninstall failed")
            } catch (e: Exception) {
                ActionResult(false, e.message ?: "Unknown error")
            }
        }
    }

    // ========== REINSTALL APP ==========
    suspend fun reinstallApp(packageName: String): ActionResult = withContext(Dispatchers.Main) {
        withContext(Dispatchers.IO) {
            try {
                if (!hasPrivilegedAccess()) {
                    withContext(Dispatchers.Main) { showShizukuRequiredDialog() }
                    return@withContext ActionResult(false, "Root or Shizuku required")
                }

                val cmd = listOf(
                    "cmd package install-existing --user 0 $packageName",
                    "pm install-existing --user 0 $packageName",
                    "cmd package enable --user 0 $packageName",
                    "cmd package unhide --user 0 $packageName"
                ).joinToString("; ")

                val ok = when {
                    RootCleaner.isRootAvailable() -> RootCleaner.runShell(cmd)
                    ShizukuCleaner.hasPermission() -> {
                        val res = ShizukuCleaner.exec(cmd)
                        !res.startsWith("Error:")
                    }
                    else -> false
                }

                if (ok || awaitState { !isAppDisabled(packageName) }) {
                    ActionResult(true, "App reinstalled successfully")
                } else {
                    ActionResult(false, "Failed to reinstall app")
                }
            } catch (e: Exception) {
                ActionResult(false, e.message ?: "Unknown error")
            }
        }
    }

    private fun isSystemApp(packageName: String): Boolean {
        return try {
            val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
            (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 || (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        } catch (_: Exception) {
            false
        }
    }

    // ========== DISABLE APP ==========
    suspend fun disableApp(packageName: String): ActionResult = withContext(Dispatchers.Main) {
        val isSystem = isSystemApp(packageName)
        if (isSystem && !hasPrivilegedAccess()) {
            showShizukuRequiredDialog()
            return@withContext ActionResult(false, "Root or Shizuku required")
        }

        if (isCriticalSystemApp(packageName)) {
            val confirmed = showCriticalWarningDialog(packageName, "disable")
            if (!confirmed) return@withContext ActionResult(false, "Canceled by user")
        }

        executeDisable(packageName, isSystem)
    }

    private suspend fun executeDisable(packageName: String, isSystem: Boolean): ActionResult = withContext(Dispatchers.IO) {
        try {
            val ok = when {
                RootCleaner.isRootAvailable() -> {
                    if (isSystem) RootCleaner.disable(packageName)
                    else RootCleaner.runShell("am force-stop --user 0 $packageName; cmd package disable-user --user 0 $packageName; pm disable-user --user 0 $packageName")
                }
                ShizukuCleaner.hasPermission() -> {
                    if (isSystem) ShizukuCleaner.disable(packageName)
                    else {
                        val res = ShizukuCleaner.exec("am force-stop --user 0 $packageName; cmd package disable-user --user 0 $packageName; pm disable-user --user 0 $packageName")
                        !res.startsWith("Error:")
                    }
                }
                else -> {
                    if (!isSystem) {
                        try {
                            context.packageManager.setApplicationEnabledSetting(
                                packageName,
                                PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
                                0
                            )
                            true
                        } catch (_: Exception) {
                            false
                        }
                    } else false
                }
            }
            if (ok || awaitState { isAppDisabled(packageName) }) {
                return@withContext ActionResult(true, "App disabled successfully")
            }
            ActionResult(false, "System blocked disable request")
        } catch (e: Exception) {
            ActionResult(false, e.message ?: "Unknown error")
        }
    }

    // ========== ENABLE APP ==========
    suspend fun enableApp(packageName: String): ActionResult = withContext(Dispatchers.IO) {
        val isSystem = isSystemApp(packageName)
        if (isSystem && !hasPrivilegedAccess()) {
            withContext(Dispatchers.Main) { showShizukuRequiredDialog() }
            return@withContext ActionResult(false, "Root or Shizuku required")
        }

        try {
            val ok = when {
                RootCleaner.isRootAvailable() -> {
                    if (isSystem) RootCleaner.enable(packageName)
                    else RootCleaner.runShell("cmd package enable --user 0 $packageName; pm enable --user 0 $packageName")
                }
                ShizukuCleaner.hasPermission() -> {
                    if (isSystem) ShizukuCleaner.enable(packageName)
                    else {
                        val res = ShizukuCleaner.exec("cmd package enable --user 0 $packageName; pm enable --user 0 $packageName")
                        !res.startsWith("Error:")
                    }
                }
                else -> {
                    if (!isSystem) {
                        try {
                            context.packageManager.setApplicationEnabledSetting(
                                packageName,
                                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                                0
                            )
                            true
                        } catch (_: Exception) {
                            false
                        }
                    } else false
                }
            }
            if (ok || awaitState { !isAppDisabled(packageName) }) {
                return@withContext ActionResult(true, "App enabled successfully")
            }
            ActionResult(false, "App is still disabled")
        } catch (e: Exception) {
            ActionResult(false, e.message ?: "Unknown error")
        }
    }

    // ========== SUSPEND APP ==========
    @RequiresApi(Build.VERSION_CODES.P)
    suspend fun suspendApp(packageName: String): ActionResult = withContext(Dispatchers.Main) {
        if (!hasPrivilegedAccess()) {
            showShizukuRequiredDialog()
            return@withContext ActionResult(false, "Root or Shizuku required")
        }

        if (isCriticalSystemApp(packageName)) {
            val confirmed = showCriticalWarningDialog(packageName, "suspend")
            if (!confirmed) return@withContext ActionResult(false, "Canceled by user")
        }

        withContext(Dispatchers.IO) {
            try {
                val ok = when {
                    RootCleaner.isRootAvailable() -> RootCleaner.suspend(packageName, true)
                    ShizukuCleaner.hasPermission() -> ShizukuCleaner.suspend(packageName, true)
                    else -> false
                }
                if (ok || awaitState { isAppSuspended(packageName) }) return@withContext ActionResult(true, "App suspended")
                if (trySuspendAlternative(packageName, true)) ActionResult(true, "App suspended")
                else ActionResult(false, "App is not suspended")
            } catch (e: Exception) {
                ActionResult(false, e.message ?: "Unknown error")
            }
        }
    }

    // ========== UNSUSPEND APP ==========
    @RequiresApi(Build.VERSION_CODES.P)
    suspend fun unsuspendApp(packageName: String): ActionResult = withContext(Dispatchers.IO) {
        if (!hasPrivilegedAccess()) {
            withContext(Dispatchers.Main) { showShizukuRequiredDialog() }
            return@withContext ActionResult(false, "Root or Shizuku required")
        }

        try {
            val ok = when {
                RootCleaner.isRootAvailable() -> RootCleaner.suspend(packageName, false)
                ShizukuCleaner.hasPermission() -> ShizukuCleaner.suspend(packageName, false)
                else -> false
            }
            if (ok || awaitState { !isAppSuspended(packageName) }) return@withContext ActionResult(true, "App unsuspended")
            if (trySuspendAlternative(packageName, false)) ActionResult(true, "App unsuspended")
            else ActionResult(false, "App is still suspended")
        } catch (e: Exception) {
            ActionResult(false, e.message ?: "Unknown error")
        }
    }

    /**
     * Polls [check] every 10 ms for up to [timeoutMs]. Returns as soon as it is true, so the
     * normal case costs zero or minimal delay instead of a fixed sleep plus a fallback chain.
     */
    private suspend fun awaitState(timeoutMs: Long = 500, check: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (true) {
            if (check()) return true
            if (System.currentTimeMillis() >= deadline) return false
            delay(10.milliseconds)
        }
    }

    // Helper methods to verify state
    private fun isAppDisabled(packageName: String): Boolean {
        return try {
            val pm = context.packageManager
            // 1. Check official enabled setting
            val state = pm.getApplicationEnabledSetting(packageName)
            val isOfficiallyDisabled = state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED ||
                    state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER ||
                    state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED

            if (isOfficiallyDisabled) return true

            // 2. Check if it's "Hidden" (pm hide / pm uninstall -k)
            val info = pm.getApplicationInfo(packageName, PackageManager.MATCH_DISABLED_COMPONENTS or PackageManager.MATCH_UNINSTALLED_PACKAGES or 0x02000000)
            val isInstalled = (info.flags and ApplicationInfo.FLAG_INSTALLED) != 0
            
            !isInstalled
        } catch (_: Exception) {
            true
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun isAppSuspended(packageName: String): Boolean {
        val pm = context.packageManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                return pm.isPackageSuspended(packageName)
            } catch (_: Exception) {}
        }

        try {
            val method = pm.javaClass.getMethod("isPackageSuspended", String::class.java)
            return method.invoke(pm, packageName) as Boolean
        } catch (_: Exception) {}

        return try {
            val info = pm.getApplicationInfo(packageName, PackageManager.MATCH_DISABLED_COMPONENTS)
            (info.flags and ApplicationInfo.FLAG_SUSPENDED) != 0
        } catch (_: Exception) {
            false
        }
    }

    // Alternative methods for fallback
    private suspend fun tryDisableAlternative(packageName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            // Aggressive sequence as ONE shell call (was 7 separate su/shizuku round-trips)
            val cmd = listOf(
                "am force-stop --user 0 $packageName",
                "cmd package disable-user --user 0 $packageName",
                "cmd package disable --user 0 $packageName",
                "cmd package hide --user 0 $packageName",
                "pm uninstall -k --user 0 $packageName"
            ).joinToString("; ")
            runPrivileged(cmd)
            awaitState { isAppDisabled(packageName) }
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun tryEnableAlternative(packageName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val cmd = listOf(
                "pm install-existing --user 0 $packageName",
                "cmd package enable --user 0 $packageName",
                "cmd package unhide --user 0 $packageName"
            ).joinToString("; ")
            runPrivileged(cmd)
            awaitState { !isAppDisabled(packageName) }
        } catch (_: Exception) {
            false
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private suspend fun trySuspendAlternative(packageName: String, suspend: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val action = if (suspend) "suspend" else "unsuspend"
            runPrivileged("pm $action --user 0 $packageName; cmd package $action $packageName")
            awaitState { if (suspend) isAppSuspended(packageName) else !isAppSuspended(packageName) }
        } catch (_: Exception) {
            false
        }
    }

    /** Runs one shell command line through whichever privileged engine is available. */
    private suspend fun runPrivileged(cmd: String) {
        when {
            RootCleaner.isRootAvailable() -> RootCleaner.runShell(cmd)
            ShizukuCleaner.hasPermission() -> ShizukuCleaner.exec(cmd)
        }
    }

    private suspend fun hasPrivilegedAccess(): Boolean = withContext(Dispatchers.IO) {
        RootCleaner.isRootAvailable() || 
               (ShizukuCleaner.isShizukuAvailable() && ShizukuCleaner.hasPermission())
    }

    fun isCriticalSystemApp(packageName: String): Boolean {
        if (packageName == context.packageName) return true

        val criticalExact = setOf(
            "android",
            "com.android.systemui",
            "com.android.settings",
            "com.android.phone",
            "com.android.dialer",
            "com.google.android.dialer",
            "com.android.providers.telephony",
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
            "com.android.inputmethod.latin",
            "com.google.android.inputmethod.latin",
            "rikka.shizuku",
            "moe.shizuku.privileged.api"
        )
        val criticalPrefixes = listOf(
            "com.android.launcher",
            "com.miui.home",
            "com.sec.android.app.launcher",
            "com.huawei.android.launcher",
            "com.oppo.launcher",
            "com.oneplus.launcher",
            "com.google.android.apps.nexuslauncher",
            "com.samsung.android.honeyboard"
        )

        if (criticalExact.contains(packageName)) return true
        if (criticalPrefixes.any { packageName.startsWith(it) }) return true

        try {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val resolveInfo = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            if (resolveInfo?.activityInfo?.packageName == packageName) {
                return true
            }
        } catch (_: Exception) {}

        return false
    }

    fun showShizukuRequiredDialog() {
        when {
            ShizukuHelper.isNotRunning(context) -> showShizukuNotRunningDialog()
            !ShizukuHelper.isInstalled(context) -> showShizukuNotInstalledDialog()
            ShizukuHelper.isAvailable() && !ShizukuHelper.hasPermission() -> showShizukuPermissionRequestDialog()
            else -> showShizukuNotRunningDialog()
        }
    }

    fun showShizukuNotRunningDialog() {
        AlertDialog.Builder(activity)
            .setTitle(context.getString(R.string.shizuku_not_running_dialog_title))
            .setMessage(context.getString(R.string.shizuku_not_running_dialog_msg))
            .setPositiveButton(context.getString(R.string.shizuku_open_button)) { _, _ ->
                if (!ShizukuHelper.openShizuku(activity)) {
                    ShizukuHelper.installShizuku(activity)
                }
            }
            .setNegativeButton(context.getString(android.R.string.cancel), null)
            .show()
    }

    fun showShizukuNotInstalledDialog() {
        AlertDialog.Builder(activity)
            .setTitle(context.getString(R.string.access_install_title))
            .setMessage(context.getString(R.string.access_install_desc))
            .setPositiveButton(context.getString(R.string.access_install_button)) { _, _ ->
                ShizukuHelper.installShizuku(activity)
            }
            .setNegativeButton(context.getString(android.R.string.cancel), null)
            .show()
    }

    fun showShizukuPermissionRequestDialog() {
        if (ShizukuHelper.isAvailable()) {
            ShizukuHelper.requestPermission(activity, ShizukuHelper.REQUEST_CODE)
        } else {
            showShizukuNotRunningDialog()
        }
    }

    suspend fun showCriticalWarningDialog(packageName: String, actionName: String = "modify"): Boolean = suspendCancellableCoroutine { cont ->
        val appLabel = try {
            val info = context.packageManager.getApplicationInfo(packageName, 0)
            context.packageManager.getApplicationLabel(info).toString()
        } catch (_: Exception) {
            packageName
        }
        AlertDialog.Builder(activity)
            .setTitle("⚠️ CRITICAL SYSTEM APP")
            .setMessage("Performing '$actionName' on '$appLabel' ($packageName) may soft-brick or crash your device.\n\nAre you sure you want to proceed?")
            .setPositiveButton("🚨 YES, PROCEED") { _, _ ->
                cont.resume(true)
            }
            .setNegativeButton("Cancel") { _, _ ->
                cont.resume(false)
            }
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setOnCancelListener { cont.resume(false) }
            .show()
    }
}
