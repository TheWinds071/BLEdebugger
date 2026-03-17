package com.winds.bledebugger

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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

@Composable
fun DeviceScreen(
    controller: BleController,
    onRequestPermissions: () -> Unit,
    onRequestEnableBluetooth: () -> Unit
) {
    val devices = controller.scanResults
    var pendingDevice by remember { mutableStateOf<BleScanItem?>(null) }
    val status = when {
        !controller.isBluetoothSupported -> "设备不支持蓝牙"
        !controller.hasRequiredPermissions() -> "等待权限"
        !controller.isBluetoothEnabled -> "蓝牙已关闭"
        controller.isScanning -> "扫描中"
        else -> "已就绪"
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            text = "蓝牙调试助手",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        DeviceOverviewStrip(
            deviceCount = devices.size,
            status = status,
            pairedCount = devices.count { it.isBonded }
        )
        SectionCard(title = "扫描控制", subtitle = "混合扫描 BLE 与经典蓝牙，支持权限与蓝牙状态检测") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!controller.hasRequiredPermissions()) {
                    Button(
                        onClick = onRequestPermissions,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(18.dp)
                    ) { Text("授权蓝牙") }
                } else if (!controller.isBluetoothEnabled) {
                    Button(
                        onClick = onRequestEnableBluetooth,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(18.dp)
                    ) { Text("打开蓝牙") }
                } else {
                    Button(
                        onClick = {
                            if (controller.isScanning) controller.stopScan() else controller.startScan()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (controller.isScanning) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(if (controller.isScanning) "停止扫描" else "开始扫描")
                    }
                }
                OutlinedButton(
                    onClick = { controller.clearResults() },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(18.dp)
                ) { Text("清空列表") }
            }
            if (!controller.isLocationEnabled) {
                OutlinedButton(
                    onClick = { controller.openLocationSettings() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp)
                ) { Text("打开定位服务") }
            }
            StatusLine("权限", if (controller.hasRequiredPermissions()) "已授权" else "未授权")
            StatusLine("蓝牙", if (controller.isBluetoothEnabled) "已开启" else "未开启")
            StatusLine("定位", if (controller.isLocationEnabled) "已开启" else "未开启")
            StatusLine("扫描器", if (controller.isScannerAvailable) "可用" else "不可用")
            StatusLine("经典搜索", if (controller.isClassicDiscovering) "进行中" else "未进行")
            StatusLine("BLE 扫描", if (controller.isScanning) "进行中" else "未进行")
            StatusLine("配对设备", controller.bondedDeviceCount.toString())
            StatusLine("新增设备", controller.discoveryDeviceCount.toString())
            StatusLine("最后发现", controller.lastDiscoveryTime ?: "--")
            StatusLine("选中设备", controller.selectedDevice?.displayName ?: "未选择")
        }
        SectionCard(
            title = "设备列表",
            subtitle = if (devices.isEmpty()) "已配对设备、BLE 设备和经典蓝牙可发现设备都会显示在这里" else "点击设备可选中，用于调试页和日志页"
        ) {
            if (devices.isEmpty()) {
                Text("暂无设备", color = MaterialTheme.colorScheme.onSurfaceVariant)
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

    pendingDevice?.let { device ->
        AlertDialog(
            onDismissRequest = { pendingDevice = null },
            title = { Text("连接设备") },
            text = { Text("是否连接 ${device.displayName}？") },
            confirmButton = {
                Button(
                    onClick = {
                        controller.selectDevice(device)
                        pendingDevice = null
                    }
                ) { Text("连接") }
            },
            dismissButton = {
                OutlinedButton(onClick = { pendingDevice = null }) { Text("取消") }
            }
        )
    }
}

@Composable
fun DeviceOverviewStrip(deviceCount: Int, status: String, pairedCount: Int) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.62f),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("附近设备", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(status, fontWeight = FontWeight.SemiBold)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    deviceCount.toString().padStart(2, '0'),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text("已配对 $pairedCount", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun DeviceRow(device: BleScanItem, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else androidx.compose.ui.graphics.Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = device.displayName,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${device.transport} · ${device.address}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Column(
                modifier = Modifier.requiredWidth(92.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = device.rssiLabel,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    textAlign = TextAlign.End
                )
                Text(
                    text = when {
                        selected -> "已选中"
                        device.isBonded -> "已配对"
                        device.isConnectable -> "可连接"
                        else -> "广播中"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End
                )
            }
        }
    }
}
