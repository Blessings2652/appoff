package com.theblacksheep.appoff.ui

import kotlinx.coroutines.launch
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.Launch
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Terminal
import com.theblacksheep.appoff.core.AppRecommendations
import com.theblacksheep.appoff.core.RiskLevel
import com.theblacksheep.appoff.core.JunkType
import com.theblacksheep.appoff.core.CleanableApp
import com.theblacksheep.appoff.core.DisableSafetyLevel
import com.theblacksheep.appoff.core.MemoryUtils
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import com.theblacksheep.appoff.R
import com.theblacksheep.appoff.ui.theme.*
import androidx.core.graphics.createBitmap
import kotlin.math.*

enum class CleanMode { STANDARD, SYSTEM, SHIZUKU, ROOT }
enum class AppTheme { LIGHT, DARK, BLACK, SYSTEM }
enum class Screen { 
    SPLASH, INTRO, MAIN, SETTINGS, CLEANUP_CONFIRM, DIAGNOSTICS, JUNK_CLEANER, 
    SYSTEM_TOOLS, PERMISSION_HUB, SHIELD, RECOMMENDED, SECURITY, ABOUT, TERMS, PRIVACY, LICENSES
}
enum class FreezerFilter { ALL, FROZEN, STOPPED, DISABLED }
enum class SelectionFilter { ALL, RUNNING, STOPPED, DISABLED, UNINSTALL }

data class Spacing(val small: Dp, val medium: Dp, val xLarge: Dp)
val LocalAppSpacing = staticCompositionLocalOf { Spacing(4.dp, 8.dp, 16.dp) }
// LocalCardColor and GlassCard moved to Components.kt

@Composable
private fun SwipeBackWrapper(
    onBack: () -> Unit,
    content: @Composable () -> Unit
) {
    // Handle system back button
    BackHandler(onBack = onBack)

    val density = LocalDensity.current
    val edgeWidth = with(density) { 40.dp.toPx() }
    val minSwipeDistance = with(density) { 100.dp.toPx() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val screenWidth = size.width
                    
                    val isLeftEdge = down.position.x <= edgeWidth
                    val isRightEdge = down.position.x >= screenWidth - edgeWidth
                    
                    if (isLeftEdge || isRightEdge) {
                        var horizontalDragAmount = 0f
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            
                            if (change.changedToUp()) {
                                // Trigger back if dragged far enough in the correct direction
                                if (isLeftEdge && horizontalDragAmount > minSwipeDistance) {
                                    onBack()
                                } else if (isRightEdge && horizontalDragAmount < -minSwipeDistance) {
                                    onBack()
                                }
                                break
                            }
                            
                            val dragAmount = change.position.x - change.previousPosition.x
                            horizontalDragAmount += dragAmount
                            
                            // If we've dragged significantly, consume the event to avoid 
                            // other UI elements (like lists) from intercepting it.
                            if (abs(horizontalDragAmount) > 20f) {
                                change.consume()
                            }
                            
                            // If user drags back in opposite direction or too much vertically, cancel
                            if (isLeftEdge && horizontalDragAmount < -20f) break
                            if (isRightEdge && horizontalDragAmount > 20f) break
                            if (abs(change.position.y - down.position.y) > 150f) {
                                break
                            }
                        }
                    }
                }
            }
    ) {
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CleanerScreen(
) {
    val viewModel: CleanerViewModel = viewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    val spacing = Spacing(4.dp, 8.dp, 16.dp)

    CompositionLocalProvider(
        LocalAppSpacing provides spacing,
        LocalCardColor provides Color(state.cardColor),
        LocalDensity provides LocalDensity.current.let { 
            Density(it.density, it.fontScale * state.fontScale)
        },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
        AnimatedContent(
            targetState = state.currentScreen,
            transitionSpec = {
                fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
            },
            label = "screen_transition"
        ) { targetScreen ->
            when (targetScreen) {
                Screen.SPLASH -> SplashScreen(state)
                Screen.INTRO -> AppIntroScreen(state, viewModel, onComplete = { viewModel.completeIntro() })
                Screen.SETTINGS -> SwipeBackWrapper(onBack = { viewModel.navigateTo(Screen.MAIN) }) {
                    SettingsScreen(state, viewModel, onBack = { viewModel.navigateTo(Screen.MAIN) })
                }
                Screen.PERMISSION_HUB -> SwipeBackWrapper(onBack = { viewModel.navigateTo(Screen.SETTINGS) }) {
                    PermissionHubScreen(state, viewModel, onBack = { viewModel.navigateTo(Screen.SETTINGS) })
                }
                Screen.CLEANUP_CONFIRM -> SwipeBackWrapper(onBack = { viewModel.navigateTo(Screen.MAIN) }) {
                    CleanupConfirmScreen(state, viewModel, onBack = { viewModel.navigateTo(Screen.MAIN) })
                }
                Screen.DIAGNOSTICS -> SwipeBackWrapper(onBack = { viewModel.navigateTo(Screen.MAIN) }) {
                    DiagnosticsScreen(state, viewModel, onBack = { viewModel.navigateTo(Screen.MAIN) })
                }
                Screen.JUNK_CLEANER -> SwipeBackWrapper(onBack = { viewModel.navigateTo(Screen.MAIN) }) {
                    JunkCleanerScreen(state, viewModel, onBack = { viewModel.navigateTo(Screen.MAIN) })
                }
                Screen.SYSTEM_TOOLS -> SwipeBackWrapper(onBack = { viewModel.navigateTo(Screen.MAIN) }) {
                    SystemToolsScreen(viewModel) { viewModel.navigateTo(Screen.MAIN) }
                }
                Screen.SHIELD -> SwipeBackWrapper(onBack = { viewModel.navigateTo(Screen.MAIN) }) {
                    ShieldScreen(state, viewModel, onBack = { viewModel.navigateTo(Screen.MAIN) })
                }
                Screen.RECOMMENDED -> SwipeBackWrapper(onBack = { viewModel.navigateTo(Screen.MAIN) }) {
                    RecommendedAppsScreen(state, viewModel, onBack = { viewModel.navigateTo(Screen.MAIN) })
                }
                Screen.SECURITY -> SwipeBackWrapper(onBack = { viewModel.navigateTo(Screen.MAIN) }) {
                    VirusScannerScreen(state, viewModel, onBack = { viewModel.navigateTo(Screen.MAIN) })
                }
                Screen.ABOUT -> SwipeBackWrapper(onBack = { viewModel.navigateTo(Screen.MAIN) }) {
                    AboutScreen(onBack = { viewModel.navigateTo(Screen.MAIN) }, onOpen = { viewModel.navigateTo(it) })
                }
                Screen.TERMS -> SwipeBackWrapper(onBack = { viewModel.navigateTo(Screen.ABOUT) }) {
                    LegalDocumentScreen("Terms of Service", LegalContent.TERMS, onBack = { viewModel.navigateTo(Screen.ABOUT) })
                }
                Screen.PRIVACY -> SwipeBackWrapper(onBack = { viewModel.navigateTo(Screen.ABOUT) }) {
                    LegalDocumentScreen("Privacy Policy", LegalContent.PRIVACY, onBack = { viewModel.navigateTo(Screen.ABOUT) })
                }
                Screen.LICENSES -> SwipeBackWrapper(onBack = { viewModel.navigateTo(Screen.ABOUT) }) {
                    LicensesScreen(onBack = { viewModel.navigateTo(Screen.ABOUT) })
                }
                Screen.MAIN -> MainContent(state, viewModel)
            }
        }

        if (state.isCleaning || state.isCooling) {
            CleaningOverlay(state)
        }

        if (state.showPermissionInstructions) {
            SecureSettingsDialog(
                state = state,
                onDismiss = viewModel::dismissInstructions,
                onOpenSettings = viewModel::requestWriteSettings,
            )
        }

        if (state.showDisabledAppsDrawer) {
            DisabledAppsDrawer(state, viewModel)
        }
        }
    }
}

