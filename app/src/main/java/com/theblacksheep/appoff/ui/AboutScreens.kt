package com.theblacksheep.appoff.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal fun openEmail(context: Context, email: String) {
    try {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:$email")
            putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
        }
        context.startActivity(Intent.createChooser(intent, "Send Email"))
    } catch (_: Exception) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("mailto:$email"))
            context.startActivity(intent)
        } catch (_: Exception) {}
    }
}

/** One row of the navigation drawer. Shared by the main menu and the About/legal group. */
@Composable
internal fun DrawerMenuRow(
    icon: ImageVector,
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun InfoScaffold(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(Modifier.width(4.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(Modifier.height(12.dp))
        content()
    }
}

// ---------------------------------------------------------------- About

@Composable
fun AboutScreen(onBack: () -> Unit, onOpen: (Screen) -> Unit) {
    val context = LocalContext.current
    val versionName = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "-"
        } catch (_: Exception) { "-" }
    }

    InfoScaffold(title = "About", onBack = onBack) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Memory,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "AppOff",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "Version $versionName",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            item {
                AboutCard {
                    Text(
                        LegalContent.ABOUT_DESCRIPTION,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Developer",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        LegalContent.DEVELOPER,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Contact Email",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        LegalContent.CONTACT,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        textDecoration = TextDecoration.Underline,
                        modifier = Modifier.clickable {
                            openEmail(context, LegalContent.CONTACT)
                        }
                    )
                }
            }
            item { AboutLink(Icons.Default.Email, "Contact Developer") { openEmail(context, LegalContent.CONTACT) } }
            item { AboutLink(Icons.Default.Gavel, "Terms of Service") { onOpen(Screen.TERMS) } }
            item { AboutLink(Icons.Default.PrivacyTip, "Privacy Policy") { onOpen(Screen.PRIVACY) } }
            item { AboutLink(Icons.Default.Description, "Open-source licenses") { onOpen(Screen.LICENSES) } }
        }
    }
}

