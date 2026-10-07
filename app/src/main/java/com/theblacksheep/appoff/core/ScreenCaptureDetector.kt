package com.theblacksheep.appoff.core

import android.app.Activity
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast

class ScreenCaptureDetector(private val activity: Activity) {

    private var screenCaptureCallback: Activity.ScreenCaptureCallback? = null
    private var contentObserver: ContentObserver? = null

    fun start() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                registerOfficialCallback()
            } else {
                registerContentObserver()
            }
        } catch (e: Exception) {
            Log.e("ScreenCaptureDetector", "Error starting screen capture detection", e)
        }
    }

    fun stop() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                screenCaptureCallback?.let {
                    activity.unregisterScreenCaptureCallback(it)
                }
                screenCaptureCallback = null
            } else {
                contentObserver?.let {
                    activity.contentResolver.unregisterContentObserver(it)
                }
                contentObserver = null
            }
        } catch (e: Exception) {
            Log.e("ScreenCaptureDetector", "Error stopping screen capture detection", e)
        }
    }

    private fun registerOfficialCallback() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            screenCaptureCallback = Activity.ScreenCaptureCallback {
                onScreenCaptured()
            }.also {
                activity.registerScreenCaptureCallback(activity.mainExecutor, it)
            }
        }
    }

    private fun registerContentObserver() {
        contentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                uri?.let {
                    if (it.toString().contains(MediaStore.Images.Media.EXTERNAL_CONTENT_URI.toString())) {
                        onScreenCaptured()
                    }
                }
            }
        }.also {
            activity.contentResolver.registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                true,
                it
            )
        }
    }

    private fun onScreenCaptured() {
        Log.d("ScreenCaptureDetector", "Screen capture detected!")
        if (!activity.isFinishing && !activity.isDestroyed) {
            Toast.makeText(activity, "Screen capture detected!", Toast.LENGTH_SHORT).show()
        }
    }
}
