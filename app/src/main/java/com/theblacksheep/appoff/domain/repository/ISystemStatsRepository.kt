package com.theblacksheep.appoff.domain.repository

import android.content.Context
import com.theblacksheep.appoff.core.SystemStats

/**
 * Clean Architecture Domain Repository Interface for System Monitoring.
 */
interface ISystemStatsRepository {
    fun getStats(context: Context): SystemStats
}
