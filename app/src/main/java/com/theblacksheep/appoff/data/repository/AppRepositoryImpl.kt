package com.theblacksheep.appoff.data.repository

import android.content.Context
import com.theblacksheep.appoff.core.AppRepository
import com.theblacksheep.appoff.core.CleanableApp
import com.theblacksheep.appoff.domain.repository.IAppRepository

/**
 * Clean Architecture Data Layer: Implementation of IAppRepository.
 */
class AppRepositoryImpl : IAppRepository {
    override suspend fun getSystemApps(
        context: Context,
        extraWhitelist: Set<String>,
        runningPackages: Set<String>,
        packageMemoryUsage: Map<String, Long>
    ): List<CleanableApp> {
        return AppRepository.getCleanableApps(
            context = context,
            includeSystem = true,
            extraWhitelist = extraWhitelist,
            requireUsage = false,
            runningPackages = runningPackages,
            packageMemoryUsage = packageMemoryUsage
        )
    }

    override suspend fun getAppByPackageName(context: Context, packageName: String): CleanableApp? {
        return AppRepository.getAppByPackageName(context, packageName)
    }

    override suspend fun getTopDrainers(context: Context): List<CleanableApp> {
        return AppRepository.getTopDrainers(context)
    }
}
