package com.winds.bledebugger

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.winds.bledebugger.ui.theme.BLEdebuggerTheme

@Composable
fun BluetoothDebuggerApp() {
    val context = LocalContext.current
    val bleController = remember(context.applicationContext) {
        BleController(context.applicationContext)
    }
    var currentTab by rememberSaveable { androidx.compose.runtime.mutableIntStateOf(0) }
    var showBottomBar by rememberSaveable { mutableStateOf(true) }
    val deviceScrollState = rememberScrollState()
    val debugScrollState = rememberScrollState()
    val logScrollState = rememberScrollState()

    DisposableEffect(bleController) {
        bleController.startObserving()
        onDispose { bleController.dispose() }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        bleController.refreshState()
        bleController.appendLog(
            type = "SYS",
            message = if (bleController.hasRequiredPermissions()) "蓝牙权限已授权" else "蓝牙权限未完整授权"
        )
    }
    val enableBluetoothLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        bleController.refreshState()
    }

    LaunchedEffect(currentTab) {
        val activeScrollState = when (currentTab) {
            0 -> deviceScrollState
            1 -> debugScrollState
            else -> logScrollState
        }
        var previous = activeScrollState.value
        snapshotFlow { activeScrollState.value }.collect { current ->
            showBottomBar = current <= previous || current < 8
            previous = current
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f)
                    )
                )
            )
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0)
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(top = 10.dp)
                    .padding(innerPadding)
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Crossfade(
                    targetState = currentTab,
                    label = "page_switch",
                    animationSpec = tween(durationMillis = 220),
                    modifier = Modifier.weight(1f)
                ) { targetTab ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(
                                when (targetTab) {
                                    0 -> deviceScrollState
                                    1 -> debugScrollState
                                    else -> logScrollState
                                }
                            ),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        when (targetTab) {
                            0 -> DeviceScreen(
                                controller = bleController,
                                onRequestPermissions = { permissionLauncher.launch(BLE_PERMISSIONS) },
                                onRequestEnableBluetooth = {
                                    enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                                }
                            )
                            1 -> DebugScreen(controller = bleController)
                            else -> LogScreen(controller = bleController)
                        }
                        Spacer(modifier = Modifier.height(112.dp))
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showBottomBar,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            StandardBottomBar(
                currentTab = currentTab,
                onTabSelected = { currentTab = it }
            )
        }
    }
}

@Composable
fun StandardBottomBar(
    currentTab: Int,
    onTabSelected: (Int) -> Unit
) {
    val tabs = listOf(
        BottomTab("设备", R.drawable.ic_device_tab),
        BottomTab("调试", R.drawable.ic_debug_tab),
        BottomTab("日志", R.drawable.ic_log_tab)
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(vertical = 8.dp)
        ) {
            tabs.forEachIndexed { index, tab ->
                val selected = currentTab == index
                val interactionSource = remember { MutableInteractionSource() }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = { onTabSelected(index) }
                        )
                        .padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        painter = painterResource(tab.iconRes),
                        contentDescription = tab.label,
                        tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = tab.label,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun BluetoothDebuggerPreview() {
    BLEdebuggerTheme {
        BluetoothDebuggerApp()
    }
}

@Preview(
    name = "Dark",
    showBackground = true,
    showSystemUi = true,
    device = Devices.PIXEL_6,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES
)
@Composable
fun BluetoothDebuggerDarkPreview() {
    BLEdebuggerTheme(darkTheme = true, dynamicColor = false) {
        BluetoothDebuggerApp()
    }
}
