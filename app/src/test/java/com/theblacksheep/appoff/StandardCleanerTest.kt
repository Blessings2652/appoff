package com.theblacksheep.appoff

import android.app.ActivityManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.theblacksheep.appoff.core.PROTECTED_PACKAGES
import com.theblacksheep.appoff.core.SettingsRepository
import com.theblacksheep.appoff.core.StandardCleaner
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class StandardCleanerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("cleaner_settings", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun testStandardCleanNormalExecution() = runBlocking {
        val testPackages = listOf("com.example.app1", "com.example.app2", "com.example.app3")
        var progressCount = 0
        var lastTaskUpdate = ""

        val result = StandardCleaner.clean(
            context = context,
            packages = testPackages,
            cacheOnly = false,
            onProgress = { progressCount++ },
            onTaskUpdate = { lastTaskUpdate = it }
        )

        assertEquals("Standard", result.strategy)
        assertEquals(testPackages.size, result.attempted)
        assertEquals(testPackages.size, result.succeeded)
        assertEquals(testPackages, result.succeededPackages)
        assertTrue(result.freedBytesEstimate >= 0)
        assertEquals(3, progressCount)
        assertTrue(lastTaskUpdate.isNotEmpty())
    }

    @Test
    fun testStandardCleanFiltersWhitelistAndProtectedPackages() = runBlocking {
        val whitelistedPkg = "com.whitelisted.app"
        // Ensure it is whitelisted
        if (!SettingsRepository.getUserWhitelist(context).contains(whitelistedPkg)) {
            SettingsRepository.toggleWhitelistApp(context, whitelistedPkg)
        }

        val protectedPkg = PROTECTED_PACKAGES.first()
        val validPkg = "com.valid.app"
        val testPackages = listOf(whitelistedPkg, protectedPkg, validPkg)

        var progressedPackages = mutableListOf<String>()

        val result = StandardCleaner.clean(
            context = context,
            packages = testPackages,
            cacheOnly = false,
            onProgress = { progressedPackages.add(it) },
            onTaskUpdate = {}
        )

        assertEquals(3, result.attempted)
        assertEquals(1, result.succeeded)
        assertEquals(listOf(validPkg), result.succeededPackages)
        assertEquals(listOf(validPkg), progressedPackages)
        assertFalse(result.succeededPackages.contains(whitelistedPkg))
        assertFalse(result.succeededPackages.contains(protectedPkg))

        // Cleanup: Ensure it is removed
        if (SettingsRepository.getUserWhitelist(context).contains(whitelistedPkg)) {
            SettingsRepository.toggleWhitelistApp(context, whitelistedPkg)
        }
    }

    @Test
    fun testStandardCleanCacheOnlyMode() = runBlocking {
        val testPackages = listOf("com.cache.app1", "com.cache.app2")
        var task = ""

        val result = StandardCleaner.clean(
            context = context,
            packages = testPackages,
            cacheOnly = true,
            onProgress = {},
            onTaskUpdate = { task = it }
        )

        assertEquals("Standard", result.strategy)
        assertEquals(2, result.attempted)
        assertEquals(2, result.succeeded)
        assertEquals("RAM pressure optimization", task)
    }

    @Test
    fun testStandardCleanDegradesGracefullyOnSecurityException() = runBlocking {
        val spyContext = spyk(context)
        val mockAm = mockk<ActivityManager>(relaxed = true)
        
        every { spyContext.getSystemService(Context.ACTIVITY_SERVICE) } returns mockAm
        every { mockAm.killBackgroundProcesses("com.failing.app") } throws SecurityException("Permission denied")

        val result = StandardCleaner.clean(
            context = spyContext,
            packages = listOf("com.failing.app"),
            cacheOnly = false
        )

        assertEquals("Standard", result.strategy)
        assertEquals(1, result.attempted)
        assertEquals(0, result.succeeded)
        assertTrue(result.succeededPackages.isEmpty())
    }

    @Test
    fun testStandardCleanDegradesGracefullyOnNullService() = runBlocking {
        val spyContext = spyk(context)
        every { spyContext.getSystemService(Context.ACTIVITY_SERVICE) } returns null

        val result = StandardCleaner.clean(
            context = spyContext,
            packages = listOf("com.app.one", "com.app.two"),
            cacheOnly = false
        )

        assertEquals("Standard", result.strategy)
        assertEquals(2, result.attempted)
        assertEquals(0, result.succeeded)
    }

    @Test
    fun testStandardCleanDegradesGracefullyOnEmptyPackageList() = runBlocking {
        val result = StandardCleaner.clean(
            context = context,
            packages = emptyList(),
            cacheOnly = false
        )

        assertEquals("Standard", result.strategy)
        assertEquals(0, result.attempted)
        assertEquals(0, result.succeeded)
        assertTrue(result.succeededPackages.isEmpty())
    }
}
