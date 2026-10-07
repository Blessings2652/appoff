package com.theblacksheep.appoff.core

object NativeMemoryUtils {
    private var isLibLoaded = false

    init {
        try {
            System.loadLibrary("deepramcleaner")
            isLibLoaded = true
        } catch (e: Throwable) {
            android.util.Log.e("NativeMemoryUtils", "Failed to load deepramcleaner library", e)
        }
    }

    fun isAvailable() = isLibLoaded

    /**
     * Reads available memory directly from /proc/meminfo in C++.
     * Returns value in bytes.
     */
    fun getAvailableMemoryNative(): Long {
        return if (isLibLoaded) {
            try { nGetAvailableMemoryNative() } catch (_: Throwable) { -1L }
        } else -1L
    }

    private external fun nGetAvailableMemoryNative(): Long

    /**
     * Triggers native-level system synchronization and optimization.
     */
    fun optimizeMemoryNative() {
        if (isLibLoaded) {
            try { nOptimizeMemoryNative() } catch (_: Throwable) {}
        }
    }

    private external fun nOptimizeMemoryNative()

    /**
     * Triggers Kernel Cache Dropping.
     * level 1: Pagecache, 2: dentries/inodes, 3: both
     * Requires root permission for the process to write to /proc/sys/vm/
     */
    fun dropCachesNative(level: Int): Boolean {
        return if (isLibLoaded) {
            try { nDropCachesNative(level) } catch (_: Throwable) { false }
        } else false
    }

    private external fun nDropCachesNative(level: Int): Boolean

    /**
     * Triggers Kernel Memory Compaction.
     * Requires root permission.
     */
    fun compactMemoryNative(): Boolean {
        return if (isLibLoaded) {
            try { nCompactMemoryNative() } catch (_: Throwable) { false }
        } else false
    }

    private external fun nCompactMemoryNative(): Boolean

    /**
     * Trims the native heap of the current process.
     */
    fun trimMallocNative() {
        if (isLibLoaded) {
            try { nTrimMallocNative() } catch (_: Throwable) {}
        }
    }

    private external fun nTrimMallocNative()

    /**
     * Adjusts the OOM score of the current process to make it harder to kill.
     * Requires root permission.
     */
    fun setOomScoreAdjNative(score: Int): Boolean {
        return if (isLibLoaded) {
            try { nSetOomScoreAdjNative(score) } catch (_: Throwable) { false }
        } else false
    }

    private external fun nSetOomScoreAdjNative(score: Int): Boolean

    /**
     * Reads the kernel OOM score of a specific process.
     */
    fun getProcessOomScoreNative(pid: Int): Int {
        return if (isLibLoaded) {
            try { nGetProcessOomScoreNative(pid) } catch (_: Throwable) { -1 }
        } else -1
    }

    private external fun nGetProcessOomScoreNative(pid: Int): Int

    /**
     * Reads hardware measured FPS from system files if available.
     * Returns -1 if not supported.
     */
    fun getSystemFpsNative(): Int {
        return if (isLibLoaded) {
            try { nGetSystemFpsNative() } catch (_: Throwable) { -1 }
        } else -1
    }

    private external fun nGetSystemFpsNative(): Int
}