@Composable
private fun SettingsScreen(state: CleanerUiState, viewModel: CleanerViewModel, onBack: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val triggerHaptic = {
        if (state.hapticFeedbackEnabled) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        
        // Redesigned Top Bar with Live Status Chip
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { 
                        triggerHaptic()
                        onBack() 
                    },
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack, 
                        contentDescription = "Back", 
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        stringResource(R.string.system_configuration), 
                        style = MaterialTheme.typography.titleMedium, 
                        fontWeight = FontWeight.Bold, 
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        "PRO ENGINE • CONTROL CENTER",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Live Privilege Status Badge
            val (statusText, statusColor) = when {
                state.rootAvailable -> "ROOT ACTIVE" to Accent
                state.shizukuGranted -> "SHIZUKU READY" to Accent
                else -> "STANDARD MODE" to MaterialTheme.colorScheme.onSurfaceVariant
            }

            Surface(
                color = statusColor.copy(alpha = 0.12f),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, statusColor.copy(alpha = 0.25f))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = statusText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = statusColor
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 28.dp)
        ) {
            // SECTION 1: Privileged Access Engine
            item {
                SettingsSectionContainer(
                    title = stringResource(R.string.privileged_access),
                    icon = Icons.Default.AdminPanelSettings,
                    badgeText = if (state.rootAvailable || state.shizukuGranted) "PRIVILEGED" else "STANDARD",
                    badgeColor = if (state.rootAvailable || state.shizukuGranted) Accent else MaterialTheme.colorScheme.primary
                ) {
                    PrivilegeAccessCard(
                        state = state,
                        onAction = {
                            triggerHaptic()
                            viewModel.requestShizukuAccess()
                        }
                    )
                }
            }

            // SECTION 2: Automation & Optimization Engine
            item {
                SettingsSectionContainer(
                    title = stringResource(R.string.automation),
                    icon = Icons.Default.RocketLaunch,
                    badgeText = if (state.autoCleanEnabled || state.cleanOnBootEnabled) "ENABLED" else null
                ) {
                    AutoCleanSettings(state, viewModel)
                }
            }

            // SECTION 3: Visual & Interface Customization
            item {
                SettingsSectionContainer(
                    title = stringResource(R.string.appearance),
                    icon = Icons.Default.Palette
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(
                            stringResource(R.string.app_theme),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        ThemeSegmentedSelector(
                            currentTheme = state.theme,
                            onSelect = {
                                triggerHaptic()
                                viewModel.setTheme(it)
                            }
                        )

                        HorizontalDivider(color = Color.White.copy(alpha = 0.05f))

                        Text(
                            stringResource(R.string.icon_style),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        IconStyleSegmentedSelector(
                            currentStyle = state.iconStyle,
                            onSelect = {
                                triggerHaptic()
                                viewModel.setIconStyle(it)
                            }
                        )

                        HorizontalDivider(color = Color.White.copy(alpha = 0.05f))

                        PrecisionSliderRow(
                            title = stringResource(R.string.font_size),
                            valueText = "${(state.fontScale * 100).toInt()}%",
                            value = state.fontScale,
                            valueRange = 0.8f..1.4f,
                            steps = 5,
                            minLabel = "80%",
                            maxLabel = "140%",
                            onValueChange = { viewModel.setFontScale(it) }
                        )

                        HorizontalDivider(color = Color.White.copy(alpha = 0.05f))

                        OutlinedButton(
                            onClick = {
                                triggerHaptic()
                                viewModel.reopenIntro()
                            },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth().height(42.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                Text(
                                    stringResource(R.string.intro_replay),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            // SECTION 5: System Tweaks & Developer Power Tools
            item {
                SettingsSectionContainer(
                    title = stringResource(R.string.system_tweaks),
                    icon = Icons.Default.Tune
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SettingsSwitchRow(
                            title = stringResource(R.string.turbo_mode),
                            subtitle = stringResource(R.string.turbo_mode_desc),
                            icon = Icons.Default.Bolt,
                            checked = state.isTurboModeEnabled,
                            onCheckedChange = {
                                triggerHaptic()
                                viewModel.toggleTurboMode()
                            }
                        )

                        HorizontalDivider(color = Color.White.copy(alpha = 0.05f))

                        SettingsSwitchRow(
                            title = stringResource(R.string.fast_animations),
                            subtitle = stringResource(R.string.fast_animations_desc),
                            icon = Icons.Default.FlashOn,
                            checked = state.fastAnimationsEnabled,
                            onCheckedChange = {
                                triggerHaptic()
                                viewModel.toggleFastAnimations()
                            }
                        )

                        HorizontalDivider(color = Color.White.copy(alpha = 0.05f))

                        SettingsSwitchRow(
                            title = stringResource(R.string.force_hw_accel),
                            subtitle = stringResource(R.string.force_hw_accel_desc),
                            icon = Icons.Default.Layers,
                            checked = state.hardwareAccelerationEnabled,
                            onCheckedChange = {
                                triggerHaptic()
                                viewModel.toggleHardwareAcceleration()
                            }
                        )

                        HorizontalDivider(color = Color.White.copy(alpha = 0.05f))

                        SettingsSwitchRow(
                            title = stringResource(R.string.enable_dev_options),
                            subtitle = stringResource(R.string.debug_desc),
                            icon = Icons.Default.Build,
                            checked = state.isDeveloperOptionsEnabled,
                            onCheckedChange = {
                                triggerHaptic()
                                viewModel.toggleDeveloperOptions()
                            }
                        )

                        HorizontalDivider(color = Color.White.copy(alpha = 0.05f))

                        SettingsSwitchRow(
                            title = stringResource(R.string.wireless_debugging),
                            subtitle = "Enable ADB over Wi-Fi for untethered tweaking",
                            icon = Icons.Default.Wifi,
                            checked = state.isWirelessDebugEnabled,
                            onCheckedChange = {
                                triggerHaptic()
                                viewModel.toggleWirelessDebug()
                            }
                        )

                        HorizontalDivider(color = Color.White.copy(alpha = 0.05f))

                        SettingsClickableRow(
                            title = stringResource(R.string.developer_options),
                            subtitle = stringResource(R.string.developer_options_desc),
                            icon = Icons.Default.Terminal,
                            onClick = {
                                triggerHaptic()
                                viewModel.launchDeveloperOptions()
                            }
                        )
                    }
                }
            }

            // SECTION 6: Technical Audit & Maintenance Engine
            item {
                SettingsSectionContainer(
                    title = stringResource(R.string.troubleshooting),
                    icon = Icons.Default.Shield
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SettingsClickableRow(
                            title = stringResource(R.string.permission_hub),
                            subtitle = stringResource(R.string.permission_hub_desc),
                            icon = Icons.Default.Security,
                            trailingText = "Audit",
                            onClick = {
                                triggerHaptic()
                                viewModel.navigateTo(Screen.PERMISSION_HUB)
                            }
                        )

                        HorizontalDivider(color = Color.White.copy(alpha = 0.05f))

                        Text(
                            stringResource(R.string.troubleshooting_desc),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    triggerHaptic()
                                    viewModel.refresh(manual = true)
                                },
                                modifier = Modifier.weight(1f).height(42.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                    contentColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Refresh Engine", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = {
                                    triggerHaptic()
                                    if (!state.isBatteryOptimizationsIgnored) viewModel.requestBatteryOptimizationExemption()
                                },
                                modifier = Modifier.weight(1f).height(42.dp),
                                shape = RoundedCornerShape(10.dp),
                                enabled = !state.isBatteryOptimizationsIgnored,
                                border = BorderStroke(
                                    1.dp,
                                    if (state.isBatteryOptimizationsIgnored) Accent.copy(alpha = 0.4f)
                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                                )
                            ) {
                                Icon(
                                    Icons.Default.BatteryChargingFull,
                                    null,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (state.isBatteryOptimizationsIgnored) Accent else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    if (state.isBatteryOptimizationsIgnored) "Unrestricted" else "Battery Limits",
                                    fontSize = 11.sp,
                                    color = if (state.isBatteryOptimizationsIgnored) Accent else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            // SECTION 7: System Specs Terminal Card
            item {
                SystemSpecsTerminalCard(state = state)
            }
        }
    }
}

@Composable
private fun SettingsSectionContainer(
    title: String,
    icon: ImageVector,
    badgeText: String? = null,
    badgeColor: Color = MaterialTheme.colorScheme.primary,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            icon, 
                            contentDescription = null, 
                            tint = MaterialTheme.colorScheme.primary, 
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        text = title.uppercase(),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp,
                            fontSize = 12.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                if (badgeText != null) {
                    Surface(
                        color = badgeColor.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.25f))
                    ) {
                        Text(
                            text = badgeText,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Black,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace
                            ),
                            color = badgeColor
                        )
                    }
                }
            }
            content()
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        if (icon != null) {
            Icon(
                icon, 
                contentDescription = null, 
                tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title, 
                fontWeight = FontWeight.SemiBold, 
                fontSize = 14.sp, 
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle, 
                    color = MaterialTheme.colorScheme.onSurfaceVariant, 
                    fontSize = 11.sp, 
                    lineHeight = 15.sp
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.scale(0.75f),
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedBorderColor = MaterialTheme.colorScheme.primary,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
    }
}

@Composable
private fun SettingsClickableRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    trailingText: String? = null,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp)
    ) {
        if (icon != null) {
            Icon(
                icon, 
                contentDescription = null, 
                tint = MaterialTheme.colorScheme.primary, 
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title, 
                fontWeight = FontWeight.SemiBold, 
                fontSize = 14.sp, 
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle, 
                    color = MaterialTheme.colorScheme.onSurfaceVariant, 
                    fontSize = 11.sp, 
                    lineHeight = 15.sp
                )
            }
        }
        if (trailingText != null) {
            Text(
                trailingText, 
                fontSize = 12.sp, 
                fontWeight = FontWeight.Medium, 
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 4.dp)
            )
        }
        Icon(
            Icons.Default.ChevronRight, 
            contentDescription = null, 
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun ThemeSegmentedSelector(
    currentTheme: AppTheme,
    onSelect: (AppTheme) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.25f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val themes = listOf(
            Triple(AppTheme.LIGHT, "Light", Color(0xFFE2E8F0)),
            Triple(AppTheme.DARK, "Dark", Color(0xFF1E293B)),
            Triple(AppTheme.BLACK, "OLED", Color(0xFF000000)),
            Triple(AppTheme.SYSTEM, "Auto", Color(0xFF64748B))
        )
        themes.forEach { (theme, label, previewColor) ->
            val isSelected = currentTheme == theme
            Surface(
                onClick = { onSelect(theme) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
                color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent,
                border = BorderStroke(
                    1.dp,
                    if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                )
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(previewColor)
                            .border(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.2f), CircleShape)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = label,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun IconStyleSegmentedSelector(
    currentStyle: String,
    onSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.25f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val styles = listOf(
            Triple("ROUNDED", stringResource(R.string.icon_rounded), RoundedCornerShape(8.dp)),
            Triple("SHARP", stringResource(R.string.icon_sharp), RoundedCornerShape(0.dp)),
            Triple("CYBER", stringResource(R.string.icon_cyberpunk), RoundedCornerShape(topStart = 8.dp, bottomEnd = 8.dp))
        )
        styles.forEach { (id, label, shape) ->
            val isSelected = currentStyle == id
            val accent = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            Surface(
                onClick = { onSelect(id) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
                color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent,
                border = BorderStroke(
                    1.dp,
                    if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                )
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(shape)
                            .background(accent.copy(alpha = 0.2f))
                            .border(1.5.dp, accent, shape)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = label,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = accent
                    )
                }
            }
        }
    }
}

@Composable
private fun PrecisionSliderRow(
    title: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    minLabel: String,
    maxLabel: String,
    onValueChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.2f))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                title,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
            ) {
                Text(
                    text = valueText,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                minLabel, 
                fontSize = 10.sp, 
                fontFamily = FontFamily.Monospace, 
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                steps = steps,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = Color.White.copy(alpha = 0.1f)
                )
            )
            Text(
                maxLabel, 
                fontSize = 10.sp, 
                fontFamily = FontFamily.Monospace, 
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SystemSpecsTerminalCard(state: CleanerUiState) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color.Black.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Accent)
                )
                Text(
                    "SYSTEM AUDIT & SPECS",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = Accent
                )
            }
            
            HorizontalDivider(color = Color.White.copy(alpha = 0.05f), modifier = Modifier.padding(bottom = 10.dp))

            val items = listOf(
                "ENGINE VERSION" to "2.0.0 (Pro Engine)",
                "BUILD STAMP" to "2026.08.10",
                "ANDROID OS" to if (state.deviceInfo.androidVersion.isNotEmpty()) "v${state.deviceInfo.androidVersion}" else "Android System",
                "SECURITY PATCH" to if (state.deviceInfo.securityPatch.isNotEmpty()) state.deviceInfo.securityPatch else "Up to date",
                "KERNEL" to if (state.deviceInfo.kernelVersion.isNotEmpty()) state.deviceInfo.kernelVersion else "Linux Standard"
            )

            items.forEach { (label, value) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        label,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Text(
                        value,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.copyright),
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}


@Composable
private fun AdbHelpDialog(state: CleanerUiState, viewModel: CleanerViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    
    val commands = listOf(
        "WRITE_SECURE_SETTINGS" to "adb shell pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS",
        "WRITE_SETTINGS" to "adb shell pm grant ${context.packageName} android.permission.WRITE_SETTINGS",
        "FORCE_STOP_PACKAGES" to "adb shell pm grant ${context.packageName} android.permission.FORCE_STOP_PACKAGES",
        "USAGE_STATS" to "adb shell pm grant ${context.packageName} android.permission.PACKAGE_USAGE_STATS",
        "DUMP" to "adb shell pm grant ${context.packageName} android.permission.DUMP",
        "READ_LOGS" to "adb shell pm grant ${context.packageName} android.permission.READ_LOGS"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ADB Privileged Grant", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Some permissions require ADB or Root. If Shizuku is unavailable, use these commands via your PC:",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                
                commands.forEach { (label, cmd) ->
                    Text(label, fontWeight = FontWeight.Black, fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(4.dp))
                    SelectionContainer {
                            Surface(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(cmd))
                                    android.widget.Toast.makeText(context, "Command copied", android.widget.Toast.LENGTH_SHORT).show()
                                },
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                            ) {
                            Text(
                                text = cmd,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
                
                Text(
                    "Note: FORCE_STOP_PACKAGES may fail on some devices if not rooted. Use Shizuku for best results.",
                    fontSize = 11.sp,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    color = Warning
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onSurface)
            ) {
                Text("GOT IT")
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
private fun SecureSettingsDialog(state: CleanerUiState, onOpenSettings: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.permission_required), fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    stringResource(R.string.secure_settings_instructions),
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(12.dp))
                
                val cmd1 = "adb shell pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS"
                SelectionContainer {
                    Surface(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(cmd1))
                            android.widget.Toast.makeText(context, "Command copied", android.widget.Toast.LENGTH_SHORT).show()
                        },
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = cmd1,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
                
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.write_settings_instructions),
                    fontSize = 13.sp
                )
                
                val cmd2 = "adb shell pm grant ${context.packageName} android.permission.WRITE_SETTINGS"
                SelectionContainer {
                    Surface(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(cmd2))
                            android.widget.Toast.makeText(context, "Command copied", android.widget.Toast.LENGTH_SHORT).show()
                        },
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) {
                        Text(
                            text = cmd2,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = { 
                    if (state.hapticFeedbackEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onOpenSettings() 
                }) {
                    Text(stringResource(R.string.open_settings), color = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { 
                            if (state.hapticFeedbackEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onDismiss() 
                        }, 
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onSurface)
                    ) {
                        Text(stringResource(R.string.ok))
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        textContentColor = MaterialTheme.colorScheme.onSurface,
        titleContentColor = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun SplashScreen(state: CleanerUiState) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val backgroundColor = MaterialTheme.colorScheme.background
    val surfaceColor = MaterialTheme.colorScheme.surface

    val infiniteTransition = rememberInfiniteTransition(label = "splash_infinite")
    
    // Rotating sci-fi ring 1 (clockwise)
    val rotation1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation1"
    )

    // Rotating sci-fi ring 2 (counter-clockwise)
    val rotation2 by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation2"
    )

    // Pulsing aura glow scale
    val auraScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "aura_scale"
    )

    // Pulsing aura glow alpha
    val auraAlpha by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "aura_alpha"
    )

    // Determine target progress (0.0f to 1.0f) based on splashStepLabel
    val targetProgress = when {
        state.splashStepLabel.contains("Ready", ignoreCase = true) -> 1.0f
        state.splashStepLabel.contains("Memory", ignoreCase = true) -> 0.8f
        state.splashStepLabel.contains("Privileges", ignoreCase = true) -> 0.6f
        state.splashStepLabel.contains("AppOff", ignoreCase = true) -> 0.4f
        else -> 0.2f
    }

    val animatedProgress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "splash_progress"
    )

    val engineModeLabel = when {
        state.rootAvailable -> "ENGINE: ROOT PRIVILEGED"
        state.shizukuAvailable && state.shizukuGranted -> "ENGINE: SHIZUKU ACTIVE"
        state.systemPrivileged -> "ENGINE: SYSTEM ACCESS"
        else -> "ENGINE: HIGH-PERFORMANCE"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.12f),
                        backgroundColor,
                        backgroundColor
                    ),
                    radius = 1200f
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // Decorative background glowing ambient circles
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerOffset = Offset(size.width / 2f, size.height / 2.3f)
            drawCircle(
                color = primaryColor.copy(alpha = auraAlpha * 0.3f),
                radius = 220.dp.toPx() * auraScale,
                center = centerOffset
            )
            drawCircle(
                color = primaryColor.copy(alpha = auraAlpha * 0.15f),
                radius = 320.dp.toPx() * auraScale,
                center = centerOffset
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 48.dp, horizontal = 24.dp)
        ) {
            Spacer(Modifier.height(1.dp))

            // Center Logo & Hero Section
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(200.dp)
                ) {
                    // Outer sci-fi tech ring 1
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        rotate(rotation1) {
                            val strokeWidth = 2.dp.toPx()
                            val radius = size.minDimension / 2f - strokeWidth
                            drawCircle(
                                color = primaryColor.copy(alpha = 0.15f),
                                radius = radius,
                                style = Stroke(width = strokeWidth)
                            )
                            for (i in 0..2) {
                                drawArc(
                                    color = primaryColor,
                                    startAngle = i * 120f,
                                    sweepAngle = 40f,
                                    useCenter = false,
                                    style = Stroke(width = strokeWidth + 1.dp.toPx(), cap = StrokeCap.Round)
                                )
                            }
                        }
                    }

                    // Inner counter-rotating sci-fi ring 2
                    Canvas(modifier = Modifier.size(160.dp)) {
                        rotate(rotation2) {
                            val strokeWidth = 1.5.dp.toPx()
                            val radius = size.minDimension / 2f - strokeWidth
                            drawCircle(
                                color = primaryColor.copy(alpha = 0.2f),
                                radius = radius,
                                style = Stroke(width = strokeWidth, pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f))
                            )
                            for (i in 0..3) {
                                drawArc(
                                    color = primaryColor.copy(alpha = 0.8f),
                                    startAngle = i * 90f + 15f,
                                    sweepAngle = 30f,
                                    useCenter = false,
                                    style = Stroke(width = strokeWidth + 1.dp.toPx(), cap = StrokeCap.Round)
                                )
                            }
                        }
                    }

                    // Outer Glowing Backing Surface
                    Box(
                        modifier = Modifier
                            .size(110.dp)
                            .scale(auraScale)
                            .background(
                                color = primaryColor.copy(alpha = auraAlpha * 0.25f),
                                shape = CircleShape
                            )
                    )

                    // Central High-Tech Icon Card matching App Launcher Icon
                    Surface(
                        modifier = Modifier.size(96.dp),
                        shape = RoundedCornerShape(28.dp),
                        color = Color(0xFF0D1B2A),
                        border = BorderStroke(2.dp, Brush.linearGradient(listOf(primaryColor, primaryColor.copy(alpha = 0.3f)))),
                        shadowElevation = 16.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                                contentDescription = "App Launcher Icon",
                                modifier = Modifier.size(72.dp)
                            )
                        }
                    }

                    // Version / Mode Badge Overlay
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .offset(y = (-6).dp),
                        shape = RoundedCornerShape(12.dp),
                        color = primaryColor,
                        shadowElevation = 4.dp
                    ) {
                        Text(
                            text = "PRO 2.0",
                            color = backgroundColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(Modifier.height(36.dp))

                // App Name Title
                Text(
                    text = stringResource(R.string.ram_cleaner),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onBackground,
                    letterSpacing = 4.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(8.dp))

                // Subtitle Tagline Pill
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = primaryColor.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.25f))
                ) {
                    Text(
                        text = "DEEP OPTIMIZATION ENGINE",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = primaryColor,
                        letterSpacing = 2.5.sp,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
                    )
                }
            }

            // Bottom Progress & Status Section
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) {
                // Step Progress Text & Percentage
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(primaryColor, CircleShape)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = state.splashStepLabel,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Text(
                        text = "${(animatedProgress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = primaryColor,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(Modifier.height(10.dp))

                // Custom Animated Glowing Progress Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animatedProgress)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        primaryColor.copy(alpha = 0.6f),
                                        primaryColor
                                    )
                                )
                            )
                    )
                }

                Spacer(Modifier.height(28.dp))

                // Active Engine Indicator Pill
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = primaryColor,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = engineModeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}


