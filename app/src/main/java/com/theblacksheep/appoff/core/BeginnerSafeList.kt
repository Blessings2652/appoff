package com.theblacksheep.appoff.core

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.provider.Telephony

/**
 * Provides a curated recommended safe list of essential packages for beginners.
 * Shielding these packages prevents accidental disruption of daily messaging, email,
 * banking, navigation, clock alarms, keyboards, and phone utilities.
 */
object BeginnerSafeList {

    /**
     * Curated list of popular essential apps that beginners should shield/whitelist.
     */
    val POPULAR_ESSENTIAL_PACKAGES = setOf(
        // Messaging & Social
        "com.whatsapp",
        "com.whatsapp.w4b",
        "org.telegram.messenger",
        "org.telegram.messenger.web",
        "org.thoughtcrime.securesms", // Signal
        "com.facebook.orca", // Messenger
        "com.facebook.katana", // Facebook
        "com.instagram.android",
        "com.snapchat.android",
        "jp.naver.line.android",
        "com.viber.voip",
        "com.discord",
        "com.tencent.mm", // WeChat
        "com.bbm",
        "com.skype.raider",

        // Email & Workspace
        "com.google.android.gm", // Gmail
        "com.microsoft.office.outlook",
        "com.yahoo.mobile.client.android.mail",
        "com.Slack",
        "com.microsoft.teams",
        "com.google.android.apps.tasks",

        // Navigation & Transport
        "com.google.android.apps.maps",
        "com.waze",
        "com.ubercab",
        "me.lyft.android",
        "com.bolt.deliveryclient",

        // Phone / Messages / Clocks / Alarm / Calendar (Stock & OEM)
        "com.google.android.apps.messaging",
        "com.google.android.dialer",
        "com.google.android.contacts",
        "com.google.android.deskclock",
        "com.sec.android.app.clockpackage", // Samsung Clock
        "com.samsung.android.messaging",
        "com.samsung.android.dialer",
        "com.miui.clock", // Xiaomi Clock
        "com.coloros.alarmclock", // Oppo/Realme Clock
        "com.oneplus.deskclock",
        "com.google.android.calendar",
        "com.samsung.android.calendar",

        // Media & Streaming
        "com.spotify.music",
        "com.google.android.youtube",
        "com.google.android.apps.youtube.music",
        "com.netflix.mediaclient",

        // Banking & Payments / Financial
        "com.google.android.apps.walletnfcrel", // Google Pay/Wallet
        "com.google.android.apps.nbu.paisa.user", // GPay India
        "com.paypal.android.p2pmobile",
        "com.venmo",
        "com.cashapp.csh",

        // Storage, Cloud & Camera
        "com.google.android.apps.photos",
        "com.google.android.apps.docs",
        "com.dropbox.android",
        "com.google.android.GoogleCamera"
    )

    /**
     * Identifies installed packages on the user's device that match the beginner safe list or
     * critical system role packages (Default Keyboard, Default Launcher, Default SMS).
     */
    fun getInstalledBeginnerSafePackages(context: Context, installedApps: List<CleanableApp>): Set<String> {
        val pm = context.packageManager
        val result = mutableSetOf<String>()

        // 1. Match curated popular packages from installed apps
        val installedPackageNames = installedApps.map { it.packageName }.toSet()
        POPULAR_ESSENTIAL_PACKAGES.forEach { pkg ->
            if (pkg in installedPackageNames) {
                result.add(pkg)
            }
        }

        // 2. Default Input Method (Keyboard)
        try {
            val defaultIme = Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            if (!defaultIme.isNullOrBlank()) {
                val pkg = defaultIme.split("/").firstOrNull()
                if (!pkg.isNullOrBlank() && pkg in installedPackageNames) {
                    result.add(pkg)
                }
            }
        } catch (_: Exception) {}

        // 3. Default Launcher
        try {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val resolveInfo = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            resolveInfo?.activityInfo?.packageName?.let { pkg ->
                if (pkg in installedPackageNames) result.add(pkg)
            }
        } catch (_: Exception) {}

        // 4. Default SMS App
        try {
            val defaultSms = Telephony.Sms.getDefaultSmsPackage(context)
            if (!defaultSms.isNullOrBlank() && defaultSms in installedPackageNames) {
                result.add(defaultSms)
            }
        } catch (_: Exception) {}

        return result
    }
}
