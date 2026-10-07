package com.theblacksheep.appoff

import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import com.theblacksheep.appoff.shizuku.ShizukuCleaner
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import rikka.shizuku.Shizuku

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class ShizukuCleanerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        mockkStatic(Shizuku::class)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testShizukuCleanDegradesGracefullyWhenUnavailable() = runBlocking {
        every { Shizuku.pingBinder() } returns false
        every { Shizuku.checkSelfPermission() } returns PackageManager.PERMISSION_DENIED

        val packages = listOf("com.app.one", "com.app.two")
        val result = ShizukuCleaner.clean(
            context = context,
            packages = packages,
            cacheOnly = false
        )

        assertEquals("Shizuku", result.strategy)
        assertEquals(2, result.attempted)
        assertEquals(0, result.succeeded)
        assertTrue(result.succeededPackages.isEmpty())
        assertFalse(ShizukuCleaner.isShizukuAvailable())
    }

    @Test
    fun testShizukuCleanDegradesGracefullyWhenPingThrows() = runBlocking {
        every { Shizuku.pingBinder() } throws IllegalStateException("Binder died")

        val packages = listOf("com.app.one")
        val result = ShizukuCleaner.clean(
            context = context,
            packages = packages,
            cacheOnly = false
        )

        assertEquals("Shizuku", result.strategy)
        assertEquals(1, result.attempted)
        assertEquals(0, result.succeeded)
        assertFalse(ShizukuCleaner.isShizukuAvailable())
    }

    @Test
    fun testShizukuCleanDegradesGracefullyWhenPermissionDenied() = runBlocking {
        every { Shizuku.pingBinder() } returns true
        every { Shizuku.checkSelfPermission() } returns PackageManager.PERMISSION_DENIED

        val packages = listOf("com.app.one", "com.app.two")
        val result = ShizukuCleaner.clean(
            context = context,
            packages = packages,
            cacheOnly = false
        )

        assertEquals("Shizuku", result.strategy)
        assertEquals(2, result.attempted)
        assertEquals(0, result.succeeded)
        assertFalse(ShizukuCleaner.hasPermission())
    }

    @Test
    fun testShizukuHelpersDegradeGracefullyWhenUnavailable() = runBlocking {
        every { Shizuku.pingBinder() } returns false
        every { Shizuku.checkSelfPermission() } returns PackageManager.PERMISSION_DENIED

        assertFalse(ShizukuCleaner.forceStop("com.example.app"))
        assertFalse(ShizukuCleaner.freeze("com.example.app"))
        assertFalse(ShizukuCleaner.unfreeze("com.example.app"))
        assertFalse(ShizukuCleaner.disable("com.example.app"))
        assertFalse(ShizukuCleaner.enable("com.example.app"))
        assertFalse(ShizukuCleaner.clearCache("com.example.app"))
        assertFalse(ShizukuCleaner.suspend("com.example.app", true))
        assertFalse(ShizukuCleaner.setAppOp("com.example.app", 63, 1))
        assertFalse(ShizukuCleaner.setStandbyBucket("com.example.app", 45))
        assertFalse(ShizukuCleaner.uninstall("com.example.app"))
        
        val execResult = ShizukuCleaner.exec("ls")
        assertTrue(execResult.contains("Error", ignoreCase = true) || execResult.contains("not available", ignoreCase = true))
    }
}