@Composable
private fun MainContent(
    state: CleanerUiState,
    viewModel: CleanerViewModel
) {
    val haptic = LocalHapticFeedback.current
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var isSearchExpanded by remember { mutableStateOf(state.selectionSearchQuery.isNotEmpty()) }
    var showSnapshotDialog by remember { mutableStateOf(false) }
    var showAppDrainersDialog by remember { mutableStateOf(false) }
    var selectedAppForDetails by remember { mutableStateOf<CleanableApp?>(null) }
    var dismissShizukuNotRunningPopup by remember { mutableStateOf(false) }
    val showShizukuNotRunningPopup = state.isShizukuInstalled && !state.shizukuAvailable && !state.rootAvailable && !dismissShizukuNotRunningPopup
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(isSearchExpanded) {
        if (isSearchExpanded) {
            try { focusRequester.requestFocus() } catch (_: Exception) {}
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp),
                modifier = Modifier.width(300.dp).fillMaxHeight()
            ) {
                Column {
                    Spacer(Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Memory,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        stringResource(R.string.ram_cleaner),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                )

                val canHibernate = (state.shizukuAvailable && state.shizukuGranted) || state.rootAvailable || state.systemPrivileged

                val menuItems = listOf(
                    Triple(Screen.CLEANUP_CONFIRM, Icons.Default.RocketLaunch, stringResource(R.string.system_cleaner)),
                    Triple(Screen.RECOMMENDED, Icons.Default.Recommend, stringResource(R.string.recommended_removals)),
                    Triple(Screen.JUNK_CLEANER, Icons.Default.Delete, stringResource(R.string.junk_cleaner)),
                    Triple(Screen.SYSTEM_TOOLS, Icons.Default.Build, stringResource(R.string.advanced_system_tools)),
                    Triple(Screen.SECURITY, Icons.Default.Security, stringResource(R.string.security)),
                    Triple(Screen.SETTINGS, Icons.Default.Settings, stringResource(R.string.settings))
                )
                val infoItems = listOf(
                    Triple(Screen.ABOUT, Icons.Default.Info, stringResource(R.string.about))
                )

                LazyColumn(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(menuItems, key = { it.first.name }) { (screen, icon, title) ->
                        DrawerMenuRow(icon, title, state.currentScreen == screen) {
                            scope.launch { drawerState.close() }
                            if (state.currentScreen != screen) viewModel.navigateTo(screen)
                        }
                    }
                    item(key = "info_divider") {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 8.dp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                        )
                    }
                    items(infoItems, key = { it.first.name }) { (screen, icon, title) ->
                        DrawerMenuRow(icon, title, state.currentScreen == screen) {
                            scope.launch { drawerState.close() }
                            if (state.currentScreen != screen) viewModel.navigateTo(screen)
                        }
                    }
                }
                }
            }
        }
    ) {
        @OptIn(ExperimentalMaterial3Api::class)
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { viewModel.refresh(manual = true) },
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {


        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isSearchExpanded) {
                    OutlinedTextField(
                        value = state.selectionSearchQuery,
                        onValueChange = viewModel::setSelectionSearchQuery,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        placeholder = { Text(stringResource(R.string.search_processes), fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary) },
                        trailingIcon = {
                            IconButton(onClick = {
                                viewModel.setSelectionSearchQuery("")
                                isSearchExpanded = false
                            }) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            cursorColor = MaterialTheme.colorScheme.primary
                        )
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { 
                                if (state.hapticFeedbackEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                scope.launch { drawerState.open() }
                            },
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Menu",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                        Text(
                            stringResource(R.string.ram_cleaner),
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onBackground,
                            lineHeight = 32.sp
                        )
                    }
                    IconButton(
                        onClick = {
                            if (state.hapticFeedbackEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            isSearchExpanded = true
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = stringResource(R.string.search_processes),
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
            
            Spacer(Modifier.height(12.dp))
            SystemStatusBadge(state)

            AnimatedVisibility(
                visible = state.lastResult != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                state.lastResult?.let { result ->
                    val localizedStrategy = when(result.strategy) {
                        "Standard" -> stringResource(R.string.standard)
                        "System" -> stringResource(R.string.system)
                        "Shizuku" -> stringResource(R.string.shizuku)
                        "Root" -> stringResource(R.string.root)
                        else -> result.strategy
                    }
                    Column {
                        Spacer(Modifier.height(16.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                        ) {
                            Row(
                                Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Verified, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = stringResource(
                                        R.string.optimization_result_banner,
                                        localizedStrategy,
                                        if (result.succeededLabels.isNotEmpty()) {
                                            if (result.succeededLabels.size > 2) {
                                                val firstTwo = result.succeededLabels.take(2).joinToString()
                                                stringResource(R.string.cleaned_others_msg, firstTwo, result.succeededLabels.size - 2)
                                            } else {
                                                stringResource(R.string.cleaned_msg, result.succeededLabels.joinToString())
                                            }
                                        } else {
                                            stringResource(R.string.processes_cleared, result.succeeded)
                                        },
                                        stringResource(R.string.ram_recovered, MemoryUtils.formatBytes(result.freedBytesEstimate))
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            val visibleDrainers = remember(state.topDrainers, state.userWhitelist) {
                state.topDrainers.filter { !state.userWhitelist.contains(it.packageName) }
            }

            val filteredApps = remember(state.apps, state.selectionSearchQuery, state.selectionFilter, state.userWhitelist) {
                state.apps.filter { app ->
                    if (state.userWhitelist.contains(app.packageName)) return@filter false
                    
                    val matchesSearch = app.label.contains(state.selectionSearchQuery, ignoreCase = true) || 
                                      app.packageName.contains(state.selectionSearchQuery, ignoreCase = true)
                    
                    val matchesFilter = when(state.selectionFilter) {
                        SelectionFilter.ALL -> true
                        SelectionFilter.RUNNING -> app.isRunning && !app.isDisabled
                        SelectionFilter.STOPPED -> app.isStopped
                        SelectionFilter.DISABLED -> app.isDisabled
                        SelectionFilter.UNINSTALL -> app.isUninstalled || app.isHidden
                    }

                    matchesSearch && matchesFilter
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 100.dp) // Leave space for Super Button
            ) {
                item {
                    state.ram?.let { 
                        RamGauge(
                            usedFraction = it.usedFraction, 
                            usedText = MemoryUtils.formatBytes(it.usedBytes),
                            availText = MemoryUtils.formatBytes(it.availBytes),
                            totalText = MemoryUtils.formatBytes(it.totalBytes)
                        ) 
                    }
                }





                if (visibleDrainers.isNotEmpty()) {
                    item {
                        Column {
                            Text(
                                text = stringResource(R.string.app_drainers).uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.app_drainers_desc),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                fontSize = 10.sp,
                                lineHeight = 14.sp
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        visibleDrainers.forEach { app ->
                            AppRow(
                                app = app,
                                isWhitelisted = state.userWhitelist.contains(app.packageName),
                                isProcessing = state.processingApps.contains(app.packageName),
                                hasElevatedAccess = state.rootAvailable || (state.shizukuAvailable && state.shizukuGranted),
                                hapticFeedbackEnabled = state.hapticFeedbackEnabled,
                                iconStyle = state.iconStyle,
                                onToggle = { viewModel.toggleApp(app.packageName) },
                                onToggleWhitelist = { viewModel.toggleWhitelist(app.packageName) },
                                onForceStop = { viewModel.forceStopApp(app.packageName) },
                                onDisable = { viewModel.disableApp(app.packageName) },
                                onEnable = { viewModel.enableApp(app.packageName) },
                                onSuspend = { viewModel.toggleAppSuspension(app.packageName, it) },
                                onClearCache = { viewModel.clearCacheApp(app.packageName) },
                                onLaunch = { viewModel.launchApp(app.packageName) },
                                onUninstall = { viewModel.uninstallApp(app.packageName) },
                                onReinstall = { viewModel.reinstallApp(app.packageName) },
                                onAppClick = { selectedAppForDetails = app }
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }

                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = stringResource(R.string.running_processes).uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        GlassCard(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.TouchApp, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = stringResource(R.string.running_processes_desc),
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.selection_label),
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = stringResource(R.string.apps_selected_count, state.apps.count { it.selected }),
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Row {
                            TextButton(
                                onClick = viewModel::toggleAllApps,
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                val anyUnselected = state.apps.any { !it.selected }
                                Text(if (anyUnselected) stringResource(R.string.select_all) else stringResource(R.string.deselect_all), fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black)
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "SYSTEM APPS ONLY",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SelectionFilter.entries.forEach { filter ->
                            val count = when(filter) {
                                SelectionFilter.ALL -> state.apps.count { !state.userWhitelist.contains(it.packageName) }
                                SelectionFilter.RUNNING -> state.apps.count { it.isRunning && !it.isDisabled && !state.userWhitelist.contains(it.packageName) }
                                SelectionFilter.STOPPED -> state.apps.count { it.isStopped && !state.userWhitelist.contains(it.packageName) }
                                SelectionFilter.DISABLED -> state.apps.count { it.isDisabled && !state.userWhitelist.contains(it.packageName) }
                                SelectionFilter.UNINSTALL -> state.apps.count { (it.isUninstalled || it.isHidden) && !state.userWhitelist.contains(it.packageName) }
                            }
                            
            FilterChip(
                selected = state.selectionFilter == filter,
                onClick = { 
                    if (state.hapticFeedbackEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.setSelectionFilter(filter) 
                },
                                label = { 
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(filter.name, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        Spacer(Modifier.width(4.dp))
                                        Surface(
                                            color = if (state.selectionFilter == filter) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                            shape = CircleShape
                                        ) {
                                            Text(
                                                text = count.toString(),
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Black
                                            )
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                )
                            )
                        }
                    }
                }



                items(items = filteredApps, key = { it.packageName }) { app ->
                    val isWhitelisted = state.userWhitelist.contains(app.packageName)
                    AppRow(
                        app = app,
                        isWhitelisted = isWhitelisted,
                        isProcessing = state.processingApps.contains(app.packageName),
                        hasElevatedAccess = state.rootAvailable || (state.shizukuAvailable && state.shizukuGranted),
                        hapticFeedbackEnabled = state.hapticFeedbackEnabled,
                        iconStyle = state.iconStyle,
                        onToggle = { viewModel.toggleApp(app.packageName) },
                        onToggleWhitelist = { viewModel.toggleWhitelist(app.packageName) },
                        onForceStop = { viewModel.forceStopApp(app.packageName) },
                        onDisable = { viewModel.disableApp(app.packageName) },
                        onEnable = { viewModel.enableApp(app.packageName) },
                        onSuspend = { viewModel.toggleAppSuspension(app.packageName, it) },
                        onSetAppOp = { op, mode -> viewModel.setAppOp(app.packageName, op, mode) },
                        onSetStandbyBucket = { viewModel.setStandbyBucket(app.packageName, it) },
                        onClearCache = { viewModel.clearCacheApp(app.packageName) },
                        onLaunch = { viewModel.launchApp(app.packageName) },
                        onUninstall = { viewModel.uninstallApp(app.packageName) },
                        onReinstall = { viewModel.reinstallApp(app.packageName) },
                        onAppClick = { selectedAppForDetails = app }
                    )
                }
            }
        }

        GlassmorphismTaskBar(
            state = state,
            viewModel = viewModel,
            onOpenAppDrainers = { showAppDrainersDialog = true },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
        )
    }
    }
    }

    if (showAppDrainersDialog) {
        AppDrainersDialog(
            state = state,
            viewModel = viewModel,
            onDismiss = { showAppDrainersDialog = false }
        )
    }

    if (showShizukuNotRunningPopup) {
        ShizukuNotRunningPopupDialog(
            onStartShizuku = {
                viewModel.openShizukuApp()
            },
            onDismiss = {
                dismissShizukuNotRunningPopup = true
            }
        )
    }

    selectedAppForDetails?.let { app ->
        AppDetailDialog(
            app = app,
            state = state,
            viewModel = viewModel,
            onDismiss = { selectedAppForDetails = null }
        )
    }
}


@Composable
private fun PermissionHubScreen(state: CleanerUiState, viewModel: CleanerViewModel, onBack: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { 
                    if (state.hapticFeedbackEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onBack() 
                },
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                    .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "SETTINGS > PERMISSIONS", 
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
                Text(
                    stringResource(R.string.technical_audit), 
                    style = MaterialTheme.typography.titleMedium, 
                    fontWeight = FontWeight.Black, 
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            
            var showAdbDialog by remember { mutableStateOf(false) }
            IconButton(
                onClick = { showAdbDialog = true },
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
            ) {
                Icon(Icons.Default.Terminal, null, tint = MaterialTheme.colorScheme.primary)
            }
            
            if (showAdbDialog) {
                AdbHelpDialog(state, viewModel) { showAdbDialog = false }
            }
        }

        Spacer(Modifier.height(24.dp))

        if ((!state.shizukuGranted && !state.rootAvailable) || state.isApplyingAccess) {
            PrivilegeAccessCard(
                state = state,
                onAction = {
                    if (state.hapticFeedbackEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.requestShizukuAccess()
                }
            )
            Spacer(Modifier.height(16.dp))
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            item {
                Text(stringResource(R.string.essential_privileges), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                Spacer(Modifier.height(8.dp))
            }
            
            items(state.essentialPermissions, key = { it.id }) { perm ->
                PermissionHubRow(perm)
            }
        }

        if (state.shizukuGranted || state.rootAvailable) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
            ) {
                Button(
                    onClick = { 
                        if (state.hapticFeedbackEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.grantAllPermissions() 
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Bolt, null, tint = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.one_tap_auto_grant), fontWeight = FontWeight.Black, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@Composable
private fun PermissionHubRow(perm: PermissionStatus) {
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(perm.name, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                Text(perm.id, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), fontFamily = FontFamily.Monospace)
            }
            
            Surface(
                color = (if (perm.isGranted) Color(0xFF00E5A0) else Danger).copy(alpha = 0.1f),
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, (if (perm.isGranted) Color(0xFF00E5A0) else Danger).copy(alpha = 0.2f))
            ) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(if (perm.isGranted) Color(0xFF00E5A0) else Danger))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (perm.isGranted) stringResource(R.string.granted_label) else stringResource(R.string.missing_label), 
                        fontSize = 10.sp, 
                        fontWeight = FontWeight.Black, 
                        color = if (perm.isGranted) Color(0xFF00E5A0) else Danger,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun IconStyleChip(id: String, label: String, selected: Boolean, onClick: () -> Unit) {
    val accent = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    val shape = when(id) {
        "SHARP" -> RoundedCornerShape(0.dp)
        "CYBER" -> RoundedCornerShape(topStart = 12.dp, bottomEnd = 12.dp)
        else -> RoundedCornerShape(12.dp)
    }
    Surface(
        onClick = onClick,
        shape = shape,
        color = if (selected) accent.copy(alpha = 0.1f) else Color.Transparent,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) accent else Color.White.copy(alpha = 0.1f)),
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Text(label, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = accent)
    }
}

@Composable
private fun DeveloperToggleRow(title: String, enabled: Boolean, onToggle: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(stringResource(R.string.debug_desc), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        }
        Switch(
            checked = enabled,
            onCheckedChange = { onToggle() },
            modifier = Modifier.scale(0.75f),
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.primary,
                checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            )
        )
    }
}



@Composable
private fun SettingsCategory(title: String) {
    Text(
        title.uppercase(),
        color = MaterialTheme.colorScheme.primary,
        fontSize = 10.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Black,
        letterSpacing = 2.sp,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
    )
}

@Composable
private fun SettingsItem(title: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
    }
}

@Composable
private fun SystemStatusBadge(state: CleanerUiState) {
    val (text, color) = when {
        state.ram == null -> stringResource(R.string.calibrating_system) to MaterialTheme.colorScheme.onSurfaceVariant
        state.ram.usedFraction > 0.85f -> stringResource(R.string.critical_performance) to Danger
        state.ram.usedFraction > 0.70f -> stringResource(R.string.heavy_system_load) to Color(0xFFFFA500)
        else -> return
    }
    Surface(
        color = color.copy(alpha = 0.08f),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.15f))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(2.dp, color.copy(alpha = 0.3f), CircleShape)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = text,
                color = color,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.5.sp
            )
        }
    }
}


@Composable
private fun AutoKillCard(state: CleanerUiState, viewModel: CleanerViewModel) {
    val haptic = LocalHapticFeedback.current
    GlassCard(
        onClick = { 
            if (state.hapticFeedbackEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            viewModel.navigateTo(Screen.SHIELD) 
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        stringResource(R.string.auto_kill_protection),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Black,
                        fontSize = 11.sp,
                        letterSpacing = 1.sp
                    )
                    Text(
                        if (state.autoKillBackgroundEnabled) "ENABLED" else "DISABLED",
                        color = if (state.autoKillBackgroundEnabled) MaterialTheme.colorScheme.primary else Color.Gray,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
            Switch(
                checked = state.autoKillBackgroundEnabled,
                onCheckedChange = { 
                    if (state.hapticFeedbackEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.toggleAutoKillBackground() 
                },
                modifier = Modifier.scale(0.75f),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                )
            )
        }
        
        Spacer(Modifier.height(16.dp))
        
        Text(
            stringResource(R.string.auto_kill_desc),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            fontSize = 11.sp,
            lineHeight = 15.sp
        )
        
        Spacer(Modifier.height(16.dp))
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                shape = CircleShape
            ) {
                Text(
                    text = stringResource(R.string.apps_protected_count, state.userWhitelist.size),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        }
    }
}



@Composable
private fun SecurityCard(state: CleanerUiState, viewModel: CleanerViewModel) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = { viewModel.navigateTo(Screen.SECURITY) }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Shield, contentDescription = null, tint = if (state.threats.isNotEmpty()) Danger else MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.security).uppercase(), 
                fontSize = 11.sp, 
                fontWeight = FontWeight.Black, 
                color = MaterialTheme.colorScheme.onSurfaceVariant, 
                letterSpacing = 1.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.height(12.dp))
        if (state.isScanningViruses) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape), color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.scanning), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, fontWeight = FontWeight.Black)
        } else {
            val count = state.threats.size
            Text(if (count == 0) stringResource(R.string.secure) else stringResource(R.string.threat_count_detected, count), fontWeight = FontWeight.Black, fontSize = 20.sp, color = if (count > 0) Danger else MaterialTheme.colorScheme.onSurface)
            Text(stringResource(R.string.threat_scan), color = if (count > 0) Danger else MaterialTheme.colorScheme.primary, fontSize = 10.sp, fontWeight = FontWeight.Black)
        }
    }
}



@Composable
private fun RamGauge(usedFraction: Float, usedText: String, availText: String, totalText: String) {
    val animatedProgress by animateFloatAsState(
        targetValue = usedFraction,
        animationSpec = tween(1500, easing = FastOutSlowInEasing),
        label = "ram_progress"
    )
    
    val statusColor = when {
        usedFraction > 0.85f -> Danger
        usedFraction > 0.7f -> Warning
        else -> Color(0xFF4CAF50)
    }

    GlassCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // STATUS Pill
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "STATUS: ",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Surface(
                        shape = CircleShape,
                        color = statusColor.copy(alpha = 0.1f),
                        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.4f)),
                        modifier = Modifier.shadow(8.dp, CircleShape, spotColor = statusColor)
                    ) {
                        Text(
                            text = when {
                                usedFraction > 0.85f -> "CRITICAL"
                                usedFraction > 0.7f -> "STRESSED"
                                else -> "OPTIMIZED"
                            },
                            color = statusColor,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                // USED
                RamDataPoint(label = "USED:", value = usedText)
                // FREE
                RamDataPoint(label = "FREE:", value = availText)
                
                // TOTAL
                Column {
                    Text(
                        text = "TOTAL: $totalText",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "Physical RAM",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Box(
                modifier = Modifier.size(140.dp),
                contentAlignment = Alignment.Center
            ) {
                val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                val ghostTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f)
                val ghostLoadColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
                
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidth = 14.dp.toPx()
                    val ghostStrokeWidth = 3.dp.toPx()
                    val center = size.center
                    
                    // Main track
                    drawCircle(
                        color = trackColor,
                        style = Stroke(width = strokeWidth)
                    )

                    // Main Progress Glow & Arc
                    drawArc(
                        brush = Brush.sweepGradient(
                            0f to statusColor.copy(alpha = 0.3f),
                            0.5f to statusColor,
                            1f to statusColor.copy(alpha = 0.3f)
                        ),
                        startAngle = -90f,
                        sweepAngle = 360f * animatedProgress,
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )

                    // Ghost ring
                    val ghostRadius = (size.minDimension / 2) - strokeWidth - 8.dp.toPx()
                    drawCircle(
                        color = ghostTrackColor,
                        radius = ghostRadius,
                        style = Stroke(width = ghostStrokeWidth)
                    )
                    
                    // Inner load representation
                    drawArc(
                        color = ghostLoadColor,
                        startAngle = -90f,
                        sweepAngle = 360f * (animatedProgress * 0.85f),
                        useCenter = false,
                        style = Stroke(width = ghostStrokeWidth, cap = StrokeCap.Round),
                        topLeft = Offset(center.x - ghostRadius, center.y - ghostRadius),
                        size = Size(ghostRadius * 2, ghostRadius * 2)
                    )
                }

                // Center text structure with standard size attributes
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${(usedFraction * 100).toInt()}%",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "USED",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun RamDataPoint(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(45.dp)
        )
        Text(
            text = value,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 16.sp,
            fontWeight = FontWeight.Black
        )
    }
}


@Composable
private fun PerformanceModeCard(viewModel: CleanerViewModel) {
    val perfColor = Color(0xFFFF4500)
    GlassCard(
        onClick = viewModel::launchPerformanceMode,
        modifier = Modifier.fillMaxWidth(),
        baseColor = perfColor
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(perfColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.FlashOn, contentDescription = null, tint = perfColor, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.performance_mode),
                    color = perfColor,
                    fontWeight = FontWeight.Black,
                    fontSize = 11.sp,
                    letterSpacing = 1.sp
                )
                Text(
                    stringResource(R.string.unlock_hardware_desc),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = perfColor.copy(alpha = 0.5f))
        }
    }
}




