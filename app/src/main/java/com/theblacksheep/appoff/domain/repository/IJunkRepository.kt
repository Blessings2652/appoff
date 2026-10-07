package com.theblacksheep.appoff.domain.repository

import android.content.Context
import com.theblacksheep.appoff.core.JunkItem

/**
 * Clean Architecture Domain Repository Interface for Junk Scanning & Cleaning.
 */
interface IJunkRepository {
    suspend fun scanJunk(context: Context, onProgress: (String) -> Unit = {}): List<JunkItem>
    suspend fun cleanJunk(items: List<JunkItem>, onProgress: (String) -> Unit = {}): Long
}
