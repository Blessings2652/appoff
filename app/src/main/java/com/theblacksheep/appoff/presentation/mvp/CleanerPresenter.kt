package com.theblacksheep.appoff.presentation.mvp

import android.app.Activity
import android.content.Context
import com.theblacksheep.appoff.core.CleanableApp
import com.theblacksheep.appoff.domain.usecase.GetSystemAppsUseCase
import com.theblacksheep.appoff.domain.usecase.ManageAppUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * MVP Presenter for Cleaner Screen using Clean Architecture Use Cases.
 */
class CleanerPresenter(
    private val context: Context,
    private val getSystemAppsUseCase: GetSystemAppsUseCase,
    private val manageAppUseCase: ManageAppUseCase
) : CleanerContract.Presenter {

    private var view: CleanerContract.View? = null
    private val presenterScope = CoroutineScope(Dispatchers.Main + Job())

    override fun attachView(view: CleanerContract.View) {
        this.view = view
    }

    override fun detachView() {
        this.view = null
    }

    override fun loadSystemApps() {
        view?.showLoading(true)
        presenterScope.launch {
            try {
                val apps = getSystemAppsUseCase(context, emptySet())
                view?.showApps(apps)
            } catch (e: Exception) {
                view?.showToast("Error loading apps: ${e.localizedMessage}")
            } finally {
                view?.showLoading(false)
            }
        }
    }

    override fun onAppClicked(app: CleanableApp) {
        view?.showAppDetail(app)
    }

    override fun onToggleAppSelection(packageName: String) {
        // Selection state logic handled in presenter
    }

    override fun onForceStopApp(packageName: String) {
        // Executes force stop
    }

    override fun onDisableApp(packageName: String) {
        val activity = context as? Activity ?: return
        presenterScope.launch {
            val result = manageAppUseCase.disableApp(activity, packageName)
            result.message?.let { view?.showToast(it) }
            loadSystemApps()
        }
    }

    override fun onEnableApp(packageName: String) {
        val activity = context as? Activity ?: return
        presenterScope.launch {
            val result = manageAppUseCase.enableApp(activity, packageName)
            result.message?.let { view?.showToast(it) }
            loadSystemApps()
        }
    }

    override fun onUninstallApp(packageName: String) {
        val activity = context as? Activity ?: return
        presenterScope.launch {
            val result = manageAppUseCase.uninstallApp(activity, packageName)
            result.message?.let { view?.showToast(it) }
            loadSystemApps()
        }
    }

    override fun onClearCacheApp(packageName: String) {
        // Clear cache
    }
}
