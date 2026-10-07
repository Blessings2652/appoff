package com.theblacksheep.appoff.domain.repository

import android.content.Context
import com.theblacksheep.appoff.core.CleanableApp

/**
 * Clean Architecture Domain Repository Interface for App Management.
 */
interface IAppRepository {
    suspend fun getSystemApps(
        context: Context,
        extraWhitelist: Set<String>,
        runningPackages: Set<String> = emptySet(),
        packageMemoryUsage: Map<String, Long> = emptyMap()
    ): List<CleanableApp>

    suspend fun getAppByPackageName(context: Context, packageName: String): CleanableApp?
    suspend fun getTopDrainers(context: Context): List<CleanableApp>
}