@Composable
private fun AutoCleanSettings(state: CleanerUiState, viewModel: CleanerViewModel) {
    val haptic = LocalHapticFeedback.current
    val triggerHaptic = {
        if (state.hapticFeedbackEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SettingsSwitchRow(
            title = stringResource(R.string.clean_on_boot),
            subtitle = stringResource(R.string.optimize_on_startup),
            icon = Icons.Default.PowerSettingsNew,
            checked = state.cleanOnBootEnabled,
            onCheckedChange = {
                triggerHaptic()
                viewModel.toggleCleanOnBoot()
            }
        )

        HorizontalDivider(color = Color.White.copy(alpha = 0.05f))

        SettingsSwitchRow(
            title = stringResource(R.string.auto_clean_background),
            subtitle = "Purge memory automatically when RAM exceeds threshold",
            icon = Icons.Default.Memory,
            checked = state.autoCleanEnabled,
            onCheckedChange = {
                triggerHaptic()
                viewModel.toggleAutoClean()
            }
        )

        if (state.autoCleanEnabled) {
            Spacer(Modifier.height(2.dp))
            PrecisionSliderRow(
                title = stringResource(R.string.ram_threshold),
                valueText = "${state.autoCleanThreshold}% RAM",
                value = state.autoCleanThreshold.toFloat(),
                valueRange = 50f..95f,
                steps = 8,
                minLabel = "50%",
                maxLabel = "95%",
                onValueChange = { viewModel.setAutoCleanThreshold(it.toInt()) }
            )
            Spacer(Modifier.height(2.dp))
        }

        HorizontalDivider(color = Color.White.copy(alpha = 0.05f))

        SettingsSwitchRow(
            title = stringResource(R.string.auto_clean_result),
            subtitle = stringResource(R.string.auto_clean_result_desc),
            icon = Icons.Default.NotificationsActive,
            checked = state.autoCleanNotificationsEnabled,
            onCheckedChange = {
                triggerHaptic()
                viewModel.toggleAutoCleanNotifications()
            }
        )

        HorizontalDivider(color = Color.White.copy(alpha = 0.05f))

        SettingsSwitchRow(
            title = stringResource(R.string.auto_kill_background),
            subtitle = stringResource(R.string.auto_kill_background_desc),
            icon = Icons.Default.Timer,
            checked = state.autoKillBackgroundEnabled,
            onCheckedChange = {
                triggerHaptic()
                viewModel.toggleAutoKillBackground()
            }
        )

        HorizontalDivider(color = Color.White.copy(alpha = 0.05f))

        SettingsSwitchRow(
            title = stringResource(R.string.auto_kill_inactive),
            subtitle = stringResource(R.string.auto_kill_inactive_desc),
            icon = Icons.Default.History,
            checked = state.autoKillInactiveEnabled,
            onCheckedChange = {
                triggerHaptic()
                viewModel.toggleAutoKillInactive()
            }
        )
    }
}





@Composable
private fun ThemePicker(
    state: CleanerUiState,
    currentTheme: AppTheme,
    onSelect: (AppTheme) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ThemeChip(
            state = state,
            label = stringResource(R.string.theme_light),
            selected = currentTheme == AppTheme.LIGHT,
            previewBg = Color(0xFFF8F9FA),
            previewSurface = Color.White
        ) { onSelect(AppTheme.LIGHT) }
        
        ThemeChip(
            state = state,
            label = stringResource(R.string.theme_dark),
            selected = currentTheme == AppTheme.DARK,
            previewBg = Color(0xFF0B0E14),
            previewSurface = Color(0xFF171B22)
        ) { onSelect(AppTheme.DARK) }
        
        ThemeChip(
            state = state,
            label = stringResource(R.string.theme_black),
            selected = currentTheme == AppTheme.BLACK,
            previewBg = Color.Black,
            previewSurface = Color(0xFF0D1017)
        ) { onSelect(AppTheme.BLACK) }
        
        ThemeChip(
            state = state,
            label = stringResource(R.string.theme_system),
            selected = currentTheme == AppTheme.SYSTEM,
            previewBg = Color.DarkGray,
            previewSurface = Color.Gray
        ) { onSelect(AppTheme.SYSTEM) }
    }
}


