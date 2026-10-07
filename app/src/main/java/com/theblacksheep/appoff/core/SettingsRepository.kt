package com.theblacksheep.appoff.core

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import com.theblacksheep.appoff.shizuku.ShizukuHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object SettingsRepository {
    private const val PREFS_NAME = "cleaner_settings"
    private const val KEY_AUTO_CLEAN = "auto_clean_enabled"
    private const val KEY_AGGRESSIVE_MODE = "aggressive_mode_enabled"
    private const val KEY_AUTO_CLEAN_THRESHOLD = "auto_clean_threshold" // in percentage
    private const val KEY_USER_WHITELIST = "user_whitelist"
    private const val KEY_FAST_ANIMATIONS = "fast_animations_enabled"
    private const val KEY_HARDWARE_ACCELERATION = "hardware_acceleration_enabled"
    private const val KEY_AUTO_CLEAN_NOTIFS = "auto_clean_notifications_enabled"
    private const val KEY_CLEAN_ON_BOOT = "clean_on_boot_enabled"
    private const val KEY_CLEAN_MODE = "clean_mode"
    private const val KEY_APP_THEME = "app_theme"
    private const val KEY_OVERHEAT_PROT = "overheat_protection_enabled"
    private const val KEY_TURBO_MODE = "turbo_mode_enabled"
    private const val KEY_AUTO_KILL_BACKGROUND = "auto_kill_background_enabled"
    private const val KEY_AUTO_KILL_INACTIVE = "auto_kill_inactive_enabled"
    private const val KEY_USER_GAMES = "user_games"
    private const val KEY_HIDDEN_GAMES = "hidden_games"
    private const val KEY_LAST_VERSION_CODE = "last_version_code"
    private const val KEY_DISABLED_APPS = "disabled_apps"
    private const val KEY_USER_FROZEN_APPS = "user_frozen_apps"
    private const val KEY_TEMP_DISABLED_APPS = "temp_disabled_apps"
    private const val KEY_SUSPENDED_APPS = "suspended_apps"
    private const val KEY_GAME_MODE_PERF = "game_mode_perf_enabled"
    private const val KEY_GAME_MODE_MSAA = "game_mode_msaa_enabled"
    private const val KEY_GAME_MODE_OVERLAYS = "game_mode_overlays_disabled"
    private const val KEY_TOUCH_SENSITIVITY = "touch_sensitivity_enabled"
    private const val KEY_GPU_PRIORITY = "gpu_priority_enabled"
    private const val KEY_NETWORK_BOOST = "network_boost_enabled"
    private const val KEY_HIGH_REFRESH_RATE = "high_refresh_rate_enabled"
    private const val KEY_FONT_SCALE = "font_scale"
    private const val KEY_ICON_STYLE = "icon_style"
    private const val KEY_INTRO_COMPLETED = "intro_completed"
    private const val KEY_HIBERNATE_SCREEN_OFF = "hibernate_screen_off_enabled"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isAutoCleanEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_AUTO_CLEAN, false)
    }

    fun setAutoCleanEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_AUTO_CLEAN, enabled).apply()
    }

    fun isAggressiveModeEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_AGGRESSIVE_MODE, false)
    }

    fun setAggressiveModeEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_AGGRESSIVE_MODE, enabled).apply()
    }

    fun getAutoCleanThreshold(context: Context): Int {
        return getPrefs(context).getInt(KEY_AUTO_CLEAN_THRESHOLD, 80) // default 80%
    }

    fun setAutoCleanThreshold(context: Context, threshold: Int) {
        getPrefs(context).edit().putInt(KEY_AUTO_CLEAN_THRESHOLD, threshold).apply()
    }

    fun isFastAnimationsEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_FAST_ANIMATIONS, false)
    }

    fun setFastAnimationsEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_FAST_ANIMATIONS, enabled).apply()
    }

    fun isHardwareAccelerationEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_HARDWARE_ACCELERATION, false)
    }

    fun setHardwareAccelerationEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_HARDWARE_ACCELERATION, enabled).apply()
    }

    fun isHibernateScreenOffEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_HIBERNATE_SCREEN_OFF, false)
    }

    fun setHibernateScreenOffEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_HIBERNATE_SCREEN_OFF, enabled).apply()
    }

    fun isAutoCleanNotificationsEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_AUTO_CLEAN_NOTIFS, true)
    }

    fun setAutoCleanNotificationsEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_AUTO_CLEAN_NOTIFS, enabled).apply()
    }

    fun isCleanOnBootEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_CLEAN_ON_BOOT, false)
    }

    fun setCleanOnBootEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_CLEAN_ON_BOOT, enabled).apply()
    }

    fun getUserWhitelist(context: Context): Set<String> {
        return getPrefs(context).getStringSet(KEY_USER_WHITELIST, emptySet()) ?: emptySet()
    }

    fun toggleWhitelistApp(context: Context, packageName: String) {
        val current = getUserWhitelist(context).toMutableSet()
        if (current.contains(packageName)) {
            current.remove(packageName)
        } else {
            current.add(packageName)
        }
        getPrefs(context).edit().putStringSet(KEY_USER_WHITELIST, current).commit()
    }

    fun addAppsToWhitelist(context: Context, packageNames: Collection<String>): Int {
        val current = getUserWhitelist(context).toMutableSet()
        val initialSize = current.size
        current.addAll(packageNames)
        val addedCount = current.size - initialSize
        getPrefs(context).edit().putStringSet(KEY_USER_WHITELIST, current).commit()
        return addedCount
    }

    fun getCleanMode(context: Context): String? {
        return getPrefs(context).getString(KEY_CLEAN_MODE, null)
    }

    fun setCleanMode(context: Context, mode: String) {
        getPrefs(context).edit().putString(KEY_CLEAN_MODE, mode).apply()
    }

    fun getAppTheme(context: Context): String {
        return getPrefs(context).getString(KEY_APP_THEME, "DARK") ?: "DARK"
    }

    fun setAppTheme(context: Context, theme: String) {
        getPrefs(context).edit().putString(KEY_APP_THEME, theme).apply()
    }

    fun isOverheatProtectionEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_OVERHEAT_PROT, true)
    }

    fun setOverheatProtectionEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_OVERHEAT_PROT, enabled).apply()
    }

    fun isTurboModeEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_TURBO_MODE, false)
    }

    fun setTurboModeEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_TURBO_MODE, enabled).apply()
    }

    fun isAutoKillBackgroundEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_AUTO_KILL_BACKGROUND, false)
    }

    fun setAutoKillBackgroundEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_AUTO_KILL_BACKGROUND, enabled).apply()
    }

    fun isAutoKillInactiveEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_AUTO_KILL_INACTIVE, false)
    }

    fun setAutoKillInactiveEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_AUTO_KILL_INACTIVE, enabled).apply()
    }

    fun getUserGames(context: Context): Set<String> {
        return getPrefs(context).getStringSet(KEY_USER_GAMES, emptySet()) ?: emptySet()
    }

    fun addUserGame(context: Context, packageName: String) {
        val current = getUserGames(context).toMutableSet()
        current.add(packageName)
        removeHiddenGame(context, packageName)
        getPrefs(context).edit().putStringSet(KEY_USER_GAMES, current).apply()
    }

    fun removeUserGame(context: Context, packageName: String) {
        val current = getUserGames(context).toMutableSet()
        current.remove(packageName)
        getPrefs(context).edit().putStringSet(KEY_USER_GAMES, current).apply()
    }

    fun getHiddenGames(context: Context): Set<String> {
        return getPrefs(context).getStringSet(KEY_HIDDEN_GAMES, emptySet()) ?: emptySet()
    }

    fun addHiddenGame(context: Context, packageName: String) {
        val current = getHiddenGames(context).toMutableSet()
        current.add(packageName)
        removeUserGame(context, packageName)
        getPrefs(context).edit().putStringSet(KEY_HIDDEN_GAMES, current).apply()
    }

    fun removeHiddenGame(context: Context, packageName: String) {
        val current = getHiddenGames(context).toMutableSet()
        current.remove(packageName)
        getPrefs(context).edit().putStringSet(KEY_HIDDEN_GAMES, current).apply()
    }

    fun getLastVersionCode(context: Context): Int {
        return getPrefs(context).getInt(KEY_LAST_VERSION_CODE, 0)
    }

    fun setLastVersionCode(context: Context, versionCode: Int) {
        getPrefs(context).edit().putInt(KEY_LAST_VERSION_CODE, versionCode).apply()
    }

    // ========== DISABLED APPS ==========

    fun markAppDisabled(context: Context, packageName: String, disabled: Boolean) {
        val disabledSet = getDisabledAppsSet(context).toMutableSet()
        if (disabled) {
            disabledSet.add(packageName)
        } else {
            disabledSet.remove(packageName)
        }
        getPrefs(context).edit().putStringSet(KEY_DISABLED_APPS, disabledSet).apply()
    }

    fun getDisabledAppsSet(context: Context): Set<String> {
        return getPrefs(context).getStringSet(KEY_DISABLED_APPS, emptySet()) ?: emptySet()
    }

    fun isAppDisabled(context: Context, packageName: String): Boolean {
        return getDisabledAppsSet(context).contains(packageName)
    }

    // Apps the USER disabled/froze on purpose. Unlike KEY_DISABLED_APPS this is never overwritten
    // by refreshDisabledApps() (which mirrors `pm list packages -d`), so background services can
    // trust it to mean "do not re-enable this".
    fun markUserFrozen(context: Context, packageName: String, frozen: Boolean) {
        val set = getUserFrozenSet(context).toMutableSet()
        if (frozen) set.add(packageName) else set.remove(packageName)
        getPrefs(context).edit().putStringSet(KEY_USER_FROZEN_APPS, set).commit()
    }

    fun getUserFrozenSet(context: Context): Set<String> {
        return getPrefs(context).getStringSet(KEY_USER_FROZEN_APPS, emptySet()) ?: emptySet()
    }

    fun isUserFrozen(context: Context, packageName: String): Boolean {
        return getUserFrozenSet(context).contains(packageName)
    }

    // Packages the Game Mode service disabled temporarily; persisted so they can still be
    // restored if the service/process dies mid-session.
    fun getTempDisabledApps(context: Context): Set<String> {
        return getPrefs(context).getStringSet(KEY_TEMP_DISABLED_APPS, emptySet()) ?: emptySet()
    }

    fun setTempDisabledApps(context: Context, packages: Set<String>) {
        getPrefs(context).edit().putStringSet(KEY_TEMP_DISABLED_APPS, packages.toSet()).apply()
    }

    suspend fun refreshDisabledApps(context: Context): Set<String> = withContext(Dispatchers.IO) {
        val disabledSet = mutableSetOf<String>()
        try {
            if (ShizukuHelper.hasPermission()) {
                val result = ShizukuHelper.runCommand("pm list packages -d")
                if (!result.startsWith("ERROR")) {
                    result.split("\n").forEach { line ->
                        if (line.startsWith("package:")) {
                            val pkg = line.substringAfter("package:").trim()
                            if (pkg.isNotEmpty()) {
                                disabledSet.add(pkg)
                            }
                        }
                    }
                    getPrefs(context).edit().putStringSet(KEY_DISABLED_APPS, disabledSet).apply()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        disabledSet
    }

    // ========== SUSPEND APPS ==========

    fun markAppSuspended(context: Context, packageName: String, suspended: Boolean) {
        val suspendedSet = getSuspendedAppsSet(context).toMutableSet()
        if (suspended) {
            suspendedSet.add(packageName)
        } else {
            suspendedSet.remove(packageName)
        }
        getPrefs(context).edit().putStringSet(KEY_SUSPENDED_APPS, suspendedSet).apply()
    }

    fun getSuspendedAppsSet(context: Context): Set<String> {
        return getPrefs(context).getStringSet(KEY_SUSPENDED_APPS, emptySet()) ?: emptySet()
    }

    fun isAppSuspended(context: Context, packageName: String): Boolean {
        return getSuspendedAppsSet(context).contains(packageName)
    }

    suspend fun refreshSuspendedApps(context: Context): Set<String> = withContext(Dispatchers.IO) {
        val suspendedSet = mutableSetOf<String>()
        try {
            if (ShizukuHelper.hasPermission() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val result = ShizukuHelper.runCommand("pm list packages -s")
                if (!result.startsWith("ERROR")) {
                    result.split("\n").forEach { line ->
                        if (line.startsWith("package:")) {
                            val pkg = line.substringAfter("package:").trim()
                            if (pkg.isNotEmpty()) {
                                suspendedSet.add(pkg)
                            }
                        }
                    }
                    getPrefs(context).edit().putStringSet(KEY_SUSPENDED_APPS, suspendedSet).apply()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        suspendedSet
    }

    // ========== GAME TWEAKS ==========

    fun isGameModePerfEnabled(context: Context): Boolean = getPrefs(context).getBoolean(KEY_GAME_MODE_PERF, false)
    fun setGameModePerfEnabled(context: Context, enabled: Boolean) = getPrefs(context).edit().putBoolean(KEY_GAME_MODE_PERF, enabled).apply()

    fun isGameModeMsaaEnabled(context: Context): Boolean = getPrefs(context).getBoolean(KEY_GAME_MODE_MSAA, false)
    fun setGameModeMsaaEnabled(context: Context, enabled: Boolean) = getPrefs(context).edit().putBoolean(KEY_GAME_MODE_MSAA, enabled).apply()

    fun isGameModeOverlaysDisabled(context: Context): Boolean = getPrefs(context).getBoolean(KEY_GAME_MODE_OVERLAYS, false)
    fun setGameModeOverlaysDisabled(context: Context, enabled: Boolean) = getPrefs(context).edit().putBoolean(KEY_GAME_MODE_OVERLAYS, enabled).apply()

    fun isTouchSensitivityEnabled(context: Context): Boolean = getPrefs(context).getBoolean(KEY_TOUCH_SENSITIVITY, false)
    fun setTouchSensitivityEnabled(context: Context, enabled: Boolean) = getPrefs(context).edit().putBoolean(KEY_TOUCH_SENSITIVITY, enabled).apply()

    fun isGpuPriorityEnabled(context: Context): Boolean = getPrefs(context).getBoolean(KEY_GPU_PRIORITY, false)
    fun setGpuPriorityEnabled(context: Context, enabled: Boolean) = getPrefs(context).edit().putBoolean(KEY_GPU_PRIORITY, enabled).apply()

    fun isNetworkBoostEnabled(context: Context): Boolean = getPrefs(context).getBoolean(KEY_NETWORK_BOOST, false)
    fun setNetworkBoostEnabled(context: Context, enabled: Boolean) = getPrefs(context).edit().putBoolean(KEY_NETWORK_BOOST, enabled).apply()

    fun isHighRefreshRateEnabled(context: Context): Boolean = getPrefs(context).getBoolean(KEY_HIGH_REFRESH_RATE, false)
    fun setHighRefreshRateEnabled(context: Context, enabled: Boolean) = getPrefs(context).edit().putBoolean(KEY_HIGH_REFRESH_RATE, enabled).apply()

    fun getFontScale(context: Context): Float = getPrefs(context).getFloat(KEY_FONT_SCALE, 0.8f)
    fun setFontScale(context: Context, scale: Float) = getPrefs(context).edit().putFloat(KEY_FONT_SCALE, scale).apply()

    fun getIconStyle(context: Context): String = getPrefs(context).getString(KEY_ICON_STYLE, "ROUNDED") ?: "ROUNDED"
    fun setIconStyle(context: Context, style: String) = getPrefs(context).edit().putString(KEY_ICON_STYLE, style).apply()

    fun isIntroCompleted(context: Context): Boolean = getPrefs(context).getBoolean(KEY_INTRO_COMPLETED, false)
    fun setIntroCompleted(context: Context, completed: Boolean) = getPrefs(context).edit().putBoolean(KEY_INTRO_COMPLETED, completed).apply()
}
