package com.theblacksheep.appoff.presentation.mvp

import com.theblacksheep.appoff.core.CleanableApp
import com.theblacksheep.appoff.core.SystemStats

/**
 * MVP (Model-View-Presenter) Contract for Cleaner Feature.
 */
interface CleanerContract {

    /**
     * View Interface in MVP.
     */
    interface View {
        fun showApps(apps: List<CleanableApp>)
        fun showLoading(isLoading: Boolean)
        fun showSystemStats(stats: SystemStats)
        fun showAppDetail(app: CleanableApp)
        fun showToast(message: String)
        fun updateProcessingApps(processingApps: Set<String>)
    }

    /**
     * Presenter Interface in MVP.
     */
    interface Presenter {
        fun attachView(view: View)
        fun detachView()
        fun loadSystemApps()
        fun onAppClicked(app: CleanableApp)
        fun onToggleAppSelection(packageName: String)
        fun onForceStopApp(packageName: String)
        fun onDisableApp(packageName: String)
        fun onEnableApp(packageName: String)
        fun onUninstallApp(packageName: String)
        fun onClearCacheApp(packageName: String)
    }
}
