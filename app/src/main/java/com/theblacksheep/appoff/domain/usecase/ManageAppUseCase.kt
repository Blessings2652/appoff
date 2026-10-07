package com.theblacksheep.appoff.domain.usecase

import android.app.Activity
import com.theblacksheep.appoff.service.AppActionHandler

/**
 * Clean Architecture Use Case: Perform App Management Actions.
 */
class ManageAppUseCase {
    suspend fun disableApp(activity: Activity, packageName: String): AppActionHandler.ActionResult {
        return AppActionHandler(activity).disableApp(packageName)
    }

    suspend fun enableApp(activity: Activity, packageName: String): AppActionHandler.ActionResult {
        return AppActionHandler(activity).enableApp(packageName)
    }

    suspend fun uninstallApp(activity: Activity, packageName: String): AppActionHandler.ActionResult {
        return AppActionHandler(activity).uninstallApp(packageName)
    }
}
