@file:Suppress("DEPRECATION")

package com.theblacksheep.appoff.core

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.net.Uri
import android.provider.MediaStore
import android.view.accessibility.AccessibilityManager
import android.view.inputmethod.InputMethodManager
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * Risk categories, derived from a 0..100 score.
 * SAFE      0-24   optional OEM application, no important dependencies
 * CAUTION   25-49  OEM service, may affect a feature
 * ADVANCED  50-74  system-integrated service, needs dependency analysis
 * PROTECTED 75+    Android / Google / OEM core, disabling can break the device
 */
enum class RiskLevel(val label: String, val summary: String) {
    SAFE("SAFE", "Optional OEM application. No important dependencies."),
    CAUTION("CAUTION", "OEM service. May affect a feature."),
    ADVANCED("ADVANCED", "System-integrated service. Requires dependency analysis."),
    PROTECTED("PROTECTED", "Android / Google / OEM core. Disabling can break the device.");

    companion object {
        fun fromScore(score: Int): RiskLevel = when {
            score >= 75 -> PROTECTED
            score >= 50 -> ADVANCED
            score >= 25 -> CAUTION
            else -> SAFE
        }
    }
}

/** One piece of evidence. Positive points raise the risk, negative points lower it. */
data class RiskSignal(val points: Int, val text: String)

data class PackageRisk(
    val packageName: String,
    val score: Int,
    val level: RiskLevel,
    val signals: List<RiskSignal>,
    /** Other installed packages that depend on this one (permissions it defines, shared UID). */
    val dependents: List<String>
)

/**
 * Scores a package from what the system actually knows about it (flags, shared UID, services,
 * providers, privileged permissions, default roles, reverse dependencies) instead of a plain
 * blacklist. The curated optional-app list is only one signal and can never lower a package
 * that is hard-protected.
 */
object PackageRiskAnalyzer {

    private val OEM_PREFIXES = listOf(
        "com.samsung.", "com.sec.", "com.miui.", "com.xiaomi.", "com.mi.", "com.oneplus.",
        "net.oneplus.", "com.oppo.", "com.coloros.", "com.heytap.", "com.realme.", "com.huawei.",
        "com.hihonor.", "com.vivo.", "com.bbk.", "com.motorola.", "com.lenovo.", "com.asus.",
        "com.sonymobile.", "com.sony.", "com.nothing.", "com.transsion.", "com.tecno.", "com.infinix."
    )

    private const val SNAPSHOT_TTL_MS = 10 * 60 * 1000L

    private class Snapshot(
        val builtAt: Long,
        val launcherPackages: Set<String>,
        val strongRoles: Map<String, List<String>>,
        val weakRoles: Map<String, List<String>>,
        val activeComponents: Map<String, List<String>>,
        val dependents: Map<String, Set<String>>,
        val results: ConcurrentHashMap<String, PackageRisk> = ConcurrentHashMap()
    )

    @Volatile private var snapshot: Snapshot? = null
    private val mutex = Mutex()
    private val privilegedPermCache = ConcurrentHashMap<String, Boolean>()

    fun isOemPackage(packageName: String): Boolean = OEM_PREFIXES.any { packageName.startsWith(it) }

    suspend fun analyze(context: Context, packages: Collection<String>): Map<String, PackageRisk> =
        withContext(Dispatchers.Default) {
            val app = context.applicationContext
            val snap = getSnapshot(app)
            val out = HashMap<String, PackageRisk>(packages.size)
            for (pkg in packages) {
                val cached = snap.results[pkg]
                val risk = cached ?: compute(app, snap, pkg)?.also { snap.results[pkg] = it }
                if (risk != null) out[pkg] = risk
            }
            out
        }

    suspend fun analyze(context: Context, packageName: String): PackageRisk? =
        analyze(context, listOf(packageName))[packageName]

    private suspend fun getSnapshot(context: Context): Snapshot {
        val now = System.currentTimeMillis()
        snapshot?.let { if (now - it.builtAt < SNAPSHOT_TTL_MS) return it }
        return mutex.withLock {
            snapshot?.let { if (System.currentTimeMillis() - it.builtAt < SNAPSHOT_TTL_MS) return it }
            buildSnapshot(context).also { snapshot = it }
        }
    }

