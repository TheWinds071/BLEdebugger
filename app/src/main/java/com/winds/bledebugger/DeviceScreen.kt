package com.winds.bledebugger

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.extra.SuperDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun DeviceScreen(
    controller: BleController,
    onRequestPermissions: () -> Unit,
    onRequestEnableBluetooth: () -> Unit
) {
    val devices = controller.scanResults
    var pendingDevice by remember { mutableStateOf<BleScanItem?>(null) }
    var showUnnamedDevices by rememberSaveable { mutableStateOf(false) }
    val visibleDevices = if (showUnnamedDevices) devices else devices.filter { !it.name.isNullOrBlank() }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("设备", style = MiuixTheme.textStyles.title2, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconActionButton(
                    iconRes = R.drawable.ic_visibility,
                    contentDescription = if (showUnnamedDevices) "隐藏未命名设备" else "显示未命名设备",
                    selected = showUnnamedDevices,
                    onClick = { showUnnamedDevices = !showUnnamedDevices }
                )
                IconActionButton(
                    iconRes = R.drawable.ic_refresh,
                    contentDescription = "刷新设备",
                    selected = controller.isScanning,
                    onClick = {
                        when {
                            !controller.hasRequiredPermissions() -> onRequestPermissions()
                            !controller.isBluetoothEnabled -> onRequestEnableBluetooth()
                            else -> {
                                if (controller.isScanning) controller.stopScan()
                                controller.clearResults()
                                controller.startScan()
                            }
                        }
                    }
                )
            }
        }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (visibleDevices.isEmpty()) {
                Text(
                    text = when {
                        !controller.isBluetoothSupported -> "当前设备不支持蓝牙"
                        !controller.hasRequiredPermissions() -> "点击右上角刷新并授权蓝牙"
                        !controller.isBluetoothEnabled -> "点击右上角刷新并开启蓝牙"
                        controller.isScanning -> "正在搜索设备…"
                        devices.isNotEmpty() -> "未发现有名称的设备"
                        else -> "点击右上角刷新设备"
                    },
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.body2
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(visibleDevices, key = { it.address }) { device ->
                        DeviceRow(
                            device = device,
                            selected = controller.selectedDevice?.address == device.address,
                            onClick = { pendingDevice = device }
                        )
                    }
                }
            }
        }
    }

    val device = pendingDevice
    SuperDialog(
        title = "选择设备",
        summary = device?.let { "使用 ${it.displayName} 进行蓝牙通信？" },
        show = device != null,
        onDismissRequest = { pendingDevice = null }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TextButton(
                text = "取消",
                onClick = { pendingDevice = null },
                modifier = Modifier.weight(1f)
            )
            TextButton(
                text = "选择",
                onClick = {
                    device?.let(controller::selectDevice)
                    pendingDevice = null
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.textButtonColorsPrimary()
            )
        }
    }
}

@Composable
fun DeviceRow(device: BleScanItem, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) {
            MiuixTheme.colorScheme.primaryContainer
        } else {
            MiuixTheme.colorScheme.secondaryContainer
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = device.displayName,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${device.transport} · ${device.address}",
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.body2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Column(
                modifier = Modifier.requiredWidth(82.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = device.rssiLabel,
                    color = MiuixTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.End
                )
                Text(
                    text = if (selected) "已选择" else if (device.isBonded) "已配对" else "可用",
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.body2,
                    textAlign = TextAlign.End
                )
            }
        }
    }
}
