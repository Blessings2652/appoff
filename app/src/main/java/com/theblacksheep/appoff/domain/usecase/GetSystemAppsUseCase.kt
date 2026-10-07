package com.theblacksheep.appoff.domain.usecase

import android.content.Context
import com.theblacksheep.appoff.core.CleanableApp
import com.theblacksheep.appoff.domain.repository.IAppRepository

/**
 * Clean Architecture Use Case: Get System Applications.
 */
class GetSystemAppsUseCase(private val appRepository: IAppRepository) {
    suspend operator fun invoke(
        context: Context,
        extraWhitelist: Set<String>,
        runningPackages: Set<String> = emptySet(),
        packageMemoryUsage: Map<String, Long> = emptyMap()
    ): List<CleanableApp> {
        return appRepository.getSystemApps(
            context = context,
            extraWhitelist = extraWhitelist,
            runningPackages = runningPackages,
            packageMemoryUsage = packageMemoryUsage
        )
    }
}