@Composable
private fun ThemeChip(
    state: CleanerUiState,
    label: String,
    selected: Boolean,
    previewBg: Color,
    previewSurface: Color,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val accent = Color(state.primaryColor)
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { 
            if (state.hapticFeedbackEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick() 
        }
    ) {
        // Mini Mockup Preview
        Box(
            modifier = Modifier
                .size(width = 60.dp, height = 80.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(previewBg)
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) accent else Color.White.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(4.dp)
        ) {
            Column {
                // Header
                Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(previewSurface))
                Spacer(Modifier.height(4.dp))
                // Card 1
                Box(Modifier.fillMaxWidth().height(20.dp).clip(RoundedCornerShape(4.dp)).background(previewSurface)) {
                    Box(Modifier.padding(4.dp).size(8.dp).clip(CircleShape).background(accent))
                }
                Spacer(Modifier.height(4.dp))
                // Card 2
                Box(Modifier.fillMaxWidth().height(20.dp).clip(RoundedCornerShape(4.dp)).background(previewSurface))
                Spacer(Modifier.height(8.dp))
                // Button
                Box(Modifier.align(Alignment.CenterHorizontally).size(width = 30.dp, height = 8.dp).clip(CircleShape).background(accent))
            }
            
            if (selected) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(accent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(8.dp))
                }
            }
        }
        
        Spacer(Modifier.height(8.dp))
        Text(
            label, 
            fontSize = 10.sp, 
            fontWeight = if (selected) FontWeight.Black else FontWeight.Bold,
            color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PrivilegeAccessCard(state: CleanerUiState, onAction: () -> Unit) {
    // What the card shows is derived from live state, so it updates by itself the moment the
    // Shizuku service starts or the user grants access - nothing to refresh manually.
    val title: Int
    val desc: Int
    var button: Int? = null
    val icon: ImageVector
    when {
        state.isApplyingAccess -> {
            title = R.string.access_applying_title; desc = R.string.access_applying_desc; icon = Icons.Default.Sync
        }
        state.rootAvailable -> {
            title = R.string.access_root_title; desc = R.string.access_root_desc; icon = Icons.Default.CheckCircle
        }
        state.shizukuGranted -> {
            title = R.string.access_ready_title; desc = R.string.access_ready_desc; icon = Icons.Default.CheckCircle
        }
        state.shizukuAvailable -> {
            title = R.string.access_grant_title; desc = R.string.access_grant_desc
            button = R.string.access_grant_button; icon = Icons.Rounded.Terminal
        }
        state.isShizukuInstalled -> {
            title = R.string.access_start_title; desc = R.string.access_start_desc
            button = R.string.access_start_button; icon = Icons.Rounded.Terminal
        }
        else -> {
            title = R.string.access_install_title; desc = R.string.access_install_desc
            button = R.string.access_install_button; icon = Icons.Rounded.Terminal
        }
    }
    val active = state.rootAvailable || state.shizukuGranted
    val accent = if (active || state.isApplyingAccess) Color(0xFF00E5A0) else MaterialTheme.colorScheme.primary

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = accent, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(title), fontWeight = FontWeight.Black, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(2.dp))
                Text(stringResource(desc), fontSize = 11.sp, lineHeight = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (state.isApplyingAccess) {
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)), color = accent)
        } else if (button != null) {
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onAction,
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
            ) {
                Text(stringResource(button), fontWeight = FontWeight.Black, fontSize = 13.sp, letterSpacing = 0.5.sp)
            }
        }
    }
}


@Composable
private fun ModeChip(
    state: CleanerUiState,
    label: String,
    status: String,
    selected: Boolean,
    enabled: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    
    val infiniteTransition = rememberInfiniteTransition(label = "breathing")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.1f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(tween(2000), RepeatMode.Reverse),
        label = "glow"
    )

    Surface(
        onClick = { 
            if (state.hapticFeedbackEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick() 
        },
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.3f),
        modifier = Modifier
            .widthIn(min = 120.dp)
            .then(
                if (selected) Modifier.border(
                    2.dp, 
                    MaterialTheme.colorScheme.primary.copy(alpha = glowAlpha), 
                    RoundedCornerShape(16.dp)
                ) else Modifier.border(
                    1.dp,
                    Color.White.copy(alpha = 0.05f),
                    RoundedCornerShape(16.dp)
                )
            )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon, 
                contentDescription = null, 
                modifier = Modifier.size(24.dp),
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Text(
                label, 
                fontWeight = FontWeight.Bold, 
                fontSize = 12.sp,
                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (enabled) Color(0xFF00E5A0) else Color.Gray)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    status, 
                    fontSize = 9.sp, 
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Suppress("unused")
@Composable
private fun UsageAccessPrompt(onRequest: () -> Unit) {
    val viewModel: CleanerViewModel = viewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current
    GlassCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.LockOpen, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.height(20.dp))
            Text(
                stringResource(R.string.usage_access_required).uppercase(),
                fontWeight = FontWeight.Black,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.sp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "To build an accurate list of resident processes, ${stringResource(R.string.app_name)} requires usage statistics access.",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                lineHeight = 18.sp
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { 
                    if (state.hapticFeedbackEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onRequest() 
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onSurface)
            ) {
                Text(stringResource(R.string.grant_permission), fontWeight = FontWeight.Black)
            }
        }
    }
}


@Composable
private fun getIconShape(style: String): Shape {
    return when (style) {
        "SHARP" -> RoundedCornerShape(0.dp)
        "CYBER" -> RoundedCornerShape(topStart = 14.dp, bottomEnd = 14.dp)
        else -> RoundedCornerShape(14.dp)
    }
}

