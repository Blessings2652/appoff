package com.theblacksheep.appoff.core

import android.os.Debug

data class VmStats(
    val dalvikMax: Long,
    val dalvikUsed: Long,
    val nativeUsed: Long,
    val processorCount: Int,
    val vmVersion: String
)

object VmRepository {

    fun getVmStats(): VmStats {
        val runtime = Runtime.getRuntime()
        
        // Dalvik Heap
        val maxMemory = runtime.maxMemory() // Max bytes VM will attempt to use
        val totalMemory = runtime.totalMemory() // Current bytes allocated
        val freeMemory = runtime.freeMemory() // Bytes available within totalMemory
        val usedMemory = totalMemory - freeMemory

        // Native Heap
        val nativeUsed = Debug.getNativeHeapAllocatedSize()

        return VmStats(
            dalvikMax = maxMemory,
            dalvikUsed = usedMemory,
            nativeUsed = nativeUsed,
            processorCount = runtime.availableProcessors(),
            vmVersion = System.getProperty("java.vm.version") ?: "Unknown"
        )
    }

    /**
     * Triggers runtime-level optimizations:
     * 1. Garbage Collection (JVM Hint)
     * 2. Finalization (Hint to run pending finalizers)
     * 3. Native Synchronization (via JNI)
     */
    fun triggerVmOptimization() {
        System.gc()
        Runtime.getRuntime().runFinalization()
        NativeMemoryUtils.optimizeMemoryNative()
    }
}
