package com.theblacksheep.appoff.data.repository

import android.content.Context
import com.theblacksheep.appoff.core.JunkItem
import com.theblacksheep.appoff.core.JunkRepository
import com.theblacksheep.appoff.domain.repository.IJunkRepository

/**
 * Clean Architecture Data Layer: Implementation of IJunkRepository.
 */
class JunkRepositoryImpl : IJunkRepository {
    override suspend fun scanJunk(context: Context, onProgress: (String) -> Unit): List<JunkItem> {
        return JunkRepository.scanJunk(context, onProgress)
    }

    override suspend fun cleanJunk(items: List<JunkItem>, onProgress: (String) -> Unit): Long {
        return JunkRepository.cleanJunk(items, onProgress)
    }
}