@Composable
private fun AboutCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun AboutLink(icon: ImageVector, title: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(14.dp))
            Text(
                title,
                modifier = Modifier.weight(1f),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ---------------------------------------------------------------- Terms / Privacy

@Composable
private fun LegalBodyText(text: String) {
    val context = LocalContext.current
    val email = LegalContent.CONTACT

    if (text.contains(email)) {
        val startIndex = text.indexOf(email)
        val endIndex = startIndex + email.length
        val annotatedString = buildAnnotatedString {
            append(text.substring(0, startIndex))
            pushStringAnnotation(tag = "EMAIL", annotation = email)
            withStyle(
                style = SpanStyle(
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    textDecoration = TextDecoration.Underline
                )
            ) {
                append(email)
            }
            pop()
            append(text.substring(endIndex))
        }

        ClickableText(
            text = annotatedString,
            style = TextStyle(
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurface
            ),
            onClick = { offset ->
                annotatedString.getStringAnnotations(tag = "EMAIL", start = offset, end = offset)
                    .firstOrNull()?.let { annotation ->
                        openEmail(context, annotation.item)
                    }
            }
        )
    } else {
        Text(
            text = text,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun LegalDocumentScreen(title: String, sections: List<Pair<String, String>>, onBack: () -> Unit) {
    InfoScaffold(title = title, onBack = onBack) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item {
                Text(
                    "Last updated: ${LegalContent.LAST_UPDATED}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items(sections) { (heading, body) ->
                Column {
                    Text(
                        heading,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(4.dp))
                    LegalBodyText(body)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Licenses

@Composable
fun LicensesScreen(onBack: () -> Unit) {
    InfoScaffold(title = "Open-source licenses", onBack = onBack) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item {
                Text(
                    LegalContent.LICENSES_INTRO,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items(LegalContent.LIBRARIES) { lib ->
                AboutCard {
                    Text(lib.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text(lib.author, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    Text(lib.license, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
                    Text(lib.url, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                AboutCard {
                    Text(
                        "Apache License 2.0",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        LegalContent.APACHE_NOTICE,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Content

data class OpenSourceLibrary(val name: String, val author: String, val license: String, val url: String)

/**
 * Draft text. Have it reviewed (and fill in the contact line) before publishing.
 * Keep LIBRARIES in sync with the dependencies in build.gradle.
 */
object LegalContent {
    const val LAST_UPDATED = "October 2026"
    const val DEVELOPER = "The Blacksheep Software"
    const val CONTACT = "theblacksheepmw@gmail.com"

    const val ABOUT_DESCRIPTION =
        "AppOff helps you control background apps on your Android device: stop, freeze or disable apps, " +
        "clean junk and cache, and monitor system activity. Advanced actions use Shizuku or root access " +
        "that you grant yourself."

    val TERMS = listOf(
        "1. Acceptance" to
            "By installing or using AppOff you agree to these terms. If you do not agree, do not use the app.",
        "2. What the app does" to
            "AppOff can stop, suspend, disable, hide, uninstall-for-user and clear data or cache of apps on your device. " +
            "Some actions require elevated access (Shizuku, root or permissions granted through ADB). The app only uses " +
            "this access for the actions you start or the automation features you turn on.",
        "3. Your responsibility" to
            "Disabling or removing system or essential apps can cause instability, lost data, missed alarms or " +
            "notifications, or a device that no longer works as expected. You choose which apps to act on. Review the " +
            "selection before confirming, keep backups of important data, and do not disable apps you do not understand.",
        "4. Elevated access" to
            "Shizuku and root are third-party tools that you install and control. AppOff is not affiliated with them. " +
            "You are responsible for granting or revoking that access and for following your device maker's terms.",
        "5. No warranty" to
            "AppOff is provided \"as is\" and \"as available\", without warranties of any kind, express or implied, " +
            "including fitness for a particular purpose, accuracy of memory or usage figures, or uninterrupted operation.",
        "6. Limitation of liability" to
            "To the maximum extent permitted by law, the developer is not liable for any indirect, incidental or " +
            "consequential damages, or for loss of data or device functionality, arising from your use of the app.",
        "7. Acceptable use" to
            "Do not use AppOff to interfere with other people's devices, to break the law, or to bypass licensing or " +
            "security of apps you do not own or have permission to modify.",
        "8. Changes" to
            "These terms and the app may change over time. Continued use after an update means you accept the " +
            "updated terms.",
        "9. Contact" to "Questions about these terms: $CONTACT."
    )

    val PRIVACY = listOf(
        "Summary" to
            "AppOff works on your device. The app is not built to collect, sell or share your personal information.",
        "Information the app reads" to
            "To do its job the app reads information on your device: the list of installed apps, which apps are running, " +
            "memory, battery, CPU and network status, app usage statistics, and storage and cache sizes. This information " +
            "is used to show you your apps and system status and to perform the actions you request.",
        "Where it is stored" to
            "Settings such as your whitelist, preferences and the apps you froze are stored locally in the app's private " +
            "storage on your device. They are removed when you clear the app's data or uninstall it.",
        "Sharing" to
            "The app does not include advertising or analytics code and does not send the information above to the " +
            "developer or to third parties. If this ever changes, this policy will be updated and the change will be " +
            "described in the app before it takes effect.",
        "Permissions" to
            "AppOff requests many Android permissions (for example package visibility, usage access, notifications, " +
            "overlay, secure settings, storage access and foreground service) because system-management features need them. " +
            "Each permission is used only for the feature it relates to. You can revoke permissions at any time in " +
            "Android settings; related features will stop working.",
        "Shizuku and root" to
            "Commands run through Shizuku or root are executed locally on your device. Their handling of data is governed " +
            "by those tools, not by AppOff.",
        "Children" to
            "AppOff is not directed at children under 13 and does not knowingly collect their information.",
        "Changes" to "This policy may be updated. The date at the top shows when it last changed.",
        "Contact" to "Questions about privacy: $CONTACT."
    )

    const val LICENSES_INTRO =
        "AppOff is built with the following open-source software. Thank you to their authors and contributors."

    val LIBRARIES = listOf(
        OpenSourceLibrary("Shizuku API", "RikkaApps", "Apache License 2.0", "github.com/RikkaApps/Shizuku-API"),
        OpenSourceLibrary("Jetpack Compose & Material 3", "Google / AndroidX", "Apache License 2.0", "developer.android.com/jetpack/compose"),
        OpenSourceLibrary("Material Icons", "Google", "Apache License 2.0", "fonts.google.com/icons"),
        OpenSourceLibrary("AndroidX libraries (Activity, Lifecycle, Core)", "Google", "Apache License 2.0", "developer.android.com/jetpack/androidx"),
        OpenSourceLibrary("Kotlin & kotlinx.coroutines", "JetBrains", "Apache License 2.0", "kotlinlang.org")
    )

    const val APACHE_NOTICE =
        "Licensed under the Apache License, Version 2.0 (the \"License\"); you may not use these libraries " +
        "except in compliance with the License. You may obtain a copy of the License at " +
        "https://www.apache.org/licenses/LICENSE-2.0. Unless required by applicable law or agreed to in writing, " +
        "software distributed under the License is distributed on an \"AS IS\" BASIS, WITHOUT WARRANTIES OR " +
        "CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing " +
        "permissions and limitations under the License."
}
