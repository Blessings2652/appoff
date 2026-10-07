package com.theblacksheep.appoff.core

import android.content.Context
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class JunkItem(
    val path: String,
    val size: Long,
    val type: JunkType,
    val category: String = "Other"
)

enum class JunkType { LOG, TEMP, CACHE, THUMBNAIL, EMPTY_FOLDER, APK, SYSTEM_CACHE, LARGE_FILE }

object JunkRepository {

    suspend fun scanJunk(context: Context, onProgress: (String) -> Unit): List<JunkItem> = withContext(Dispatchers.IO) {
        val junkList = mutableListOf<JunkItem>()
        
        try {
            // 1. App-specific Caches (standard)
            val roots = mutableListOf<File>()
            context.cacheDir?.let { roots.add(it) }
            context.externalCacheDir?.let { roots.add(it) }
            
            // 2. System Caches (Root/Shizuku only)
            val isRoot = com.theblacksheep.appoff.root.RootCleaner.isRootAvailable()
            val hasShizuku = com.theblacksheep.appoff.shizuku.ShizukuCleaner.hasPermission()
            
            if (isRoot || hasShizuku) {
                // We add placeholder for system cache that we'll clean via shell
                junkList.add(JunkItem("/data/dalvik-cache", 0, JunkType.SYSTEM_CACHE, "System"))
                junkList.add(JunkItem("/cache", 0, JunkType.SYSTEM_CACHE, "System"))
            }

            // 3. External Storage (Requires MANAGE_EXTERNAL_STORAGE on 11+)
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R || 
                Environment.isExternalStorageManager()) {
                try {
                    val external = Environment.getExternalStorageDirectory()
                    roots.add(external)
                    
                    // Specific high-junk folders
                    listOf("Android/data", "Android/obb", "Download", "Telegram", "WhatsApp/Media/.Statuses").forEach {
                        val folder = File(external, it)
                        if (folder.exists()) roots.add(folder)
                    }
                } catch (_: Exception) {}
            }

            for (root in roots.distinct()) {
                if (root.exists()) {
                    scanDirectory(root, junkList, onProgress, depth = 0)
                }
            }
        } catch (_: Exception) {}

        junkList.distinctBy { it.path }
    }

    private fun scanDirectory(dir: File, list: MutableList<JunkItem>, onProgress: (String) -> Unit, depth: Int) {
        if (depth > 10) return // Safety against infinite recursion or too deep paths
        
        val files = try { dir.listFiles() } catch (_: Exception) { null } ?: return
        
        if (files.isEmpty() && dir != Environment.getExternalStorageDirectory()) {
            if (dir.name != "0" && dir.name != "Android") { // Avoid adding root/critical folders as "empty"
                list.add(JunkItem(dir.absolutePath, 0, JunkType.EMPTY_FOLDER, "Clean Up"))
            }
            return
        }

        for (file in files) {
            onProgress(file.name)
            if (file.isDirectory) {
                val name = file.name.lowercase()
                when {
                    name == "cache" || name == ".thumbnails" || name == "temp" || name == "logs" -> {
                        val size = getFolderSize(file)
                        if (size > 0) {
                            list.add(JunkItem(file.absolutePath, size, 
                                if (name.startsWith(".")) JunkType.THUMBNAIL else JunkType.CACHE, "Caches"))
                        }
                    }
                    else -> scanDirectory(file, list, onProgress, depth + 1)
                }
            } else {
                val ext = file.extension.lowercase()
                val size = file.length()
                when {
                    ext == "log" -> list.add(JunkItem(file.absolutePath, size, JunkType.LOG, "Logs"))
                    ext == "tmp" || ext == "temp" -> list.add(JunkItem(file.absolutePath, size, JunkType.TEMP, "Temporary"))
                    ext == "apk" -> list.add(JunkItem(file.absolutePath, size, JunkType.APK, "App Installers"))
                    size > 100 * 1024 * 1024 -> list.add(JunkItem(file.absolutePath, size, JunkType.LARGE_FILE, "Large Files"))
                }
            }
        }
    }

    private fun getFolderSize(dir: File): Long {
        var size = 0L
        val files = try { dir.listFiles() } catch (_: Exception) { null } ?: return 0
        for (file in files) {
            size += if (file.isDirectory) getFolderSize(file) else file.length()
        }
        return size
    }

    suspend fun cleanJunk(items: List<JunkItem>, onProgress: (String) -> Unit = {}): Long = withContext(Dispatchers.IO) {
        var totalFreed = 0L
        
        // 1. Standard deletions
        for (item in items) {
            if (item.type == JunkType.SYSTEM_CACHE) continue
            onProgress(item.path.split("/").last())
            
            val file = File(item.path)
            val size = if (file.isDirectory) getFolderSize(file) else file.length()
            if (deleteRecursive(file)) {
                totalFreed += size
            }
        }
        
        // 2. Deep/System deletions (Root/Shizuku)
        val hasSystemJunk = items.any { it.type == JunkType.SYSTEM_CACHE }
        if (hasSystemJunk) {
            val cmd = "pm trim-caches 999999999999; rm -rf /data/dalvik-cache/*; rm -rf /cache/*"
            when {
                com.theblacksheep.appoff.root.RootCleaner.isRootAvailable() -> {
                    com.theblacksheep.appoff.root.RootCleaner.runShell(cmd)
                    totalFreed += 256 * 1024 * 1024 // Estimate 256MB for system caches
                }
                com.theblacksheep.appoff.shizuku.ShizukuCleaner.hasPermission() -> {
                    com.theblacksheep.appoff.shizuku.ShizukuCleaner.exec(cmd)
                    totalFreed += 128 * 1024 * 1024 // Estimate 128MB for trimmed caches
                }
            }
        }
        
        totalFreed
    }

    private fun deleteRecursive(fileOrDirectory: File): Boolean {
        if (fileOrDirectory.isDirectory) {
            for (child in fileOrDirectory.listFiles() ?: emptyArray()) {
                deleteRecursive(child)
            }
        }
        return try { fileOrDirectory.delete() } catch (_: Exception) { false }
    }
}
