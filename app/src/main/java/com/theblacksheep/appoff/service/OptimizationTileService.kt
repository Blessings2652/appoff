package com.theblacksheep.appoff.service

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.theblacksheep.appoff.core.AppRepository
import com.theblacksheep.appoff.core.MemoryUtils
import com.theblacksheep.appoff.core.StandardCleaner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OptimizationTileService : TileService() {

    private val serviceScope = CoroutineScope(Dispatchers.Main)

    override fun onClick() {
        super.onClick()
        val tile = qsTile
        if (tile.state == Tile.STATE_UNAVAILABLE) return

        tile.state = Tile.STATE_ACTIVE
        tile.updateTile()

        serviceScope.launch {
            val context = applicationContext
            val before = MemoryUtils.snapshot(context).availBytes
            
            // Perform a standard quick clean
            val apps = withContext(Dispatchers.IO) {
                val whitelist = com.theblacksheep.appoff.core.SettingsRepository.getUserWhitelist(context)
                AppRepository.getCleanableApps(context, includeSystem = false, extraWhitelist = whitelist)
                    .filter { !it.packageName.contains(context.packageName) && it.packageName !in whitelist }
                    .map { it.packageName }
            }

            if (apps.isNotEmpty()) {
                StandardCleaner.clean(context, apps)
                val after = MemoryUtils.snapshot(context).availBytes
                val freed = (after - before).coerceAtLeast(0)
                
                Toast.makeText(
                    context, 
                    "${MemoryUtils.formatBytes(freed)} RAM Freed", 
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(context, "Nothing to clean", Toast.LENGTH_SHORT).show()
            }

            tile.state = Tile.STATE_INACTIVE
            tile.updateTile()
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        qsTile.state = Tile.STATE_INACTIVE
        qsTile.updateTile()
    }
}
