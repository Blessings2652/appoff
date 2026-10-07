package com.theblacksheep.appoff

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.theblacksheep.appoff.core.PROTECTED_PACKAGES
import com.theblacksheep.appoff.core.SettingsRepository
import com.theblacksheep.appoff.root.RootCleaner
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class RootCleanerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun testRootCleanDegradesGracefullyWhenSuUnavailable() = runBlocking {
        val packages = listOf("com.app.one", "com.app.two")
        var task = ""
        var progressCount = 0

        val result = RootCleaner.clean(
            context = context,
            packages = packages,
            cacheOnly = false,
            onProgress = { progressCount++ },
            onTaskUpdate = { task = it }
        )

        assertEquals("Root", result.strategy)
        assertEquals(2, result.attempted)
        // On standard unrooted test machine, su fails gracefully so 0 succeeded
        assertEquals(0, result.succeeded)
        assertTrue(result.succeededPackages.isEmpty())
        assertTrue(result.freedBytesEstimate >= 0)
        assertFalse(RootCleaner.isRootAvailable())
    }

    @Test
    fun testRootCleanFiltersWhitelistAndProtectedPackages() = runBlocking {
        val whitelistedPkg = "com.root.whitelisted"
        // Ensure it is whitelisted
        if (!SettingsRepository.getUserWhitelist(context).contains(whitelistedPkg)) {
            SettingsRepository.toggleWhitelistApp(context, whitelistedPkg)
        }

        val protectedPkg = PROTECTED_PACKAGES.first()
        val validPkg = "com.root.valid"
        val testPackages = listOf(whitelistedPkg, protectedPkg, validPkg)

        val result = RootCleaner.clean(
            context = context,
            packages = testPackages,
            cacheOnly = false
        )

        assertEquals(3, result.attempted)
        // All non-whitelisted/non-protected packages were processed; protected ones are skipped
        assertFalse(result.succeededPackages.contains(whitelistedPkg))
        assertFalse(result.succeededPackages.contains(protectedPkg))

        // Cleanup: Ensure it is removed
        if (SettingsRepository.getUserWhitelist(context).contains(whitelistedPkg)) {
            SettingsRepository.toggleWhitelistApp(context, whitelistedPkg)
        }
    }

    @Test
    fun testRootCleanCacheOnlyMode() = runBlocking {
        val testPackages = listOf("com.root.cache1", "com.root.cache2")
        var task = ""

        val result = RootCleaner.clean(
            context = context,
            packages = testPackages,
            cacheOnly = true,
            onProgress = {},
            onTaskUpdate = { task = it }
        )

        assertEquals("Root", result.strategy)
        assertEquals(2, result.attempted)
        assertTrue(task.isNotEmpty())
    }

    @Test
    fun testRootHelpersDegradeGracefullyWhenSuUnavailable() = runBlocking {
        // All root operations should degrade gracefully to false / default without throwing
        assertFalse(RootCleaner.forceStop("com.example.app"))
        assertFalse(RootCleaner.forceStopAdvanced("com.example.app"))
        assertFalse(RootCleaner.freeze("com.example.app"))
        assertFalse(RootCleaner.unfreeze("com.example.app"))
        assertFalse(RootCleaner.disable("com.example.app"))
        assertFalse(RootCleaner.enable("com.example.app"))
        assertFalse(RootCleaner.suspend("com.example.app", true))
        assertFalse(RootCleaner.setAppOp("com.example.app", 63, 1))
        assertFalse(RootCleaner.setStandbyBucket("com.example.app", 45))
        assertEquals(10, RootCleaner.getStandbyBucket("com.example.app"))
        assertFalse(RootCleaner.clearCache("com.example.app"))
        assertFalse(RootCleaner.uninstall("com.example.app"))
        assertFalse(RootCleaner.runShell("echo test"))
        assertNull(RootCleaner.runShellWithOutput("echo test"))
    }
}
