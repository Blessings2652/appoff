package com.theblacksheep.appoff.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.ManageSearch
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import com.theblacksheep.appoff.root.RootCleaner
import com.theblacksheep.appoff.shizuku.ShizukuHelper
import java.util.Locale
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import com.theblacksheep.appoff.core.MemoryUtils
import com.theblacksheep.appoff.ui.theme.Danger

import com.theblacksheep.appoff.R
import com.theblacksheep.appoff.ui.GlassCard
import androidx.compose.ui.res.stringResource

@Composable
fun DiagnosticsScreen(state: CleanerUiState, viewModel: CleanerViewModel, onBack: () -> Unit) {
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(Unit) {
        viewModel.analyzeProcesses()
        viewModel.auditSystemHealth()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(horizontal = 20.dp),
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
            Text(stringResource(R.string.diagnostics_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onBackground)
        }

        Spacer(Modifier.height(24.dp))

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            DiagnosticStatusBanner(state)

            SystemTimeCard(state, viewModel)

            ClusterFrequencyCard(state)

            KernelMemoryCard(state)

            DetailedMemoryCard(state)

            NetworkMonitorCard(state)

            ProcessAnalysisCard(state, viewModel)

            SystemHealthCard(state, viewModel)

            InfoSection(stringResource(R.string.developer_power_tools), Icons.Default.DeveloperMode) {
                InfoRow(stringResource(R.string.shell_engine), state.shellEngine)
                val binderHealthy = state.binderStatus == stringResource(R.string.binder_healthy)
                InfoRow(
                    stringResource(R.string.binder_status), 
                    state.binderStatus, 
                    status = if (binderHealthy) "[HEALTHY]" else "[DISCONNECTED]",
                    statusColor = if (binderHealthy) MaterialTheme.colorScheme.primary else Danger
                )
            }

            InfoSection(stringResource(R.string.processing_power), Icons.Default.Memory) {
                InfoRow(stringResource(R.string.cpu_hardware), state.deviceInfo.cpuName)
                InfoRow(stringResource(R.string.core_count), stringResource(R.string.core_count_val, state.deviceInfo.cpuCores))
                InfoRow(stringResource(R.string.ram_capacity), state.deviceInfo.ramCapacity)
            }

            StorageDiagnostics(state)

            VmDiagnostics(state, viewModel)


            InfoSection(stringResource(R.string.battery_health), Icons.Default.BatteryChargingFull) {
                val tempVal = state.deviceInfo.batteryTemp.replace("°C", "").toFloatOrNull() ?: 0f
                val isHot = tempVal > 40f
                
                InfoRow(stringResource(R.string.current_level), "${state.deviceInfo.batteryLevel}%")
                InfoRow(stringResource(R.string.temperature), state.deviceInfo.batteryTemp, if (isHot) "[WARNING]" else "[COOL]", if (isHot) Danger else MaterialTheme.colorScheme.primary)
                InfoRow(stringResource(R.string.health_status), state.deviceInfo.batteryHealth, "[PASSED]", MaterialTheme.colorScheme.primary)
            }

            InfoSection(stringResource(R.string.system_software), Icons.Default.Info) {
                InfoRow(stringResource(R.string.version), state.deviceInfo.androidVersion)
                InfoRow(stringResource(R.string.security), state.deviceInfo.securityPatch)
            }
            
            Spacer(Modifier.height(24.dp))
        }
    }

}

@Composable
private fun DiagnosticStatusBanner(state: CleanerUiState) {
    val isHealthy = ((state.deviceInfo.batteryHealth == "Good") && (state.deviceInfo.storageFraction < 0.9f))
    val color = if (isHealthy) MaterialTheme.colorScheme.primary else Color(0xFFFFA500)
    
    GlassCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isHealthy) Icons.Default.CheckCircle else Icons.Default.Info,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    text = if (isHealthy) stringResource(R.string.system_health_optimal) else stringResource(R.string.system_action_required),
                    color = color,
                    fontWeight = FontWeight.Black,
                    fontSize = 12.sp,
                    letterSpacing = 1.sp
                )
                Text(
                    text = if (isHealthy) stringResource(R.string.healthy_desc) else stringResource(R.string.action_required_desc),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun VmDiagnostics(state: CleanerUiState, viewModel: CleanerViewModel) {
    val haptic = LocalHapticFeedback.current
    val vm = state.vmStats ?: return

    InfoSection(stringResource(R.string.vm_runtime_health), Icons.Default.Terminal) {
        val dalvikPercent = if (vm.dalvikMax > 0) (vm.dalvikUsed * 100 / vm.dalvikMax).toInt() else 0
        
        InfoRow(stringResource(R.string.dalvik_art_heap), MemoryUtils.formatBytes(vm.dalvikUsed), "$dalvikPercent%", if (dalvikPercent > 80) Danger else MaterialTheme.colorScheme.primary)
        InfoRow(stringResource(R.string.native_heap), MemoryUtils.formatBytes(vm.nativeUsed))
        InfoRow(stringResource(R.string.vm_version), vm.vmVersion)
        
        Spacer(Modifier.height(16.dp))
        
        Button(
            onClick = { 
                if (state.hapticFeedbackEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.optimizeVm() 
            },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), contentColor = MaterialTheme.colorScheme.primary)
        ) {
            Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.optimize_runtime), fontWeight = FontWeight.Black, fontSize = 12.sp)
        }
    }
}

