package com.winds.bledebugger

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun DebugScreen(controller: BleController) {
    val selectedDevice = controller.selectedDevice
    val supportsGatt = selectedDevice?.supportsGatt == true

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        MetricCard(
            title = "GATT 会话",
            value = controller.gattConnectionState,
            description = if (selectedDevice != null) {
                if (supportsGatt) "已选中设备，可进行连接、服务发现、读写和通知订阅。" else "当前是经典蓝牙设备，GATT 仅适用于 BLE/双模设备。"
            } else {
                "先在设备页扫描并选择一个 BLE 设备。"
            }
        )
        SectionCard(title = "当前目标", subtitle = "调试页已接入设备页所选中的真实蓝牙设备") {
            StatusLine("设备名", selectedDevice?.displayName ?: "未选择")
            StatusLine("MAC", selectedDevice?.address ?: "--")
            StatusLine("RSSI", selectedDevice?.rssi?.let { "$it dBm" } ?: "--")
            StatusLine("类型", selectedDevice?.transport ?: "--")
            StatusLine("服务数", controller.discoveredServiceCount.toString())
            StatusLine("特征数", controller.gattCharacteristics.size.toString())
            StatusLine("通知", if (controller.notifyEnabled) "已订阅" else "未订阅")
            StatusLine("MTU", controller.currentMtu.toString())
            StatusLine("发送特征", controller.writeCharacteristicSummary ?: "--")
            StatusLine("接收特征", controller.notifyCharacteristicSummary ?: "--")
        }
        SectionCard(title = "连接与操作", subtitle = "基础 GATT 调试链路") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { controller.connectSelectedDevice() },
                    modifier = Modifier.weight(1f),
                    enabled = selectedDevice != null && supportsGatt && !controller.isGattConnected,
                    shape = RoundedCornerShape(18.dp)
                ) { Text("连接") }
                OutlinedButton(
                    onClick = { controller.disconnectGatt() },
                    modifier = Modifier.weight(1f),
                    enabled = controller.isGattConnected,
                    shape = RoundedCornerShape(18.dp)
                ) { Text("断开") }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { controller.discoverGattServices() },
                    modifier = Modifier.weight(1f),
                    enabled = controller.isGattConnected,
                    shape = RoundedCornerShape(18.dp)
                ) { Text("发现服务") }
                OutlinedButton(
                    onClick = { controller.readSelectedCharacteristic() },
                    modifier = Modifier.weight(1f),
                    enabled = controller.canReadSelectedCharacteristic,
                    shape = RoundedCornerShape(18.dp)
                ) { Text("读取") }
            }
            OutlinedTextField(
                value = controller.writePayload,
                onValueChange = { controller.writePayload = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(if (controller.sendMode == SendMode.Hex) "写入数据（Hex）" else "写入数据（UTF-8）") },
                placeholder = { Text(if (controller.sendMode == SendMode.Hex) "写入数据（Hex）" else "写入数据（UTF-8）") },
                singleLine = true,
                shape = RoundedCornerShape(18.dp)
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = controller.sendMode == SendMode.Hex,
                    onClick = { controller.sendMode = SendMode.Hex },
                    shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) { Text("Hex") }
                SegmentedButton(
                    selected = controller.sendMode == SendMode.Utf8,
                    onClick = { controller.sendMode = SendMode.Utf8 },
                    shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) { Text("UTF-8") }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { controller.writeSelectedCharacteristic() },
                    modifier = Modifier.weight(1f),
                    enabled = controller.canWriteSelectedCharacteristic,
                    shape = RoundedCornerShape(18.dp)
                ) { Text("写入") }
                OutlinedButton(
                    onClick = { controller.toggleNotifications() },
                    modifier = Modifier.weight(1f),
                    enabled = controller.canToggleSelectedNotifications,
                    shape = RoundedCornerShape(18.dp)
                ) { Text(if (controller.notifyEnabled) "关闭通知" else "订阅通知") }
            }
            StatusLine("浏览特征", controller.selectedCharacteristicSummary ?: "--")
            StatusLine("最近发送", controller.lastTxValue ?: "--")
            StatusLine("最近接收", controller.lastGattValue ?: "--")
            controller.lastWriteError?.let { StatusLine("最近错误", it) }
        }
        SectionCard(title = "特征列表", subtitle = "默认优先选中可读/可写/可通知的特征") {
            if (controller.gattCharacteristics.isEmpty()) {
                Text("暂无已发现的特征", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                controller.gattCharacteristics.take(8).forEach { item ->
                    GattCharacteristicRow(
                        item = item,
                        selected = controller.selectedCharacteristicId == item.id,
                        onClick = { controller.selectCharacteristic(item.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun LogScreen(controller: BleController) {
    val logs = controller.logs

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        MetricCard(
            title = "实时日志",
            value = logs.size.toString().padStart(2, '0'),
            description = "扫描状态、权限变化和设备发现记录会实时写入这里。"
        )
        SectionCard(title = "会话流", subtitle = "最新日志优先显示") {
            if (logs.isEmpty()) {
                Text("暂无日志", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                logs.asReversed().forEach { item ->
                    LogRow(item.time, item.type, item.message)
                }
            }
        }
    }
}

@Composable
fun GattCharacteristicRow(item: GattCharacteristicItem, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else Color.Transparent
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(item.characteristicUuid, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.serviceUuid, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.propertiesLabel, color = MaterialTheme.colorScheme.primary)
        }
    }
}
