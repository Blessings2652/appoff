package com.theblacksheep.appoff.core

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log

object SmartOpClient {

    private const val TAG = "SmartOpClient"
    private const val JOYOSE_PACKAGE = "com.xiaomi.joyose"

    // 1. Boost Request Receiver
    private const val BOOST_RECEIVER =
        "com.xiaomi.joyose.smartop.gamebooster.receiver.BoostRequestReceiver"

    // 2. GPU Tuner Service
    private const val GPU_TUNER_SERVICE =
        "com.xiaomi.joyose.securitycenter.GPUTunerService"

    // 3. Scene Recognize Service
    private const val SCENE_RECOGNIZE_SERVICE =
        "com.xiaomi.joyose.smartop.gamebooster.scenerecognize.SceneRecognizeService"

    /**
     * MAIN METHOD: Starts all Joyose features
     */
    fun triggerFullBoost(context: Context, gamePackage: String?) {
        Log.i(TAG, "🚀 Starting to enable all Joyose features...")

        // Start the broadcast (preferred method)
        sendBoostBroadcast(context, gamePackage)

        // Start GPU Tuner
        startGPUTuner(context)

        // Start Scene Recognize
        startSceneRecognize(context)

        // If everything fails, fallback to Settings
        setPerformanceViaSettings(context)

        Log.i(TAG, "✅ All features have been enabled!")
    }

    private fun sendBoostBroadcast(context: Context, gamePackage: String?) {
        try {
            val intent = Intent().apply {
                setClassName(JOYOSE_PACKAGE, BOOST_RECEIVER)
                putExtra("action", "boost")
                gamePackage?.let { putExtra("package_name", it) }
            }
            context.sendBroadcast(intent)
            Log.i(TAG, "📡 Broadcast sent.")
        } catch (e: Exception) {
            Log.w(TAG, "Broadcast failed: ${e.message}")
        }
    }

    fun startGPUTuner(context: Context) {
        try {
            val intent = Intent().apply {
                setComponent(ComponentName(JOYOSE_PACKAGE, GPU_TUNER_SERVICE))
            }
            context.startService(intent)
            Log.i(TAG, "🎮 GPUTunerService started.")
        } catch (e: Exception) {
            Log.w(TAG, "GPUTunerService failed: ${e.message}")
        }
    }

    fun startSceneRecognize(context: Context) {
        try {
            val intent = Intent().apply {
                setComponent(ComponentName(JOYOSE_PACKAGE, SCENE_RECOGNIZE_SERVICE))
            }
            context.startService(intent)
            Log.i(TAG, "🧠 SceneRecognizeService started.")
        } catch (e: Exception) {
            Log.w(TAG, "SceneRecognizeService failed: ${e.message}")
        }
    }

    private fun setPerformanceViaSettings(context: Context) {
        try {
            Settings.Global.putInt(context.contentResolver, "game_boost_mode", 1)
            Settings.System.putInt(context.contentResolver, "performance_mode", 1)
            Log.i(TAG, "⚙️ Performance mode set in Settings.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set Settings: ${e.message}")
        }
    }
}
