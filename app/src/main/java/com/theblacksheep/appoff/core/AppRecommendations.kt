package com.theblacksheep.appoff.core

import android.content.Context

/** What we suggest doing with an app. DISABLE is reversible; UNINSTALL removes it for the user. */
enum class RecommendedAction { DISABLE, UNINSTALL }

data class AppRecommendation(
    val app: CleanableApp,
    val action: RecommendedAction,
    val reason: String,
    val risk: PackageRisk
)

object AppRecommendations {

    private const val UNUSED_DAYS = 30L

    /** Result of a build: visible recommendations plus how many protected packages were left out. */
    data class Result(val recommendations: List<AppRecommendation>, val hiddenProtected: Int)

    private data class Candidate(
        val app: CleanableApp,
        val action: RecommendedAction,
        val reason: String,
        /** Found by rule rather than the curated list: only kept when the risk analysis says SAFE. */
        val discovered: Boolean
    )

    /** Curated pre-installed apps that are widely considered optional. package -> reason. */
    private val KNOWN_OPTIONAL: Map<String, String> = mapOf(
        // Xiaomi / MIUI
        "com.miui.analytics" to "Sends usage analytics to the manufacturer",
        "com.miui.msa.global" to "System ads service",
        "com.miui.hybrid" to "Quick Apps, rarely used",
        "com.miui.bugreport" to "Bug report collector",
        "com.xiaomi.mipicks" to "GetApps store with promoted apps",
        "com.miui.player" to "Mi Music, replaceable",
        "com.miui.videoplayer" to "Mi Video, replaceable",
        "com.xiaomi.glgm" to "Game Center promotions",
        "com.miui.yellowpage" to "Yellow Pages, business listings",
        "com.mi.globalbrowser" to "Mi Browser, replaceable",
        // Facebook preinstalled helpers
        "com.facebook.appmanager" to "Facebook background installer",
        "com.facebook.services" to "Facebook background services",
        "com.facebook.system" to "Facebook system stub",
        // Samsung
        "com.samsung.android.bixby.agent" to "Bixby voice assistant",
        "com.samsung.android.bixby.service" to "Bixby background service",
        "com.samsung.android.app.spage" to "Samsung Free / Bixby Home feed",
        "com.samsung.android.arzone" to "AR Zone, rarely used",
        "com.samsung.android.app.tips" to "Tips app",
        // Google optional apps
        "com.google.android.videos" to "Google TV / Play Movies, optional",
        "com.google.android.music" to "Retired Play Music app",
        "com.google.android.apps.magazines" to "News app, optional",
        // Partner / third party preloads
        "com.netflix.partner.activation" to "Netflix preload helper",
        "com.microsoft.skydrive" to "Preloaded OneDrive",
        // AOSP leftovers
        "com.android.egg" to "Android Easter egg",
        "com.android.bookmarkprovider" to "Unused bookmark provider",
        "com.android.dreams.basic" to "Basic screensaver",
        "com.android.dreams.phototable" to "Photo table screensaver",
        "com.android.traceur" to "System tracing tool"
    )

    fun optionalReason(packageName: String): String? = KNOWN_OPTIONAL[packageName]

    private fun candidates(apps: List<CleanableApp>, whitelist: Set<String>, now: Long): List<Candidate> {
        val out = ArrayList<Candidate>()
        for (app in apps) {
            val pkg = app.packageName
            if (pkg in whitelist || pkg in CRITICAL_SYSTEM_PACKAGES || pkg in PROTECTED_PACKAGES) continue
            if (app.isDisabled || app.isHidden || app.isUninstalled) continue
            if (app.disableSafetyLevel == DisableSafetyLevel.DANGER) continue

            val known = KNOWN_OPTIONAL[pkg]
            if (known != null && app.isSystem) {
                out += Candidate(app, RecommendedAction.DISABLE, known, discovered = false)
                continue
            }
            if (app.lastUsedMillis <= 0L) continue
            val days = (now - app.lastUsedMillis) / 86_400_000L
            if (days < UNUSED_DAYS) continue
            if (!app.isSystem) {
                out += Candidate(app, RecommendedAction.UNINSTALL, "Not opened for $days days", discovered = false)
            } else if (PackageRiskAnalyzer.isOemPackage(pkg)) {
                out += Candidate(app, RecommendedAction.DISABLE, "Optional OEM app, not opened for $days days", discovered = true)
            }
        }
        return out
    }

    /**
     * Builds the list, then scores every candidate. Protected packages are always dropped, and a
     * curated entry cannot override the evidence: if analysis says it is more than SAFE/CAUTION the
     * UI only shows it under "advanced".
     */
    suspend fun build(
        context: Context,
        apps: List<CleanableApp>,
        whitelist: Set<String>,
        now: Long = System.currentTimeMillis()
    ): Result {
        val cands = candidates(apps, whitelist, now)
        if (cands.isEmpty()) return Result(emptyList(), 0)
        val risks = PackageRiskAnalyzer.analyze(context, cands.map { it.app.packageName })

        var hidden = 0
        val recs = ArrayList<AppRecommendation>()
        for (c in cands) {
            val risk = risks[c.app.packageName] ?: continue
            if (risk.level == RiskLevel.PROTECTED) { hidden++; continue }
            if (c.discovered && risk.level != RiskLevel.SAFE) continue
            recs += AppRecommendation(c.app, c.action, c.reason, risk)
        }
        recs.sortWith(compareBy({ it.risk.level.ordinal }, { it.action }, { it.app.label.lowercase() }))
        return Result(recs, hidden)
    }
}
