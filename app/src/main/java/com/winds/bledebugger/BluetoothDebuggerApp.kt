package com.winds.bledebugger

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.winds.bledebugger.ui.theme.BLEdebuggerTheme
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.extra.SuperDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun BluetoothDebuggerApp() {
    val context = LocalContext.current
    val controller = remember(context.applicationContext) { BleController(context.applicationContext) }
    var currentTab by rememberSaveable { androidx.compose.runtime.mutableIntStateOf(0) }
    var bannerMessage by remember { mutableStateOf<String?>(null) }
    var availableUpdate by remember { mutableStateOf<ReleaseUpdate?>(null) }
    val isPreview = LocalInspectionMode.current

    DisposableEffect(controller) {
        controller.startObserving()
        onDispose { controller.dispose() }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        controller.refreshState()
        controller.appendLog("SYS", if (controller.hasRequiredPermissions()) "蓝牙权限已授权" else "蓝牙权限未完整授权")
    }
    val enableBluetoothLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { controller.refreshState() }

    LaunchedEffect(controller.incomingMessageDialog) {
        val message = controller.incomingMessageDialog ?: return@LaunchedEffect
        bannerMessage = message
        delay(2800)
        if (controller.incomingMessageDialog == message) controller.dismissIncomingMessageDialog()
        bannerMessage = null
    }

    LaunchedEffect(Unit) {
        if (!isPreview) {
            availableUpdate = UpdateChecker.findAvailableUpdate(context.applicationContext)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MiuixTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            MiuixBottomBar(currentTab = currentTab, onTabSelected = { currentTab = it })
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Crossfade(
                targetState = currentTab,
                animationSpec = tween(220),
                label = "page_switch",
                modifier = Modifier.fillMaxSize()
            ) { tab ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .statusBarsPadding()
                        .padding(horizontal = 18.dp)
                        .padding(top = 12.dp, bottom = 8.dp)
                ) {
                    when (tab) {
                        0 -> DeviceScreen(
                            controller = controller,
                            onRequestPermissions = { permissionLauncher.launch(BLE_PERMISSIONS) },
                            onRequestEnableBluetooth = {
                                enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                            }
                        )
                        1 -> Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                        ) {
                            DebugScreen(controller)
                        }
                        else -> LogScreen(controller)
                    }
                }
            }

            AnimatedVisibility(
                visible = bannerMessage != null,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 18.dp),
                enter = slideInVertically { -it } + fadeIn(),
                exit = slideOutVertically { -it } + fadeOut()
            ) {
                bannerMessage?.let { IncomingMessageBanner(it) }
            }

            val update = availableUpdate
            SuperDialog(
                title = update?.let { "发现新版本 ${it.tagName}" },
                summary = update?.releaseNotes?.ifBlank { "新版本已经发布，是否前往下载？" },
                show = update != null,
                onDismissRequest = { availableUpdate = null }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TextButton(
                        text = "稍后",
                        onClick = { availableUpdate = null },
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        text = "前往下载",
                        onClick = {
                            val releaseUrl = update?.releaseUrl ?: return@TextButton
                            availableUpdate = null
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(releaseUrl))
                                )
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary()
                    )
                }
            }
        }
    }
}

@Composable
private fun MiuixBottomBar(currentTab: Int, onTabSelected: (Int) -> Unit) {
    val tabs = listOf(
        BottomTab("设备", R.drawable.ic_device_tab),
        BottomTab("发送", R.drawable.ic_debug_tab),
        BottomTab("日志", R.drawable.ic_log_tab)
    )
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MiuixTheme.colorScheme.surface,
        shadowElevation = 10.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(vertical = 8.dp)
        ) {
            tabs.forEachIndexed { index, tab ->
                val selected = currentTab == index
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onTabSelected(index) }
                        )
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val tint = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary
                    Image(
                        painter = painterResource(tab.iconRes),
                        contentDescription = tab.label,
                        colorFilter = ColorFilter.tint(tint),
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = tab.label,
                        color = tint,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        style = MiuixTheme.textStyles.footnote1
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun BluetoothDebuggerPreview() {
    BLEdebuggerTheme { BluetoothDebuggerApp() }
}

@Preview(name = "Dark", showBackground = true, showSystemUi = true, device = Devices.PIXEL_6)
@Composable
fun BluetoothDebuggerDarkPreview() {
    BLEdebuggerTheme(darkTheme = true, dynamicColor = false) { BluetoothDebuggerApp() }
}