@Composable
private fun InfoSection(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    GlassCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(title.uppercase(), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.sp)
        }
        Spacer(Modifier.height(16.dp))
        content()
    }
}

@Composable
private fun ClusterFrequencyCard(state: CleanerUiState) {
    InfoSection(stringResource(R.string.cluster_freq_hub), Icons.Default.Bolt) {
        val freqs = state.liveStats.cpuFrequencies
        if (freqs.isEmpty()) {
            Text(stringResource(R.string.initializing), color = Color.Gray, fontSize = 11.sp)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                freqs.chunked(4).forEachIndexed { clusterIdx, cluster ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        cluster.forEachIndexed { coreIdx, freq ->
                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                            ) {
                                Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(stringResource(R.string.core_val, clusterIdx * 4 + coreIdx), fontSize = 8.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                    Text("${(freq / 1000)} MHz", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Black)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KernelMemoryCard(state: CleanerUiState) {
    InfoSection(stringResource(R.string.kernel_ram_profile), Icons.Default.Memory) {
        val stats = state.liveStats
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            MemoryMetricRow(stringResource(R.string.active_in_use), stats.memActive, MaterialTheme.colorScheme.primary)
            MemoryMetricRow(stringResource(R.string.inactive_standby), stats.memInactive, Color.Cyan)
            MemoryMetricRow(stringResource(R.string.kernel_slab), stats.memSlab, Color.Yellow)
            
            Text(
                stringResource(R.string.kernel_slab_desc),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                fontSize = 9.sp,
                lineHeight = 12.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun DetailedMemoryCard(state: CleanerUiState) {
    val ram = state.ram ?: return
    
    InfoSection(stringResource(R.string.ram_arch_breakdown), Icons.Default.PieChart) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            MemoryMetricRow(stringResource(R.string.physical_capacity), ram.totalBytes, MaterialTheme.colorScheme.primary)
            MemoryMetricRow(stringResource(R.string.cached_buffers), ram.cachedBytes, Color.Cyan)
            MemoryMetricRow(stringResource(R.string.reclaimable_slab), ram.sReclaimableBytes, Color.Yellow)
            MemoryMetricRow(stringResource(R.string.zram_swap_total), ram.swapTotalBytes, Color.Magenta)
            MemoryMetricRow(stringResource(R.string.zram_swap_free), ram.swapFreeBytes, Color.LightGray)
            
            Spacer(Modifier.height(8.dp))
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    stringResource(R.string.cached_memory_desc),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }
}

@Composable
private fun MemoryMetricRow(label: String, bytes: Long, color: Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(8.dp))
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
        Text(MemoryUtils.formatBytes(bytes), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

@Composable
private fun NetworkMonitorCard(state: CleanerUiState) {
    InfoSection(stringResource(R.string.realtime_network), Icons.Default.NetworkCheck) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NetworkSpeedBox(stringResource(R.string.download), state.liveStats.netDownSpeed, Icons.Default.Download, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
            NetworkSpeedBox(stringResource(R.string.upload), state.liveStats.netUpSpeed, Icons.Default.Upload, Color.Magenta, Modifier.weight(1f))
        }
    }
}

@Composable
private fun NetworkSpeedBox(label: String, speedBytes: Long, icon: ImageVector, color: Color, modifier: Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = color, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(label, fontSize = 9.sp, fontWeight = FontWeight.Black, color = color)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = formatSpeed(speedBytes),
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

private fun formatSpeed(bytesPerSec: Long): String {
    return when {
        bytesPerSec >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB/s", bytesPerSec / (1024.0 * 1024.0))
        bytesPerSec >= 1024 -> String.format(Locale.US, "%.1f KB/s", bytesPerSec / 1024.0)
        else -> "$bytesPerSec B/s"
    }
}

@Composable
private fun InfoRow(label: String, value: String, status: String? = null, statusColor: Color = MaterialTheme.colorScheme.onSurface) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            status?.let { s ->
                Text(
                    text = s, 
                    color = statusColor, 
                    fontSize = 10.sp, 
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
            Text(value, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}

@Composable
private fun StorageDiagnostics(state: CleanerUiState) {
    val animatedProgress by animateFloatAsState(
        targetValue = state.deviceInfo.storageFraction,
        animationSpec = tween(1000),
        label = "storage_anim"
    )

    GlassCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Storage, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.internal_storage), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.sp)
        }
        
        Spacer(Modifier.height(20.dp))
        
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.storage_used, state.deviceInfo.storageUsed), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(stringResource(R.string.storage_total, state.deviceInfo.storageTotal), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        }
        
        Spacer(Modifier.height(12.dp))
        
        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
        )
    }
}

@Composable
private fun SystemTimeCard(state: CleanerUiState, viewModel: CleanerViewModel) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current

    InfoSection("System Time & Uptime", Icons.Default.Schedule) {
        val timeInfo = state.systemTimeInfo

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = timeInfo.localTimeFormatted.ifEmpty { "00:00:00" },
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = timeInfo.timeZoneFormatted,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "LIVE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Deep Sleep vs Active progress bar
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Deep Sleep vs Active", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("${timeInfo.deepSleepPercent}% Deep Sleep", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.Cyan)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction = (timeInfo.deepSleepPercent.coerceIn(0, 100) / 100f))
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color.Cyan)
                )
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Sleep: ${timeInfo.formattedDeepSleepTime}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Awake: ${timeInfo.formattedActiveTime}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(Modifier.height(16.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoRow("Total Uptime", timeInfo.formattedUptime)
            InfoRow("Boot Time", timeInfo.bootTimeFormatted)
            InfoRow("Runtime Session", timeInfo.jvmUptimeFormatted)
        }

        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    if (state.hapticFeedbackEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    try {
                        val intent = Intent(Settings.ACTION_DATE_SETTINGS)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    } catch (_: Exception) {}
                },
                modifier = Modifier.weight(1f).height(40.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Icon(Icons.Default.Settings, null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text("Time Settings", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            if (state.rootAvailable || state.shizukuGranted) {
                val scope = rememberCoroutineScope()
                Button(
                    onClick = {
                        if (state.hapticFeedbackEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        scope.launch(Dispatchers.IO) {
                            if (state.rootAvailable) {
                                RootCleaner.runShell("reboot")
                            } else {
                                ShizukuHelper.runCommand("reboot")
                            }
                        }
                    },
                    modifier = Modifier.weight(1f).height(40.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Danger.copy(alpha = 0.15f),
                        contentColor = Danger
                    )
                ) {
                    Icon(Icons.Default.RestartAlt, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Reboot", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ProcessAnalysisCard(state: CleanerUiState, viewModel: CleanerViewModel) {
    val haptic = LocalHapticFeedback.current
    var showFullList by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(0) }
    val selectedPids = remember { mutableStateListOf<Int>() }

    val filteredProcesses = remember(state.processAnalysis, searchQuery, selectedTab) {
        state.processAnalysis.filter { proc ->
            val matchesSearch = searchQuery.isBlank() ||
                    proc.name.contains(searchQuery, ignoreCase = true) ||
                    proc.label.contains(searchQuery, ignoreCase = true) ||
                    proc.pid.toString().contains(searchQuery)
            val matchesTab = when (selectedTab) {
                1 -> !proc.isSystemApp
                2 -> proc.isSystemApp
                else -> true
            }
            matchesSearch && matchesTab
        }
    }

    InfoSection(stringResource(R.string.process_analysis), Icons.Default.Assessment) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${state.processAnalysis.size} ACTIVE PROCESSES",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "TASK MANAGER",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        if (state.isAnalyzingProcesses) {
            Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(32.dp))
            }
        } else if (state.processAnalysis.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.processAnalysis.take(5).forEach { info ->
                    ProcessRowItem(
                        info = info,
                        state = state,
                        onKill = { viewModel.killProcess(info.pid, info.packageName) }
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = {
                    if (state.hapticFeedbackEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    showFullList = true
                },
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Icon(Icons.AutoMirrored.Filled.FormatListBulleted, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Open Full Task Manager (${state.processAnalysis.size})", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                if (state.hapticFeedbackEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.analyzeProcesses()
            },
            modifier = Modifier.fillMaxWidth().height(44.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                contentColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(Icons.AutoMirrored.Filled.ManageSearch, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.run_deep_analysis), fontWeight = FontWeight.Black, fontSize = 12.sp)
        }
    }

    if (showFullList) {
        AlertDialog(
            onDismissRequest = {
                showFullList = false
                selectedPids.clear()
            },
            title = {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Task Manager", fontWeight = FontWeight.Black)
                    Text("${filteredProcesses.size} processes", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                }
            },
            text = {
                Column(Modifier.fillMaxWidth().height(480.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search by name or PID...", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Search, null, modifier = Modifier.size(18.dp)) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, null, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    )

                    Spacer(Modifier.height(8.dp))

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("ALL", "USER APPS", "SYSTEM").forEachIndexed { idx, label ->
                            FilterChip(
                                selected = selectedTab == idx,
                                onClick = { selectedTab = idx },
                                label = { Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    val scrollState = rememberScrollState()
                    Column(
                        Modifier.weight(1f).verticalScroll(scrollState),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (filteredProcesses.isEmpty()) {
                            Box(
                                Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("No matching processes found", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            filteredProcesses.forEach { info ->
                                val isSelected = selectedPids.contains(info.pid)
                                ProcessRowItem(
                                    info = info,
                                    state = state,
                                    isSelected = isSelected,
                                    onSelectToggle = {
                                        if (isSelected) selectedPids.remove(info.pid) else selectedPids.add(info.pid)
                                    },
                                    onKill = { viewModel.killProcess(info.pid, info.packageName) }
                                )
                            }
                        }
                    }

                    if (selectedPids.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                val killItems = filteredProcesses.filter { selectedPids.contains(it.pid) }
                                    .map { it.pid to it.packageName }
                                viewModel.killSelectedProcesses(killItems)
                                selectedPids.clear()
                            },
                            modifier = Modifier.fillMaxWidth().height(44.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Danger,
                                contentColor = Color.White
                            )
                        ) {
                            Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Kill Selected (${selectedPids.size})", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showFullList = false
                    selectedPids.clear()
                }) { Text(stringResource(R.string.close)) }
            },
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}

@Composable
private fun ProcessRowItem(
    info: ProcessAnalysisInfo,
    state: CleanerUiState,
    isSelected: Boolean = false,
    onSelectToggle: (() -> Unit)? = null,
    onKill: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    val adjColor = when (info.adj) {
        "FOREGROUND" -> Danger
        "VISIBLE" -> Color(0xFFFF9800)
        "PERCEPTIBLE" -> Color(0xFFFFC107)
        "SERVICE" -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onSelectToggle != null) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onSelectToggle() },
                    modifier = Modifier.scale(0.75f)
                )
                Spacer(Modifier.width(8.dp))
            }

            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = info.label.ifEmpty { info.name },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (info.isSystemApp) {
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                "SYS",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }

                Text(
                    text = "${info.name} | PID: ${info.pid}",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.width(8.dp))

            Surface(
                color = adjColor.copy(alpha = 0.15f),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = info.adj,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    color = adjColor,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }

            Spacer(Modifier.width(8.dp))

            IconButton(
                onClick = {
                    if (state.hapticFeedbackEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onKill()
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Kill Process",
                    tint = Danger,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun SystemHealthCard(state: CleanerUiState, viewModel: CleanerViewModel) {
    val haptic = LocalHapticFeedback.current
    var showLogsDialog by remember { mutableStateOf(value = false) }

    InfoSection(stringResource(R.string.system_health), Icons.Default.HealthAndSafety) {
        Text(stringResource(R.string.system_health_desc), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))

        val errorCount = state.systemHealthLogs.size
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (errorCount == 0) Icons.Default.CheckCircle else Icons.Default.Error,
                null,
                tint = if (errorCount == 0) MaterialTheme.colorScheme.primary else Danger,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (errorCount > 0) stringResource(R.string.recent_system_errors, errorCount) else stringResource(R.string.no_errors_found),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (errorCount > 0) Danger else MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { 
                if (state.hapticFeedbackEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.auditSystemHealth()
                showLogsDialog = true
            },
            modifier = Modifier.fillMaxWidth().height(44.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), contentColor = MaterialTheme.colorScheme.primary)
        ) {
            Text(stringResource(R.string.audit_logs), fontWeight = FontWeight.Black, fontSize = 12.sp)
        }
    }

    if (showLogsDialog) {
        AlertDialog(
            onDismissRequest = { showLogsDialog = false },
            title = { Text(stringResource(R.string.system_health), fontWeight = FontWeight.Black) },
            text = {
                Box(Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                    if (state.isAuditingSystem) {
                        CircularProgressIndicator(Modifier.align(Alignment.Center))
                    } else {
                        val scrollState = rememberScrollState()
                        Column(Modifier.verticalScroll(scrollState)) {
                            if (state.systemHealthLogs.isEmpty()) {
                            Text(
                                stringResource(R.string.no_errors_found),
                                modifier = Modifier
                                    .padding(vertical = 20.dp)
                                    .align(Alignment.CenterHorizontally),
                            )
                        } else {
                            state.systemHealthLogs.forEach { log ->
                                Text(log, fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurface)
                                HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                            }
                        }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLogsDialog = false }) {
                    Text(stringResource(R.string.close), fontWeight = FontWeight.Bold)
                }
            },
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurface,
        )
    }
}

