package com.theblacksheep.appoff.core

import android.content.Context
import android.provider.Settings
import com.theblacksheep.appoff.root.RootCleaner
import com.theblacksheep.appoff.shizuku.ShizukuCleaner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object TweakRepository {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    fun applyAllTweaks(context: Context) {
        scope.launch {
            try {
                applyAnimationTweaks(context)
                applyHardwareTweaks(context)
                
                // Native App Protection: Set OOM score to avoid being killed during cleanup
                NativeMemoryUtils.setOomScoreAdjNative(-1000)
                NativeMemoryUtils.trimMallocNative()
            } catch (_: Exception) {}
        }
    }

    private suspend fun applyAnimationTweaks(context: Context) {
        val enabled = SettingsRepository.isFastAnimationsEnabled(context)
        val scale = if (enabled) 0.5f else 1.0f
        val resolver = context.contentResolver

        try {
            // Requires WRITE_SECURE_SETTINGS
            Settings.Global.putFloat(resolver, Settings.Global.WINDOW_ANIMATION_SCALE, scale)
            Settings.Global.putFloat(resolver, Settings.Global.TRANSITION_ANIMATION_SCALE, scale)
            Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, scale)
        } catch (_: Exception) {
            // If we don't have WRITE_SECURE_SETTINGS, we can try via shell if available
            val cmd = "settings put global window_animation_scale $scale; " +
                      "settings put global transition_animation_scale $scale; " +
                      "settings put global animator_duration_scale $scale"
            when {
                RootCleaner.isRootAvailable() -> RootCleaner.runShell(cmd)
                ShizukuCleaner.hasPermission() -> ShizukuCleaner.exec(cmd)
            }
        }
    }

    private suspend fun applyHardwareTweaks(context: Context) {
        applyPerformanceTweaks(
            context,
            msaa = SettingsRepository.isGameModeMsaaEnabled(context),
            disableOverlays = SettingsRepository.isGameModeOverlaysDisabled(context),
            touchBoost = SettingsRepository.isTouchSensitivityEnabled(context),
            gpuPriority = SettingsRepository.isGpuPriorityEnabled(context),
            refreshRateBoost = SettingsRepository.isHighRefreshRateEnabled(context),
            networkBoost = SettingsRepository.isNetworkBoostEnabled(context)
        )
    }

    /**
     * Specialized hardware tweaks usually applied for gaming or high performance.
     */
    suspend fun applyPerformanceTweaks(
        context: Context,
        msaa: Boolean,
        disableOverlays: Boolean,
        touchBoost: Boolean,
        gpuPriority: Boolean,
        refreshRateBoost: Boolean,
        networkBoost: Boolean
    ) {
        val msaaVal = if (msaa) "1" else "0"
        val overlayVal = if (disableOverlays) "1" else "0"
        
        val touchCmd = if (touchBoost) "settings put system pointer_speed 7" else "settings put system pointer_speed 4"
        val gpuCmd = if (gpuPriority) "setprop debug.hwui.renderer OpenGL" else "setprop debug.hwui.renderer default"
        val refreshCmd = if (refreshRateBoost) {
            "settings put system peak_refresh_rate 120.0; settings put system min_refresh_rate 120.0"
        } else {
            "settings put system peak_refresh_rate 60.0; settings put system min_refresh_rate 60.0"
        }

        // Xiaomi Joyose Boost
        if (android.os.Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true)) {
            try {
                SmartOpClient.triggerFullBoost(context, null)
            } catch (_: Exception) {}
        }
        
        // One shell round-trip instead of 5-6 separate ones: the tweaks land as soon as it returns.
        val root = RootCleaner.isRootAvailable()
        val batch = buildList {
            add("settings put global MSAA_4x $msaaVal")
            add("service call SurfaceFlinger 1008 i32 $overlayVal")
            add(touchCmd)
            add(gpuCmd)
            add(refreshCmd)
            if (networkBoost && root) add("setprop net.dns1 1.1.1.1; setprop net.dns2 1.0.0.1")
        }.joinToString("; ")

        when {
            root -> RootCleaner.runShell(batch)
            ShizukuCleaner.hasPermission() -> ShizukuCleaner.exec(batch)
        }
    }
}