    // ------------------------------------------------------------------ snapshot

    private fun buildSnapshot(context: Context): Snapshot {
        val pm = context.packageManager

        // Launcher-visible packages
        val launcher = HashSet<String>()
        try {
            val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            pm.queryIntentActivities(main, 0).forEach { launcher += it.activityInfo.packageName }
        } catch (_: Exception) {}

        // Default handlers
        val strong = HashMap<String, MutableList<String>>()
        val weak = HashMap<String, MutableList<String>>()
        fun role(map: HashMap<String, MutableList<String>>, intent: Intent, name: String) {
            val pkg = try {
                pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
                    ?.activityInfo?.packageName?.takeIf { it != "android" }
            } catch (_: Exception) { null }
            if (pkg != null) map.getOrPut(pkg) { mutableListOf() } += name
        }
        role(strong, Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), "default home app")
        role(strong, Intent(Intent.ACTION_DIAL, Uri.parse("tel:123")), "default phone app")
        role(strong, Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:123")), "default messaging app")
        role(weak, Intent(Intent.ACTION_VIEW, Uri.parse("http://example.com")), "default browser")
        role(weak, Intent(MediaStore.ACTION_IMAGE_CAPTURE), "default camera")

        // Active system integrations
        val active = HashMap<String, MutableList<String>>()
        fun mark(pkg: String?, name: String) {
            if (pkg != null) active.getOrPut(pkg) { mutableListOf() } += name
        }
        try {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.enabledInputMethodList?.forEach { mark(it.packageName, "enabled keyboard") }
        } catch (_: Exception) {}
        try {
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            am?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                ?.forEach { mark(it.resolveInfo?.serviceInfo?.packageName, "enabled accessibility service") }
        } catch (_: Exception) {}
        try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
            dpm?.activeAdmins?.forEach { mark(it.packageName, "active device admin") }
        } catch (_: Exception) {}
        try {
            NotificationManagerCompat.getEnabledListenerPackages(context).forEach { mark(it, "notification listener") }
        } catch (_: Exception) {}

        // Reverse dependencies: who requests a permission this package defines, who shares its UID
        val deps = HashMap<String, MutableSet<String>>()
        try {
            val all: List<PackageInfo> = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
            val permOwner = HashMap<String, String>()
            for (pi in all) pi.permissions?.forEach { permOwner[it.name] = pi.packageName }
            val uidGroups = HashMap<String, MutableList<String>>()
            for (pi in all) {
                pi.requestedPermissions?.forEach { perm ->
                    val owner = permOwner[perm]
                    if (owner != null && owner != pi.packageName) {
                        deps.getOrPut(owner) { mutableSetOf() } += pi.packageName
                    }
                }
                pi.sharedUserId?.let { uidGroups.getOrPut(it) { mutableListOf() } += pi.packageName }
            }
            for ((_, members) in uidGroups) {
                if (members.size < 2) continue
                for (m in members) deps.getOrPut(m) { mutableSetOf() }.addAll(members.filter { it != m })
            }
        } catch (_: Exception) {}