@Composable
private fun AppRow(
    app: CleanableApp, 
    isWhitelisted: Boolean, 
    isProcessing: Boolean = false,
    hasElevatedAccess: Boolean = true,
    hapticFeedbackEnabled: Boolean = true,
    iconStyle: String = "ROUNDED",
    onToggle: () -> Unit, 
    onToggleWhitelist: () -> Unit,
    onForceStop: () -> Unit = {},
    onDisable: () -> Unit = {},
    onEnable: () -> Unit = {},
    onSuspend: (Boolean) -> Unit = {},
    onSetAppOp: (Int, Int) -> Unit = { _, _ -> },
    onSetStandbyBucket: (Int) -> Unit = {},
    onClearCache: () -> Unit = {},
    onLaunch: () -> Unit = {},
    onUninstall: () -> Unit = {},
    onReinstall: () -> Unit = {},
    onAppClick: () -> Unit = {}
) {
    val haptic = LocalHapticFeedback.current
    var showMenu by remember { mutableStateOf(value = false) }
    var showUninstallConfirm by remember { mutableStateOf(value = false) }

    if (showUninstallConfirm) {
        AlertDialog(
            onDismissRequest = { showUninstallConfirm = false },
            title = { Text(stringResource(R.string.uninstall_system_app)) },
            text = { Text(stringResource(R.string.uninstall_confirm_desc, app.label)) },
            confirmButton = {
                TextButton(onClick = { 
                    if (hapticFeedbackEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onUninstall()
                    showUninstallConfirm = false 
                }) {
                    Text(stringResource(R.string.uninstall), color = Danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    if (hapticFeedbackEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    showUninstallConfirm = false 
                }) {
                    Text(stringResource(R.string.cancel))
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = if (isProcessing) null else onAppClick
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val bitmap = rememberAppIcon(app.packageName, app.icon)
            val iconShape = getIconShape(iconStyle)
            Box(contentAlignment = Alignment.BottomEnd) {
                if (isProcessing) {
                    Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                    }
                } else if (bitmap != null) {
                    Image(
                        bitmap, 
                        contentDescription = null, 
                        modifier = Modifier
                            .size(46.dp)
                            .clip(iconShape)
                    )
                } else {
                    Box(Modifier.size(46.dp).clip(iconShape).background(MaterialTheme.colorScheme.surface))
                }
                
                if (!isProcessing) {
                    Row(
                        modifier = Modifier.offset(x = 6.dp, y = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy((-6).dp)
                    ) {
                        if (app.isDisabled) {
                            BadgeIcon(Icons.Default.Block, Color.Gray)
                        }
                        if (app.isHidden) {
                            BadgeIcon(Icons.Default.VisibilityOff, Color.LightGray)
                        }
                        if (app.isSuspended || app.isFrozen) {
                            BadgeIcon(Icons.Rounded.AcUnit, Color(0xFF00BFFF))
                        }
                        if (app.isStopped) {
                            BadgeIcon(Icons.Default.StopCircle, Color(0xFFFF4500))
                        }
                        if (app.isBackgroundRestricted) {
                            BadgeIcon(Icons.Default.Security, Color(0xFFFFD700))
                        }
                    }
                }
            }
            
            Spacer(Modifier.width(LocalAppSpacing.current.xLarge))
            Column(Modifier.weight(1f)) {
                Text(
                    text = app.label, 
                    fontSize = 15.sp, 
                    fontWeight = FontWeight.Bold, 
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(LocalAppSpacing.current.small))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (app.isSystem) {
                        Text(
                            text = stringResource(R.string.core_label), 
                            color = MaterialTheme.colorScheme.primary, 
                            fontSize = 7.sp, 
                            fontWeight = FontWeight.Black,
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp),
                            letterSpacing = 0.5.sp
                        )
                    }

                    // 64-bit / 32-bit Architecture Badge
                    val bitnessText = if (app.is64Bit) stringResource(R.string.arch_64bit) else stringResource(R.string.arch_32bit)
                    val bitnessColor = if (app.is64Bit) Color(0xFF00E5FF) else Color(0xFFB388FF)
                    Text(
                        text = bitnessText,
                        color = bitnessColor,
                        fontSize = 7.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(bitnessColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                        letterSpacing = 0.5.sp
                    )

                    // Safe to Disable / Caution / Danger Badge
                    val (safetyText, safetyColor) = when (app.disableSafetyLevel) {
                        DisableSafetyLevel.SAFE -> Pair(stringResource(R.string.safe_to_disable), Color(0xFF00E676))
                        DisableSafetyLevel.CAUTION -> Pair(stringResource(R.string.caution_system_app), Color(0xFFFFB300))
                        DisableSafetyLevel.DANGER -> Pair(stringResource(R.string.danger_to_disable), Color(0xFFFF1744))
                    }
                    Text(
                        text = safetyText,
                        color = safetyColor,
                        fontSize = 7.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(safetyColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                        letterSpacing = 0.5.sp
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = app.packageName, 
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), 
                    fontSize = 9.sp, 
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
            
            Box(modifier = Modifier) {
                IconButton(
                    onClick = { 
                        if (hapticFeedbackEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onToggleWhitelist() 
                    },
                    enabled = !isProcessing,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (isWhitelisted) Icons.Filled.Shield else Icons.Outlined.Shield,
                        contentDescription = "Protect",
                        tint = if (isWhitelisted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Box(modifier = Modifier) {
                IconButton(
                    onClick = { 
                        if (hapticFeedbackEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showMenu = true 
                    },
                    enabled = !isProcessing,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Manage",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(18.dp)
                    )
                }
                
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.launch_app), color = MaterialTheme.colorScheme.onSurface) },
                        onClick = { 
                            if (hapticFeedbackEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onLaunch()
                            showMenu = false 
                        },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.Launch, null, tint = MaterialTheme.colorScheme.primary) }
                    )
                    DropdownMenuItem(
                        text = { 
                            Column {
                                Text(
                                    stringResource(R.string.force_stop), 
                                    color = if (isWhitelisted) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f) else MaterialTheme.colorScheme.onSurface
                                )
                                if (isWhitelisted) {
                                    Text(stringResource(R.string.shielded_protection), fontSize = 9.sp, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        },
                        enabled = !isWhitelisted && !isProcessing,
                        onClick = { 
                            if (hapticFeedbackEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onForceStop()
                            showMenu = false 
                        },
                        leadingIcon = { Icon(Icons.Default.StopCircle, null, tint = if (isWhitelisted) Color(0xFFFF4500).copy(alpha = 0.4f) else Color(0xFFFF4500)) }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.clear_cache), color = MaterialTheme.colorScheme.onSurface) },
                        onClick = { 
                            if (hapticFeedbackEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onClearCache()
                            showMenu = false 
                        },
                        leadingIcon = { Icon(Icons.Default.DeleteSweep, null, tint = MaterialTheme.colorScheme.primary) }
                    )
                    
                    // 🔴 USER APPS & MIUI FACTORY APPS: DISABLE/ENABLE & SUSPEND/UNSUSPEND
                    if (!app.isSystem) {
                        DropdownMenuItem(
                            text = { 
                                Column {
                                    val label = if (app.isDisabled || app.isHidden || app.isUninstalled) stringResource(R.string.enable_app) else stringResource(R.string.disable_app)
                                    Text(label, color = if (hasElevatedAccess) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                                    if (!hasElevatedAccess) {
                                        Text(stringResource(R.string.requires_root_hibernate), fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                                    }
                                }
                            },
                            enabled = hasElevatedAccess && !isProcessing,
                            onClick = { 
                                if (hapticFeedbackEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (app.isDisabled || app.isHidden || app.isUninstalled) onEnable() else onDisable()
                                showMenu = false 
                            },
                            leadingIcon = { Icon(Icons.Default.Block, null, tint = if (hasElevatedAccess) Color.Gray else Color.Gray.copy(alpha = 0.4f)) }
                        )
                        
                        DropdownMenuItem(
                            text = { 
                                Column {
                                    val isSuspended = app.isSuspended
                                    Text(if (isSuspended) stringResource(R.string.unsuspend_app) else stringResource(R.string.suspend_app), 
                                         color = if (hasElevatedAccess) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                                    if (!hasElevatedAccess) {
                                        Text(stringResource(R.string.requires_root_hibernate), fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                                    }
                                }
                            },
                            enabled = hasElevatedAccess && !isProcessing,
                            onClick = { 
                                if (hapticFeedbackEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onSuspend(!app.isSuspended)
                                showMenu = false 
                            },
                            leadingIcon = { Icon(Icons.Rounded.AcUnit, null, tint = if (hasElevatedAccess) Color(0xFF00BFFF) else Color(0xFF00BFFF).copy(alpha = 0.4f)) }
                        )
                    }
                    
                    // 🔴 FIXED: BACKGROUND RUN - Properly determine action based on state
                    DropdownMenuItem(
                        text = { 
                            Column {
                                Text(if (app.isBackgroundRestricted) stringResource(R.string.allow_background_run) else stringResource(R.string.ignore_background_run), 
                                     color = if (hasElevatedAccess) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                                if (!hasElevatedAccess) {
                                    Text(stringResource(R.string.requires_root_hibernate), fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                                }
                            }
                        },
                        enabled = hasElevatedAccess && !isProcessing,
                        onClick = { 
                            if (hapticFeedbackEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            // If restricted, allow (0). If not restricted, ignore (1)
                            onSetAppOp(63, if (app.isBackgroundRestricted) 0 else 1) 
                            showMenu = false 
                        },
                        leadingIcon = { Icon(Icons.Default.Security, null, tint = if (hasElevatedAccess) Color.Yellow else Color.Yellow.copy(alpha = 0.4f)) }
                    )

                    DropdownMenuItem(
                        text = { 
                            Column {
                                Text(stringResource(R.string.limit_background_life), color = if (hasElevatedAccess) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                                if (!hasElevatedAccess) {
                                    Text(stringResource(R.string.requires_root_hibernate), fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                                }
                            }
                        },
                        enabled = hasElevatedAccess && !isProcessing,
                        onClick = { 
                            if (hapticFeedbackEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSetStandbyBucket(if (android.os.Build.VERSION.SDK_INT >= 30) 45 else 40)
                            showMenu = false 
                        },
                        leadingIcon = { Icon(Icons.Default.HourglassEmpty, null, tint = if (hasElevatedAccess) Color(0xFFFFA500) else Color(0xFFFFA500).copy(alpha = 0.4f)) }
                    )

                    DropdownMenuItem(
                        text = { Text(if (isWhitelisted) stringResource(R.string.unshield) else stringResource(R.string.shield_whitelist), color = MaterialTheme.colorScheme.onSurface) },
                        onClick = { 
                            if (hapticFeedbackEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onToggleWhitelist()
                            showMenu = false 
                        },
                        leadingIcon = { Icon(if (isWhitelisted) Icons.Filled.Shield else Icons.Outlined.Shield, null, tint = MaterialTheme.colorScheme.primary) }
                    )
                    
                    // 🔴 UNINSTALL / REINSTALL LOGIC FOR ALL APPS (System, MIUI, User)
                    if (app.isUninstalled || app.isHidden) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.reinstall_bloatware), color = MaterialTheme.colorScheme.primary) },
                            enabled = hasElevatedAccess && !isProcessing,
                            onClick = { 
                                if (hapticFeedbackEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onReinstall()
                                showMenu = false 
                            },
                            leadingIcon = { Icon(Icons.Default.SettingsBackupRestore, null, tint = MaterialTheme.colorScheme.primary) }
                        )
                    } else {
                        DropdownMenuItem(
                            text = { 
                                val label = if (app.isSystem) stringResource(R.string.uninstall_bloatware) else stringResource(R.string.uninstall)
                                Text(label, color = Danger) 
                            },
                            enabled = if (app.isSystem) hasElevatedAccess && !isProcessing else true,
                            onClick = { 
                                if (hapticFeedbackEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                showUninstallConfirm = true
                                showMenu = false 
                            },
                            leadingIcon = { Icon(Icons.Default.DeleteForever, null, tint = Danger) }
                        )
                    }
                }
            }

            Spacer(Modifier.width(LocalAppSpacing.current.medium))

            Checkbox(
                checked = app.selected, 
                onCheckedChange = { 
                    if (hapticFeedbackEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggle() 
                },
                enabled = !isProcessing && !isWhitelisted && !app.isDisabled,
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary,
                    uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                    checkmarkColor = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.scale(0.75f)
            )
        }
    }
}


@Composable
private fun BadgeIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color) {
    Box(
        modifier = Modifier
            .size(16.dp)
            .clip(CircleShape)
            .background(color)
            .border(2.dp, MaterialTheme.colorScheme.background, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(10.dp))
    }
}

@Composable
private fun JunkCleanerScreen(state: CleanerUiState, viewModel: CleanerViewModel, onBack: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { 
                    if (state.hapticFeedbackEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onBack() 
                },
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back), tint = MaterialTheme.colorScheme.onSurface)
            }
            Spacer(Modifier.width(16.dp))
            Text(stringResource(R.string.junk_cleaner), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onBackground)
        }

        Spacer(Modifier.height(32.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = LocalCardColor.current.copy(alpha = 0.5f),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    stringResource(R.string.total_junk_detected),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                )
                Text(
                    text = MemoryUtils.formatBytes(state.totalJunkSize),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.junk_clean_desc),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        if (!state.hasAllFilesAccess) {
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.permission_required), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(stringResource(R.string.junk_deep_scan_desc), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                    }
                    TextButton(onClick = viewModel::requestAllFilesAccess) {
                        Text(stringResource(R.string.grant), fontWeight = FontWeight.Black)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // 🟡 FIXED: Enhanced scanning progress indicator
        if (state.isScanningJunk) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 32.dp)) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.searching_junk), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = state.cleaningPackage ?: stringResource(R.string.searching),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    // Progress bar for scanning
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surface
                    )
                }
            }
        } else if (state.junkItems.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.DoneAll, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.storage_clean), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(items = state.junkItems, key = { it.path }) { item ->
                    GlassCard(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val icon = when(item.type) {
                                JunkType.CACHE -> Icons.Default.Cached
                                JunkType.TEMP -> Icons.Default.Timer
                                JunkType.LOG -> Icons.Default.Description
                                JunkType.EMPTY_FOLDER -> Icons.Default.FolderOpen
                                JunkType.APK -> Icons.Default.Android
                                JunkType.SYSTEM_CACHE -> Icons.Default.SettingsSuggest
                                JunkType.LARGE_FILE -> Icons.AutoMirrored.Filled.InsertDriveFile
                                else -> Icons.Default.Delete
                            }
                            Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(LocalAppSpacing.current.xLarge))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = if (item.type == JunkType.SYSTEM_CACHE) item.category else item.path.split("/").last(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                                Text(text = if (item.type == JunkType.SYSTEM_CACHE) item.path else item.category, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                            Text(text = MemoryUtils.formatBytes(item.size), fontWeight = FontWeight.Black, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = { viewModel.cleanJunk() },
            enabled = state.junkItems.isNotEmpty() && !state.isCleaning,
            modifier = Modifier.fillMaxWidth().height(60.dp),
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onSurface)
        ) {
            if (state.isCleaning) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onSurface, strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
            }
            Spacer(Modifier.width(8.dp))
            Text(if (state.isCleaning) stringResource(R.string.purging) else stringResource(R.string.purge_junk), fontWeight = FontWeight.Black, fontSize = 16.sp)
        }

        if (state.rootAvailable) {
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = { viewModel.optimizeVm() },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
            ) {
                Icon(Icons.Default.FlashOn, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.fstrim), color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Black)
            }
        }
        
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun CleanupConfirmScreen(
    state: CleanerUiState,
    viewModel: CleanerViewModel,
    onBack: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
            }
            Text(stringResource(R.string.cleanup_confirm_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(16.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Row(
                Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(stringResource(R.string.optimization_strength), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                    Text(state.mode.name + " Mode", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        
        Text(stringResource(R.string.optimization_tasks), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OptimizationTaskItem(
                icon = Icons.Default.DeleteSweep,
                title = stringResource(R.string.cache_purge),
                description = stringResource(R.string.cache_purge_desc),
                mode = state.mode
            )
            OptimizationTaskItem(
                icon = Icons.Rounded.Memory,
                title = stringResource(R.string.ram_refresh),
                description = stringResource(R.string.ram_refresh_desc),
                mode = state.mode
            )
            if (state.mode == CleanMode.ROOT) {
                OptimizationTaskItem(
                    icon = Icons.Default.SettingsSuggest,
                    title = stringResource(R.string.kernel_optimization),
                    description = stringResource(R.string.kernel_optimization_desc),
                    mode = state.mode
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        
        Text(
            stringResource(R.string.apps_optimized_count, state.apps.count { it.selected }),
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(Modifier.weight(1f))

        Button(
            onClick = {
                viewModel.cleanSelected()
                viewModel.navigateTo(Screen.MAIN)
            },
            enabled = !state.isCleaning && state.apps.any { it.selected },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Icon(
                Icons.Default.Bolt,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.purge_caches),
                fontWeight = FontWeight.ExtraBold,
                fontSize = 16.sp
            )
        }
    }
}


@Composable
private fun CleaningOverlay(state: CleanerUiState) {
    val cleaningPackage = state.cleaningPackage
    val currentApp = remember(cleaningPackage, state.apps) {
        if (cleaningPackage != null) state.apps.find { it.packageName == cleaningPackage } else null
    }

    val isHibernating = state.mode == CleanMode.SHIZUKU || state.mode == CleanMode.ROOT || state.mode == CleanMode.SYSTEM
    val isCooling = state.isCooling

    val primaryColor = if (isCooling) IceBlue else if (isHibernating) Color(0xFFBB86FC) else MaterialTheme.colorScheme.primary
    val secondaryColor = Color(0xFF6200EE) // Purple
    val deepBlack = Color(0xFF010203) // Deeper than TrueDark for "Deep Black"

    val infiniteTransition = rememberInfiniteTransition(label = "cryo")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(8000, easing = LinearEasing)),
        label = "rotation"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(deepBlack)
    ) {


        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(60.dp))
            
            // Header Typography
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = when {
                        isCooling -> stringResource(R.string.cpu_cooling)
                        else -> stringResource(R.string.system_optimization)
                    }.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = primaryColor.copy(alpha = 0.9f),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 5.sp
                )
                
                Spacer(Modifier.height(8.dp))
                
                Surface(
                    color = primaryColor.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.2f))
                ) {
                    Text(
                        text = "STAGE ${state.cleaningCurrent} / ${state.cleaningTotal}",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            // The centerpiece: Glowing Ring + Circular Progress
            Box(contentAlignment = Alignment.Center) {
                // Background Radial Glow
                Box(
                    Modifier
                        .size(300.dp)
                        .scale(pulse)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(primaryColor.copy(alpha = 0.1f), Color.Transparent)
                            )
                        )
                )

                // Large Glowing Ring (Outer)
                Canvas(modifier = Modifier.size(220.dp)) {
                    rotate(rotation) {
                        drawCircle(
                            brush = Brush.sweepGradient(
                                colors = listOf(
                                    primaryColor.copy(alpha = 0.1f),
                                    primaryColor,
                                    primaryColor.copy(alpha = 0.1f)
                                )
                            ),
                            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                }

                // Progress Ring
                val progress = if (state.cleaningTotal > 0) state.cleaningCurrent.toFloat() / state.cleaningTotal else 0f
                val animatedProgress by animateFloatAsState(
                    targetValue = progress, 
                    animationSpec = tween(1000, easing = FastOutSlowInEasing),
                    label = "progress"
                )

                val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                Canvas(modifier = Modifier.size(180.dp)) {
                    // Track
                    drawCircle(
                        color = trackColor,
                        style = Stroke(width = 6.dp.toPx())
                    )
                    
                    // Active Progress
                    drawArc(
                        brush = Brush.sweepGradient(
                            0f to primaryColor.copy(alpha = 0.5f),
                            0.5f to primaryColor,
                            1f to primaryColor.copy(alpha = 0.5f)
                        ),
                        startAngle = -90f,
                        sweepAngle = 360f * animatedProgress,
                        useCenter = false,
                        style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                // Center Icon with inner glow
                Surface(
                    modifier = Modifier
                        .size(120.dp)
                        .scale(pulse)
                        .shadow(elevation = 20.dp, shape = CircleShape, ambientColor = primaryColor, spotColor = primaryColor),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(2.dp, primaryColor.copy(alpha = 0.5f))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        val bitmap = rememberAppIcon(cleaningPackage, currentApp?.icon)
                        if (bitmap != null) {
                            Image(
                                bitmap,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            )
                        } else {
                            Icon(Icons.Default.Apps, null, tint = primaryColor, modifier = Modifier.size(48.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.height(40.dp))

            // Ticker for current package
            Text(
                text = (cleaningPackage ?: "...").uppercase(),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )

            Spacer(Modifier.weight(1f))

            // Subtext warning
            Text(
                text = stringResource(R.string.do_not_close),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                modifier = Modifier.padding(vertical = 24.dp)
            )
        }
    }
}


@Composable
private fun OptimizationTaskItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    mode: CleanMode
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, lineHeight = 12.sp)
            }
            Text(
                text = if (mode == CleanMode.STANDARD && title != "RAM Boost") stringResource(R.string.low) else stringResource(R.string.high),
                color = if (mode == CleanMode.STANDARD && title != "RAM Boost") MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}


@Composable
private fun JunkCard(state: CleanerUiState, viewModel: CleanerViewModel) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = { viewModel.navigateTo(Screen.JUNK_CLEANER) }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.junk_cleaner).uppercase(), 
                fontSize = 11.sp, 
                fontWeight = FontWeight.Black, 
                color = MaterialTheme.colorScheme.onSurfaceVariant, 
                letterSpacing = 1.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(MemoryUtils.formatBytes(state.totalJunkSize), fontWeight = FontWeight.Black, fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurface, fontFamily = FontFamily.Monospace)
        Text(stringResource(R.string.internal_storage), color = MaterialTheme.colorScheme.primary, fontSize = 10.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun CpuCoolerCard(state: CleanerUiState, viewModel: CleanerViewModel) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = { viewModel.coolCpu() }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.AcUnit, contentDescription = null, tint = IceBlue, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.cooler_label), 
                fontSize = 11.sp, 
                fontWeight = FontWeight.Black, 
                color = MaterialTheme.colorScheme.onSurfaceVariant, 
                letterSpacing = 1.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(state.deviceInfo.batteryTemp.replace("°C", ""), fontWeight = FontWeight.Black, fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurface, fontFamily = FontFamily.Monospace)
            Text("°C", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = IceBlue, modifier = Modifier.padding(bottom = 2.dp))
            
            Spacer(Modifier.width(16.dp))
            // Sparkline
            Canvas(modifier = Modifier.width(50.dp).height(16.dp)) {
                val points = listOf(0.4f, 0.6f, 0.3f, 0.8f, 0.5f, 0.2f) // Mock trend
                val path = Path().apply {
                    moveTo(0f, size.height * (1 - points[0]))
                    points.forEachIndexed { index, p ->
                        lineTo(size.width * index / (points.size - 1), size.height * (1 - p))
                    }
                }
                drawPath(path, color = IceBlue.copy(alpha = 0.4f), style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
            }
        }
        Text(stringResource(R.string.cpu_temp_label), color = IceBlue, fontSize = 10.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun DiagnosticsCard(viewModel: CleanerViewModel) {
    Surface(
        onClick = { viewModel.navigateTo(Screen.DIAGNOSTICS) },
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(Color.White.copy(alpha = 0.2f), Color.White.copy(alpha = 0.05f))
                ),
                shape = RoundedCornerShape(24.dp)
            ),
        shape = RoundedCornerShape(24.dp),
        color = LocalCardColor.current.copy(alpha = 0.45f)
    ) {
        Box {
            Box(Modifier.matchParentSize().blur(12.dp).background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.05f), Color.Transparent))))

            Row(
                modifier = Modifier
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Analytics, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "SYSTEM DIAGNOSTICS",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Black,
                    fontSize = 11.sp,
                    letterSpacing = 1.sp
                )
                Text(
                    "CPU, Battery & Storage Health",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
            }
        }
    }
}




private fun android.graphics.drawable.Drawable.toBitmap(): android.graphics.Bitmap? {
    return try {
        if (this is android.graphics.drawable.BitmapDrawable && this.bitmap != null) return this.bitmap
        
        val width = if (intrinsicWidth > 0) intrinsicWidth else 192
        val height = if (intrinsicHeight > 0) intrinsicHeight else 192
        
        // Limit maximum bitmap size to prevent OOM
        val finalWidth = width.coerceAtMost(512)
        val finalHeight = height.coerceAtMost(512)
        
        val bmp = createBitmap(finalWidth, finalHeight, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        setBounds(0, 0, canvas.width, canvas.height)
        draw(canvas)
        bmp
    } catch (_: Throwable) {
        null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DisabledAppsDrawer(state: CleanerUiState, viewModel: CleanerViewModel) {
    val disabledApps = remember(state.apps) { state.apps.filter { it.isDisabled } }
    
    ModalBottomSheet(
        onDismissRequest = { viewModel.toggleDisabledAppsDrawer(show = false) },
        containerColor = MaterialTheme.colorScheme.surface,
        scrimColor = Color.Black.copy(alpha = 0.5f),
        dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Block, null, tint = Color.Gray, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.disabled_apps), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            }
            
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.disabled_apps_desc),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(Modifier.height(20.dp))
            
            if (disabledApps.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.no_disabled_apps_found), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    items(disabledApps) { app ->
                        DisabledAppItem(app, viewModel, state.iconStyle)
                    }
                }
            }
        }
    }
}


@Composable
private fun DisabledAppItem(app: CleanableApp, viewModel: CleanerViewModel, iconStyle: String = "ROUNDED") {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val bitmap = rememberAppIcon(app.packageName, app.icon)
        val iconShape = getIconShape(iconStyle)
        if (bitmap != null) {
            Image(bitmap, null, modifier = Modifier.size(40.dp).clip(iconShape))
        } else {
            Box(Modifier.size(40.dp).clip(iconShape).background(Color.Gray.copy(alpha = 0.2f)))
        }
        
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(app.label, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val bitnessText = if (app.is64Bit) stringResource(R.string.arch_64bit) else stringResource(R.string.arch_32bit)
                val bitnessColor = if (app.is64Bit) Color(0xFF00E5FF) else Color(0xFFB388FF)
                Text(
                    text = bitnessText,
                    color = bitnessColor,
                    fontSize = 7.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .background(bitnessColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                    letterSpacing = 0.5.sp
                )

                val (safetyText, safetyColor) = when (app.disableSafetyLevel) {
                    DisableSafetyLevel.SAFE -> Pair(stringResource(R.string.safe_to_disable), Color(0xFF00E676))
                    DisableSafetyLevel.CAUTION -> Pair(stringResource(R.string.caution_system_app), Color(0xFFFFB300))
                    DisableSafetyLevel.DANGER -> Pair(stringResource(R.string.danger_to_disable), Color(0xFFFF1744))
                }
                Text(
                    text = safetyText,
                    color = safetyColor,
                    fontSize = 7.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .background(safetyColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                    letterSpacing = 0.5.sp
                )
            }
            Text(app.packageName, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        
        Button(
            onClick = { 
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.enableApp(app.packageName) 
            },
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onSurface)
        ) {
            Text(stringResource(R.string.enable_label), fontSize = 10.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun DisabledAppsCard(state: CleanerUiState, viewModel: CleanerViewModel) {
    val disabledApps = remember(state.apps) { state.apps.filter { it.isDisabled } }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { viewModel.toggleDisabledAppsDrawer(show = true) }
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Block, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.disabled_label), 
                fontSize = 11.sp, 
                fontWeight = FontWeight.Black, 
                color = MaterialTheme.colorScheme.onSurfaceVariant, 
                letterSpacing = 1.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(stringResource(R.string.apps_hidden), color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Black)
        }
        Text("${disabledApps.size}", fontWeight = FontWeight.Black, fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun RecommendedAppsScreen(state: CleanerUiState, viewModel: CleanerViewModel, onBack: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    var showAdvanced by remember { mutableStateOf(false) }
    val result by produceState<AppRecommendations.Result?>(initialValue = null, state.apps, state.userWhitelist) {
        value = AppRecommendations.build(context, state.apps, state.userWhitelist)
    }
    val res = result
    val recs = remember(res, showAdvanced) {
        res?.recommendations?.filter { showAdvanced || it.risk.level != RiskLevel.ADVANCED } ?: emptyList()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = {
                    if (state.hapticFeedbackEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onBack()
                },
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
            }
            Spacer(Modifier.width(16.dp))
            Text(stringResource(R.string.recommended_removals), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onBackground)
        }

        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.recommended_removals_desc),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            lineHeight = 16.sp
        )
        Spacer(Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.recommend_show_advanced),
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp
            )
            Switch(checked = showAdvanced, onCheckedChange = { showAdvanced = it })
        }
        if (res != null && res.hiddenProtected > 0) {
            Text(
                stringResource(R.string.recommend_hidden_protected, res.hiddenProtected),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
        }
        Spacer(Modifier.height(12.dp))

        if (res == null) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.recommend_analyzing), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else if (recs.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.no_recommendations), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(recs, key = { it.app.packageName }) { rec ->
                    val app = rec.app
                    Column {
                        AppRow(
                            app = app,
                            isWhitelisted = false,
                            isProcessing = state.processingApps.contains(app.packageName),
                            hasElevatedAccess = state.rootAvailable || (state.shizukuAvailable && state.shizukuGranted),
                            hapticFeedbackEnabled = state.hapticFeedbackEnabled,
                            iconStyle = state.iconStyle,
                            onToggle = { viewModel.toggleApp(app.packageName) },
                            onToggleWhitelist = { viewModel.toggleWhitelist(app.packageName) },
                            onForceStop = { viewModel.forceStopApp(app.packageName) },
                            onDisable = { viewModel.disableApp(app.packageName) },
                            onEnable = { viewModel.enableApp(app.packageName) },
                            onSuspend = { viewModel.toggleAppSuspension(app.packageName, it) },
                            onSetAppOp = { op, mode -> viewModel.setAppOp(app.packageName, op, mode) },
                            onSetStandbyBucket = { viewModel.setStandbyBucket(app.packageName, it) },
                            onClearCache = { viewModel.clearCacheApp(app.packageName) },
                            onLaunch = { viewModel.launchApp(app.packageName) },
                            onUninstall = { viewModel.uninstallApp(app.packageName) },
                            onReinstall = { viewModel.reinstallApp(app.packageName) }
                        )
                        RecommendationMeta(rec)
                    }
                }
            }
        }
    }
}

@Composable
private fun ShieldScreen(state: CleanerUiState, viewModel: CleanerViewModel, onBack: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    
    // Combine state.whitelistedApps and filtering state.apps for maximum reliability
    // ensuring we only show apps that are actually in the userWhitelist set.
    val whitelistedApps = remember(state.whitelistedApps, state.apps, state.userWhitelist) {
        val fromApps = state.apps.filter { state.userWhitelist.contains(it.packageName) }
        val fromWhitelisted = state.whitelistedApps.filter { state.userWhitelist.contains(it.packageName) }
        
        // Merge them by package name to avoid duplicates
        (fromApps + fromWhitelisted).distinctBy { it.packageName }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { 
                    if (state.hapticFeedbackEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onBack() 
                },
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
            }
            Spacer(Modifier.width(16.dp))
            Text(stringResource(R.string.shield_whitelist), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onBackground)
        }

        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.shield_desc),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            lineHeight = 16.sp
        )

        Spacer(Modifier.height(16.dp))

        BeginnerSafeListCard(
            hapticFeedbackEnabled = state.hapticFeedbackEnabled,
            onApply = { viewModel.applyBeginnerSafeList() }
        )

        Spacer(Modifier.height(16.dp))

        if (whitelistedApps.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.ShieldMoon, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.no_apps_protected), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(whitelistedApps, key = { it.packageName }) { app ->
                    AppRow(
                        app = app,
                        isWhitelisted = true,
                        isProcessing = state.processingApps.contains(app.packageName),
                        hasElevatedAccess = state.rootAvailable || (state.shizukuAvailable && state.shizukuGranted),
                        hapticFeedbackEnabled = state.hapticFeedbackEnabled,
                        iconStyle = state.iconStyle,
                        onToggle = { viewModel.toggleApp(app.packageName) },
                        onToggleWhitelist = { viewModel.toggleWhitelist(app.packageName) },
                        onForceStop = { viewModel.forceStopApp(app.packageName) },
                        onDisable = { viewModel.disableApp(app.packageName) },
                        onEnable = { viewModel.enableApp(app.packageName) },
                        onSuspend = { viewModel.toggleAppSuspension(app.packageName, it) },
                        onSetAppOp = { op, mode -> viewModel.setAppOp(app.packageName, op, mode) },
                        onSetStandbyBucket = { viewModel.setStandbyBucket(app.packageName, it) },
                        onClearCache = { viewModel.clearCacheApp(app.packageName) },
                        onLaunch = { viewModel.launchApp(app.packageName) },
                        onUninstall = { viewModel.uninstallApp(app.packageName) },
                        onReinstall = { viewModel.reinstallApp(app.packageName) }
                    )
                }
            }
        }
    }
}

