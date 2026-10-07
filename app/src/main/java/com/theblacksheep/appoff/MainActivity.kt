package com.theblacksheep.appoff

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.theblacksheep.appoff.service.RamMonitorService
import com.theblacksheep.appoff.ui.ActivityCallback
import com.theblacksheep.appoff.ui.CleanerScreen
import com.theblacksheep.appoff.ui.CleanerViewModel
import com.theblacksheep.appoff.ui.theme.AppOffTheme
import android.app.Activity
import android.widget.Toast
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.CompositionLocalProvider

class MainActivity : ComponentActivity() {

    private val viewModel: CleanerViewModel by viewModels()

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { /* result not required */ }

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Request High Refresh Rate (60Hz - 120Hz/144Hz)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.attributes.preferredDisplayModeId = 0 // Let system choose best
            // On some devices, we can nudge it by setting a high frame rate
            val modes = display?.supportedModes
            val highRefreshMode = modes?.asSequence()?.filter { it.refreshRate >= 120f }?.maxByOrNull { it.refreshRate }
            highRefreshMode?.let {
                window.attributes.preferredDisplayModeId = it.modeId
            }
        }

        // Request necessary permissions
        val permissions = mutableListOf<String>()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        
        if (permissions.isNotEmpty()) {
            permissionLauncher.launch(permissions.toTypedArray())
        }

        viewModel.activityCallback = object : ActivityCallback {
            override fun getActivity(): Activity = this@MainActivity
            override fun showToast(message: String, duration: Int) {
                runOnUiThread {
                    Toast.makeText(applicationContext, message, duration).show()
                }
            }
        }

        RamMonitorService.start(this)

        setContent {
            val state by viewModel.state.collectAsState()
            val windowSizeClass = calculateWindowSizeClass(this)
            
            CompositionLocalProvider(LocalWindowSizeClass provides windowSizeClass) {
                AppOffTheme(
                    appTheme = state.theme,
                    fontScale = state.fontScale,
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        CleanerScreen()
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Covers the user coming back from Shizuku / system settings: only does real work when the
        // access level changed (or the data is stale), instead of a full refresh on every resume.
        viewModel.onAppResumed()
    }

    override fun onStart() {
        super.onStart()
    }

    override fun onDestroy() {
        viewModel.activityCallback = null
        super.onDestroy()
    }
}