        return Snapshot(System.currentTimeMillis(), launcher, strong, weak, active, deps)
    }

    // ------------------------------------------------------------------ scoring

    private fun isPrivilegedPermission(pm: PackageManager, name: String): Boolean =
        privilegedPermCache.getOrPut(name) {
            try {
                val level = pm.getPermissionInfo(name, 0).protectionLevel
                (level and PermissionInfo.PROTECTION_MASK_BASE) == PermissionInfo.PROTECTION_SIGNATURE ||
                    (level and PermissionInfo.PROTECTION_FLAG_PRIVILEGED) != 0
            } catch (_: Exception) { false }
        }

    private fun compute(context: Context, snap: Snapshot, pkg: String): PackageRisk? {
        val pm = context.packageManager
        val info: PackageInfo = try {
            pm.getPackageInfo(
                pkg,
                PackageManager.GET_PERMISSIONS or PackageManager.GET_SERVICES or PackageManager.GET_PROVIDERS
            )
        } catch (_: Exception) { return null }
        val ai = info.applicationInfo ?: return null

        val isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        val isPersistent = (ai.flags and ApplicationInfo.FLAG_PERSISTENT) != 0
        val dependents = snap.dependents[pkg]?.sorted() ?: emptyList()
        val signals = ArrayList<RiskSignal>()

        // ---- Hard protection: never allowed to drop below PROTECTED
        var forced: String? = null
        if (pkg in CRITICAL_SYSTEM_PACKAGES || pkg in PROTECTED_PACKAGES) {
            forced = "Core Android / Google component"
        } else if (info.sharedUserId?.startsWith("android.uid.") == true) {
            forced = "Runs in a core system UID (${info.sharedUserId})"
        }
        if (forced != null) {
            signals += RiskSignal(100, forced)
            return PackageRisk(pkg, 100, RiskLevel.PROTECTED, signals, dependents)
        }

        // ---- Evidence based signals
        if (isSystem) signals += RiskSignal(10, "Pre-installed system app")

        snap.strongRoles[pkg]?.let { signals += RiskSignal(80, "Is the ${it.joinToString(" and ")}") }
        snap.weakRoles[pkg]?.let { signals += RiskSignal(30, "Is the ${it.joinToString(" and ")}") }
        snap.activeComponents[pkg]?.let { signals += RiskSignal(55, "Has an ${it.distinct().joinToString(", ")} in use") }

        if (isPersistent) signals += RiskSignal(55, "Persistent process, started with the system")

        if (isSystem && ai.sourceDir?.contains("/priv-app/") == true) {
            signals += RiskSignal(15, "Privileged system app")
        }

        val boundServices = info.services?.count { it.permission?.startsWith("android.permission.BIND_") == true } ?: 0
        if (boundServices > 0) {
            val pts = (20 + (boundServices - 1) * 5).coerceAtMost(30)
            signals += RiskSignal(pts, "Provides $boundServices system-bound service(s)")
        }

        if (isSystem) {
            val privCount = info.requestedPermissions?.count { isPrivilegedPermission(pm, it) } ?: 0
            if (privCount > 0) {
                signals += RiskSignal((privCount * 3).coerceAtMost(20), "Holds $privCount privileged permission(s)")
            }
            val exportedProviders = info.providers?.count { it.exported && !it.authority.isNullOrEmpty() } ?: 0
            if (exportedProviders > 0) signals += RiskSignal(10, "Shares data through $exportedProviders content provider(s)")
        }

        if (info.sharedUserId != null) signals += RiskSignal(20, "Shares a user ID with other packages")

        when {
            dependents.size >= 4 -> signals += RiskSignal(35, "${dependents.size} other packages depend on it")
            dependents.size >= 2 -> signals += RiskSignal(25, "${dependents.size} other packages depend on it")
            dependents.size == 1 -> signals += RiskSignal(15, "1 other package depends on it")
        }

        if (isSystem) {
            when {
                pkg.startsWith("com.google.") -> signals += RiskSignal(25, "Google system component")
                pkg.startsWith("com.android.") -> signals += RiskSignal(20, "Android system component")
                isOemPackage(pkg) -> signals += RiskSignal(5, "OEM component")
            }
        }

        val hasLauncher = pkg in snap.launcherPackages
        if (hasLauncher) signals += RiskSignal(-10, "Normal app with a launcher icon")
        else if (isSystem) signals += RiskSignal(10, "Background component with no launcher icon")

        AppRecommendations.optionalReason(pkg)?.let {
            signals += RiskSignal(-35, "Commonly removed optional app: $it")
        }

        val score = signals.sumOf { it.points }.coerceIn(0, 100)
        return PackageRisk(
            packageName = pkg,
            score = score,
            level = RiskLevel.fromScore(score),
            signals = signals.sortedByDescending { abs(it.points) },
            dependents = dependents
        )
    }
}