@Composable
private fun BeginnerSafeListCard(
    hapticFeedbackEnabled: Boolean,
    onApply: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.VerifiedUser,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.beginner_safelist_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.beginner_safelist_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    if (hapticFeedbackEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onApply()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                contentPadding = PaddingValues(vertical = 10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.beginner_safelist_btn),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
fun GlassmorphismTaskBar(
    state: CleanerUiState,
    viewModel: CleanerViewModel,
    modifier: Modifier = Modifier,
    onOpenAppDrainers: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val currentScreen = state.currentScreen

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = 16.dp, shape = RoundedCornerShape(28.dp), clip = false),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp, horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Home
            TaskBarItem(
                icon = Icons.Default.Home,
                label = stringResource(R.string.home_label),
                isSelected = currentScreen == Screen.MAIN,
                onClick = {
                    if (state.hapticFeedbackEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (currentScreen != Screen.MAIN) viewModel.navigateTo(Screen.MAIN)
                }
            )

            // 3. Shield
            TaskBarItem(
                icon = Icons.Default.Shield,
                label = stringResource(R.string.shield_label),
                isSelected = currentScreen == Screen.SHIELD,
                onClick = {
                    if (state.hapticFeedbackEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.navigateTo(Screen.SHIELD)
                }
            )

            // 4. Diagnostics
            TaskBarItem(
                icon = Icons.Default.Analytics,
                label = stringResource(R.string.diagnostics_label),
                isSelected = currentScreen == Screen.DIAGNOSTICS,
                onClick = {
                    if (state.hapticFeedbackEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.navigateTo(Screen.DIAGNOSTICS)
                }
            )

            // 5. App Drainer
            TaskBarItem(
                icon = Icons.Default.BatteryAlert,
                label = stringResource(R.string.drainer_label),
                isSelected = false,
                onClick = {
                    if (state.hapticFeedbackEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onOpenAppDrainers()
                }
            )
        }
    }
}

@Composable
private fun TaskBarItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(if (isSelected) activeColor.copy(alpha = 0.18f) else Color.Transparent)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isSelected) activeColor else inactiveColor,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
            color = if (isSelected) activeColor else inactiveColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun RamSnapshotDialog(
    state: CleanerUiState,
    viewModel: CleanerViewModel,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Memory, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.snapshot_label).uppercase() + " SNAPSHOT", fontWeight = FontWeight.Black)
            }
        },
        text = {
            Column(Modifier.fillMaxWidth()) {
                state.ram?.let { ram ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Used RAM:", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(MemoryUtils.formatBytes(ram.usedBytes), fontWeight = FontWeight.Bold)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Available RAM:", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(MemoryUtils.formatBytes(ram.availBytes), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Total RAM:", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(MemoryUtils.formatBytes(ram.totalBytes), fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    viewModel.optimizeVm()
                    onDismiss()
                }
            ) {
                Text("OPTIMIZE RAM", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    )
}

@Composable
private fun AppDrainersDialog(
    state: CleanerUiState,
    viewModel: CleanerViewModel,
    onDismiss: () -> Unit
) {
    val drainers = state.topDrainers
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.BatteryAlert, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.drainer_label).uppercase() + " APPS", fontWeight = FontWeight.Black)
            }
        },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 380.dp)) {
                if (drainers.isEmpty()) {
                    Text(stringResource(R.string.no_apps_found), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(drainers, key = { it.packageName }) { app ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val bitmap = rememberAppIcon(app.packageName, app.icon)
                                val iconShape = getIconShape(state.iconStyle)
                                if (bitmap != null) {
                                    Image(
                                        bitmap = bitmap,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(iconShape)
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(iconShape)
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = app.label,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = app.packageName,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Button(
                                    onClick = { viewModel.forceStopApp(app.packageName) },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                ) {
                                    Text("STOP", fontSize = 10.sp, fontWeight = FontWeight.Black)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    )
}

@Composable
private fun ShizukuNotRunningPopupDialog(
    onStartShizuku: () -> Unit,
    onDismiss: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Terminal,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
        },
        title = {
            Text(
                text = stringResource(R.string.shizuku_not_running_dialog_title),
                fontWeight = FontWeight.Black,
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = stringResource(R.string.shizuku_not_running_dialog_msg),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onStartShizuku()
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = stringResource(R.string.shizuku_open_button),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onDismiss()
                }
            ) {
                Text(
                    text = stringResource(R.string.dismiss),
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp)
    )
}

@Composable
private fun AppDetailDialog(
    app: CleanableApp,
    state: CleanerUiState,
    viewModel: CleanerViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val isWhitelisted = state.userWhitelist.contains(app.packageName)
    val hasElevatedAccess = state.rootAvailable || (state.shizukuAvailable && state.shizukuGranted)
    var showCriticalWarning by remember { mutableStateOf(false) }
    val riskContext = LocalContext.current
    val riskState by produceState<com.theblacksheep.appoff.core.PackageRisk?>(initialValue = null, app.packageName) {
        value = com.theblacksheep.appoff.core.PackageRiskAnalyzer.analyze(riskContext, app.packageName)
    }
    val riskNow = riskState
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    if (showCriticalWarning) {
        AlertDialog(
            onDismissRequest = { 
                showCriticalWarning = false 
                pendingAction = null
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = Danger, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.critical_app_warning_title), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Danger)
                }
            },
            text = {
                Text(
                    stringResource(R.string.critical_app_warning_desc, app.label),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCriticalWarning = false
                        pendingAction?.invoke()
                        pendingAction = null
                    }
                ) {
                    Text(stringResource(R.string.proceed_risk), color = Danger, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showCriticalWarning = false
                        pendingAction = null
                    }
                ) {
                    Text(stringResource(R.string.cancel))
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val bitmap = rememberAppIcon(app.packageName, app.icon)
                val iconShape = getIconShape(state.iconStyle)
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp).clip(iconShape)
                    )
                } else {
                    Box(Modifier.size(48.dp).clip(iconShape).background(MaterialTheme.colorScheme.surface))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = app.label,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = app.packageName,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Badges Row: 64-bit/32-bit & Safety status
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Architecture Badge
                    val bitnessText = if (app.is64Bit) stringResource(R.string.arch_64bit) else stringResource(R.string.arch_32bit)
                    val bitnessColor = if (app.is64Bit) Color(0xFF00E5FF) else Color(0xFFB388FF)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = bitnessColor.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, bitnessColor.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Memory, contentDescription = null, tint = bitnessColor, modifier = Modifier.size(12.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(bitnessText, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = bitnessColor)
                        }
                    }

                    if (riskNow != null) {
                        RiskBadge(riskNow)
                    } else {
                        // Safety Level Badge
                        val (safetyBadgeText, safetyBadgeColor) = when (app.disableSafetyLevel) {
                            DisableSafetyLevel.SAFE -> Pair(stringResource(R.string.safe_to_disable), Color(0xFF00E676))
                            DisableSafetyLevel.CAUTION -> Pair(stringResource(R.string.caution_system_app), Color(0xFFFFB300))
                            DisableSafetyLevel.DANGER -> Pair(stringResource(R.string.danger_to_disable), Color(0xFFFF1744))
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = safetyBadgeColor.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, safetyBadgeColor.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = when (app.disableSafetyLevel) {
                                        DisableSafetyLevel.SAFE -> Icons.Default.CheckCircle
                                        DisableSafetyLevel.CAUTION -> Icons.Default.Warning
                                        DisableSafetyLevel.DANGER -> Icons.Default.GppBad
                                    },
                                    contentDescription = null,
                                    tint = safetyBadgeColor,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(safetyBadgeText, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = safetyBadgeColor)
                            }
                        }
                    }
                }

                if (riskNow != null) {
                    RiskDetailsCard(riskNow)
                } else {
                    // Safety Recommendation Card
                    val cardBgColor = when (app.disableSafetyLevel) {
                        DisableSafetyLevel.SAFE -> Color(0xFF00E676).copy(alpha = 0.1f)
                        DisableSafetyLevel.CAUTION -> Color(0xFFFFB300).copy(alpha = 0.1f)
                        DisableSafetyLevel.DANGER -> Color(0xFFFF1744).copy(alpha = 0.12f)
                    }
                    val cardBorderColor = when (app.disableSafetyLevel) {
                        DisableSafetyLevel.SAFE -> Color(0xFF00E676).copy(alpha = 0.3f)
                        DisableSafetyLevel.CAUTION -> Color(0xFFFFB300).copy(alpha = 0.3f)
                        DisableSafetyLevel.DANGER -> Color(0xFFFF1744).copy(alpha = 0.4f)
                    }
                    val cardHeaderColor = when (app.disableSafetyLevel) {
                        DisableSafetyLevel.SAFE -> Color(0xFF00E676)
                        DisableSafetyLevel.CAUTION -> Color(0xFFFFB300)
                        DisableSafetyLevel.DANGER -> Color(0xFFFF1744)
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = cardBgColor,
                        border = BorderStroke(1.dp, cardBorderColor),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = when (app.disableSafetyLevel) {
                                    DisableSafetyLevel.SAFE -> stringResource(R.string.safe_user_title)
                                    DisableSafetyLevel.CAUTION -> stringResource(R.string.caution_system_title)
                                    DisableSafetyLevel.DANGER -> stringResource(R.string.danger_critical_title)
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                color = cardHeaderColor
                            )
                            Text(
                                text = when (app.disableSafetyLevel) {
                                    DisableSafetyLevel.SAFE -> stringResource(R.string.safe_user_desc)
                                    DisableSafetyLevel.CAUTION -> stringResource(R.string.caution_system_desc)
                                    DisableSafetyLevel.DANGER -> stringResource(R.string.danger_critical_desc)
                                },
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                            )
                            Text(
                                text = when (app.disableSafetyLevel) {
                                    DisableSafetyLevel.SAFE -> stringResource(R.string.safe_user_rec)
                                    DisableSafetyLevel.CAUTION -> stringResource(R.string.caution_system_rec)
                                    DisableSafetyLevel.DANGER -> stringResource(R.string.danger_critical_rec)
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = cardHeaderColor
                            )
                        }
                    }

                }

                // App Details Summary
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    DetailRow(
                        label = stringResource(R.string.architecture_label),
                        value = if (app.is64Bit) stringResource(R.string.arch_64bit_desc) else stringResource(R.string.arch_32bit_desc)
                    )
                    DetailRow(
                        label = stringResource(R.string.app_type_label),
                        value = if (app.isSystem) stringResource(R.string.system_application) else stringResource(R.string.user_installed_application)
                    )
                    if (app.memoryUsageBytes > 0) {
                        DetailRow(
                            label = stringResource(R.string.memory_usage_label),
                            value = android.text.format.Formatter.formatShortFileSize(context, app.memoryUsageBytes)
                        )
                    }
                    DetailRow(
                        label = stringResource(R.string.status_label),
                        value = buildString {
                            if (app.isDisabled) append("Disabled ")
                            if (app.isFrozen) append("Frozen ")
                            if (app.isRunning) append("Running ")
                            if (app.isStopped) append("Stopped ")
                            if (isWhitelisted) append("Shielded ")
                            if (isEmpty()) append("Normal")
                        }
                    )
                }

                // Action Buttons inside Dialog
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Disable / Enable Button
                    val isActionDisable = !app.isDisabled && !app.isHidden && !app.isUninstalled
                    OutlinedButton(
                        onClick = {
                            if (state.hapticFeedbackEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            val action = {
                                if (isActionDisable) viewModel.disableApp(app.packageName) else viewModel.enableApp(app.packageName)
                                onDismiss()
                            }
                            if (isActionDisable && (app.disableSafetyLevel == DisableSafetyLevel.DANGER || riskNow?.level == RiskLevel.PROTECTED)) {
                                pendingAction = action
                                showCriticalWarning = true
                            } else {
                                action()
                            }
                        },
                        enabled = hasElevatedAccess && !state.processingApps.contains(app.packageName),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (isActionDisable) stringResource(R.string.disable_app) else stringResource(R.string.enable_app))
                    }

                    // Uninstall Button
                    OutlinedButton(
                        onClick = {
                            if (state.hapticFeedbackEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            val action = {
                                viewModel.uninstallApp(app.packageName)
                                onDismiss()
                            }
                            if (app.disableSafetyLevel == DisableSafetyLevel.DANGER || riskNow?.level == RiskLevel.PROTECTED) {
                                pendingAction = action
                                showCriticalWarning = true
                            } else {
                                action()
                            }
                        },
                        enabled = if (app.isSystem) hasElevatedAccess && !state.processingApps.contains(app.packageName) else true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Danger)
                    ) {
                        Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(16.dp), tint = Danger)
                        Spacer(Modifier.width(8.dp))
                        Text(if (app.isSystem) stringResource(R.string.uninstall_bloatware) else stringResource(R.string.uninstall), color = Danger)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
        Text(value, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun AppIntroScreen(
    state: CleanerUiState,
    viewModel: CleanerViewModel,
    onComplete: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var currentPage by remember { mutableIntStateOf(0) }
    val totalPages = 4

    val slides = listOf(
        IntroSlideData(
            tag = stringResource(R.string.intro_slide1_tag),
            title = stringResource(R.string.intro_slide1_title),
            desc = stringResource(R.string.intro_slide1_desc),
            icon = Icons.Rounded.Memory,
            accentColor = Color(0xFF00E5A0)
        ),
        IntroSlideData(
            tag = stringResource(R.string.intro_slide2_tag),
            title = stringResource(R.string.intro_slide2_title),
            desc = stringResource(R.string.intro_slide2_desc),
            icon = Icons.Rounded.Terminal,
            accentColor = Color(0xFF00BFFF)
        ),
        IntroSlideData(
            tag = stringResource(R.string.intro_slide3_tag),
            title = stringResource(R.string.intro_slide3_title),
            desc = stringResource(R.string.intro_slide3_desc),
            icon = Icons.Default.BatteryChargingFull,
            accentColor = Color(0xFFFFB703)
        ),
        IntroSlideData(
            tag = stringResource(R.string.intro_slide4_tag),
            title = stringResource(R.string.intro_slide4_title),
            desc = stringResource(R.string.intro_slide4_desc),
            icon = Icons.Default.Shield,
            accentColor = Color(0xFF7000FF)
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                ) {
                    Text(
                        text = "${currentPage + 1} / $totalPages",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }

                TextButton(
                    onClick = {
                        if (state.hapticFeedbackEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onComplete()
                    }
                ) {
                    Text(
                        text = stringResource(R.string.intro_skip).uppercase(),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pointerInput(currentPage) {
                        detectHorizontalDragGestures { _, dragAmount ->
                            if (dragAmount < -50 && currentPage < totalPages - 1) {
                                currentPage++
                            } else if (dragAmount > 50 && currentPage > 0) {
                                currentPage--
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = currentPage,
                    transitionSpec = {
                        if (targetState > initialState) {
                            (slideInHorizontally { width -> width } + fadeIn()).togetherWith(
                                slideOutHorizontally { width -> -width } + fadeOut()
                            )
                        } else {
                            (slideInHorizontally { width -> -width } + fadeIn()).togetherWith(
                                slideOutHorizontally { width -> width } + fadeOut()
                            )
                        }
                    },
                    label = "intro_slide_anim"
                ) { pageIndex ->
                    IntroSlideItem(slide = slides[pageIndex])
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(totalPages) { index ->
                        val isSelected = index == currentPage
                        val width by animateDpAsState(
                            targetValue = if (isSelected) 28.dp else 8.dp,
                            animationSpec = tween(300),
                            label = "indicator_dot_width"
                        )
                        Box(
                            modifier = Modifier
                                .height(8.dp)
                                .width(width)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) slides[currentPage].accentColor
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                                )
                                .clickable { currentPage = index }
                        )
                    }
                }

                Spacer(Modifier.height(28.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentPage > 0) {
                        OutlinedButton(
                            onClick = {
                                if (state.hapticFeedbackEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                currentPage--
                            },
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                            modifier = Modifier.height(48.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.intro_back),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    } else {
                        Spacer(Modifier.width(1.dp))
                    }

                    val isLastPage = currentPage == totalPages - 1

                    Button(
                        onClick = {
                            if (state.hapticFeedbackEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (isLastPage) {
                                onComplete()
                            } else {
                                currentPage++
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = slides[currentPage].accentColor,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .height(48.dp)
                            .then(if (isLastPage) Modifier.fillMaxWidth(0.85f) else Modifier)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = if (isLastPage) stringResource(R.string.intro_get_started) else stringResource(R.string.intro_next),
                                fontWeight = FontWeight.Black,
                                fontSize = 14.sp
                            )
                            Icon(
                                imageVector = if (isLastPage) Icons.Default.Check else Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class IntroSlideData(
    val tag: String,
    val title: String,
    val desc: String,
    val icon: ImageVector,
    val accentColor: Color
)

@Composable
private fun IntroSlideItem(slide: IntroSlideData) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(130.dp)
                .clip(CircleShape)
                .background(slide.accentColor.copy(alpha = 0.12f))
                .border(2.dp, slide.accentColor.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .clip(CircleShape)
                    .background(slide.accentColor.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = slide.icon,
                    contentDescription = null,
                    tint = slide.accentColor,
                    modifier = Modifier.size(48.dp)
                )
            }
        }

        Spacer(Modifier.height(36.dp))

        Surface(
            color = slide.accentColor.copy(alpha = 0.15f),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, slide.accentColor.copy(alpha = 0.3f))
        ) {
            Text(
                text = slide.tag,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.5.sp,
                color = slide.accentColor,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
            )
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = slide.title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(Modifier.height(14.dp))

        Text(
            text = slide.desc,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 22.sp
        )
    }
}

