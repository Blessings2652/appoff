package com.theblacksheep.appoff.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

import com.theblacksheep.appoff.R
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.delay

@Composable
fun SystemToolsScreen(viewModel: CleanerViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()

    var showPingDialog by remember { mutableStateOf(value = false) }
    var showScreenTimeDialog by remember { mutableStateOf(value = false) }
    var showTaskManager by remember { mutableStateOf(value = false) }

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
                onClick = onBack,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.width(16.dp))
            Text(stringResource(R.string.advanced_system_tools), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
        }

        Spacer(Modifier.height(24.dp))

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ToolCategory(
                stringResource(R.string.monitoring),
                Icons.Default.Assessment,
            ) {
                ToolItem(stringResource(R.string.task_manager), stringResource(R.string.task_manager_desc)) {
                    showTaskManager = true
                }
                ToolItem(stringResource(R.string.screen_time), stringResource(R.string.screen_time_desc)) {
                    showScreenTimeDialog = true
                }
            }

            ToolCategory(
                stringResource(R.string.network_connectivity),
                Icons.Default.NetworkCheck,
            ) {
                ToolItem(stringResource(R.string.ping_utility), stringResource(R.string.ping_utility_desc)) {
                    showPingDialog = true
                }
                val context = androidx.compose.ui.platform.LocalContext.current
                ToolItem(stringResource(R.string.usb_utilities), stringResource(R.string.usb_utilities_desc)) {
                    try {
                        val intent = android.content.Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS)
                        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        // Fallback to general settings
                        val intent = android.content.Intent(android.provider.Settings.ACTION_SETTINGS)
                        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    }
                }
            }

            
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showPingDialog) {
        PingDialog(
            state = state,
            viewModel = viewModel,
            onDismiss = { showPingDialog = false },
        )
    }

    if (showScreenTimeDialog) {
        ScreenTimeDialog(
            state = state,
            viewModel = viewModel,
            onDismiss = { showScreenTimeDialog = false },
        )
    }

    if (showTaskManager) {
        TaskManagerDialog(
            state = state,
            viewModel = viewModel,
            onDismiss = { showTaskManager = false },
        )
    }

}

@Composable
fun TaskManagerDialog(state: CleanerUiState, viewModel: CleanerViewModel, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.task_manager), fontWeight = FontWeight.Black) },
        text = {
            Column(Modifier.height(400.dp)) {
                LazyColumn {
                    items(state.apps.filter { it.isRunning }) { app ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(app.label, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                            IconButton(onClick = { viewModel.forceStopApp(app.packageName) }) {
                                Icon(Icons.Default.Close, null, tint = Color.Red)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } }
    )
}

@Composable
fun PingDialog(state: CleanerUiState, viewModel: CleanerViewModel, onDismiss: () -> Unit) {
    LaunchedEffect(Unit) {
        while(true) {
            viewModel.updateNetworkPing()
            delay(1.seconds)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.network_latency), fontWeight = FontWeight.Black) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Wifi, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(16.dp))
                Text(
                    text = if (state.pingMs >= 0) stringResource(R.string.ms_val, state.pingMs) else stringResource(R.string.searching),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    color = if (state.pingMs < 100) MaterialTheme.colorScheme.primary else Color.Yellow
                )
                Text(stringResource(R.string.rtt_desc), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) }
        }
    )
}

@Composable
fun ScreenTimeDialog(state: CleanerUiState, viewModel: CleanerViewModel, onDismiss: () -> Unit) {
    LaunchedEffect(Unit) {
        viewModel.refreshScreenTime()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.screen_time_title), fontWeight = FontWeight.Black) },
        text = {
            Column(Modifier.height(400.dp)) {
                if (state.screenTimeStats.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.no_usage_data), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                } else {
                    LazyColumn {
                        items(state.screenTimeStats) { usage ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    usage.packageName.split(".").last().uppercase(), 
                                    fontWeight = FontWeight.Bold, 
                                    fontSize = 12.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    formatDuration(usage.totalTimeInForeground),
                                    fontWeight = FontWeight.Black,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        }
    )
}

private fun formatDuration(millis: Long): String {
    val hours = TimeUnit.MILLISECONDS.toHours(millis)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

@Composable
fun ToolCategory(name: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    GlassCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(name.uppercase(), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.sp)
        }
        Spacer(Modifier.height(16.dp))
        content()
    }
}

@Composable
fun ToolItem(title: String, description: String, onClick: () -> Unit = {}) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        color = Color.Transparent
    ) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
        }
    }
}
