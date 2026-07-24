package com.winds.bledebugger

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Button
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
    val status = when {
        !controller.isBluetoothSupported -> "不支持蓝牙"
        !controller.hasRequiredPermissions() -> "需要蓝牙权限"
        !controller.isBluetoothEnabled -> "蓝牙未开启"
        controller.isScanning -> "正在扫描"
        else -> "可以开始扫描"
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            text = "蓝牙调试助手",
            style = MiuixTheme.textStyles.title1,
            fontWeight = FontWeight.Bold
        )

        SectionCard(title = status, subtitle = "发现 ${devices.size} 台设备 · 已选 ${controller.selectedDevice?.displayName ?: "无"}") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        when {
                            !controller.hasRequiredPermissions() -> onRequestPermissions()
                            !controller.isBluetoothEnabled -> onRequestEnableBluetooth()
                            controller.isScanning -> controller.stopScan()
                            else -> controller.startScan()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColorsPrimary()
                ) {
                    Text(
                        when {
                            !controller.hasRequiredPermissions() -> "授权"
                            !controller.isBluetoothEnabled -> "打开蓝牙"
                            controller.isScanning -> "停止"
                            else -> "扫描设备"
                        }
                    )
                }
                Button(
                    onClick = { controller.clearResults() },
                    modifier = Modifier.weight(1f),
                    enabled = devices.isNotEmpty()
                ) { Text("清空") }
            }
            if (!controller.isLocationEnabled) {
                TextButton(
                    text = "定位服务未开启，点击前往设置",
                    onClick = { controller.openLocationSettings() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColorsPrimary()
                )
            }
        }

        SectionCard(
            title = "设备",
            subtitle = if (devices.isEmpty()) "扫描后，点击设备进行选择" else "点击设备后确认选择"
        ) {
            if (devices.isEmpty()) {
                Text(
                    "暂无设备",
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            } else {
                devices.forEach { device ->
                    DeviceRow(
                        device = device,
                        selected = controller.selectedDevice?.address == device.address,
                        onClick = { pendingDevice = device }
                    )
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
