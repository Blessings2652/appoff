package com.theblacksheep.appoff.data.repository

import android.content.Context
import com.theblacksheep.appoff.core.SystemStats
import com.theblacksheep.appoff.core.SystemStatsProvider
import com.theblacksheep.appoff.domain.repository.ISystemStatsRepository

/**
 * Clean Architecture Data Layer: Implementation of ISystemStatsRepository.
 */
class SystemStatsRepositoryImpl : ISystemStatsRepository {
    override fun getStats(context: Context): SystemStats {
        return SystemStatsProvider.getStats(context)
    }
}
