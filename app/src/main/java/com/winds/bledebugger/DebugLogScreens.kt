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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
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
    val quickHeaderBytes = parseOptionalHex(controller.quickHeader)
    val quickBodyBytes = when (controller.quickBodyMode) {
        SendMode.Hex -> parseHex(controller.quickBody)
        SendMode.Utf8 -> controller.quickBody.toByteArray(Charsets.UTF_8)
    }
    val quickFooterBytes = parseOptionalHex(controller.quickFooter)
    val quickPreview = if (quickHeaderBytes != null && quickBodyBytes != null && quickFooterBytes != null) {
        (quickHeaderBytes + quickBodyBytes + quickFooterBytes).toHexString().ifBlank { "--" }
    } else {
        "格式错误"
    }

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
        SectionCard(title = "连接与状态", subtitle = "连接后会自动发现服务、读取并订阅通知") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { controller.connectSelectedDevice(autoEnableNotify = true) },
                    modifier = Modifier.weight(1f),
                    enabled = selectedDevice != null && supportsGatt && !controller.isGattConnected,
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) { Text("连接") }
                OutlinedButton(
                    onClick = { controller.disconnectGatt() },
                    modifier = Modifier.weight(1f),
                    enabled = controller.isGattConnected,
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    border = ButtonDefaults.outlinedButtonBorder(enabled = controller.isGattConnected).copy(
                        brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outline)
                    )
                ) { Text("断开") }
            }
            StatusLine("浏览特征", controller.selectedCharacteristicSummary ?: "--")
            StatusLine("最近发送", controller.lastTxValue ?: "--")
            StatusLine("最近接收", controller.lastGattValue ?: "--")
            controller.lastWriteError?.let { StatusLine("最近错误", it) }
        }
        SectionCard(title = "直接发送", subtitle = "原始数据直发") {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = controller.sendMode == SendMode.Hex,
                    onClick = { controller.sendMode = SendMode.Hex },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = MaterialTheme.colorScheme.primary,
                        activeContentColor = MaterialTheme.colorScheme.onPrimary,
                        inactiveContainerColor = MaterialTheme.colorScheme.surface,
                        inactiveContentColor = MaterialTheme.colorScheme.onSurface,
                        activeBorderColor = MaterialTheme.colorScheme.primary,
                        inactiveBorderColor = MaterialTheme.colorScheme.outline
                    )
                ) { Text("Hex") }
                SegmentedButton(
                    selected = controller.sendMode == SendMode.Utf8,
                    onClick = { controller.sendMode = SendMode.Utf8 },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = MaterialTheme.colorScheme.primary,
                        activeContentColor = MaterialTheme.colorScheme.onPrimary,
                        inactiveContainerColor = MaterialTheme.colorScheme.surface,
                        inactiveContentColor = MaterialTheme.colorScheme.onSurface,
                        activeBorderColor = MaterialTheme.colorScheme.primary,
                        inactiveBorderColor = MaterialTheme.colorScheme.outline
                    )
                ) { Text("UTF-8") }
            }
            OutlinedTextField(
                value = controller.writePayload,
                onValueChange = { controller.writePayload = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(if (controller.sendMode == SendMode.Hex) "写入数据（Hex）" else "写入数据（UTF-8）") },
                placeholder = { Text(if (controller.sendMode == SendMode.Hex) "写入数据（Hex）" else "写入数据（UTF-8）") },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    cursorColor = MaterialTheme.colorScheme.primary,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )
            Button(
                onClick = { controller.writeSelectedCharacteristic() },
                modifier = Modifier.fillMaxWidth(),
                    enabled = controller.canWriteSelectedCharacteristic,
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) { Text("写入") }
        }
        SectionCard(title = "快捷发送", subtitle = "自定义包头和包尾") {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = controller.quickBodyMode == SendMode.Hex,
                    onClick = { controller.quickBodyMode = SendMode.Hex },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = MaterialTheme.colorScheme.primary,
                        activeContentColor = MaterialTheme.colorScheme.onPrimary,
                        inactiveContainerColor = MaterialTheme.colorScheme.surface,
                        inactiveContentColor = MaterialTheme.colorScheme.onSurface,
                        activeBorderColor = MaterialTheme.colorScheme.primary,
                        inactiveBorderColor = MaterialTheme.colorScheme.outline
                    )
                ) { Text("正文 Hex") }
                SegmentedButton(
                    selected = controller.quickBodyMode == SendMode.Utf8,
                    onClick = { controller.quickBodyMode = SendMode.Utf8 },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = MaterialTheme.colorScheme.primary,
                        activeContentColor = MaterialTheme.colorScheme.onPrimary,
                        inactiveContainerColor = MaterialTheme.colorScheme.surface,
                        inactiveContentColor = MaterialTheme.colorScheme.onSurface,
                        activeBorderColor = MaterialTheme.colorScheme.primary,
                        inactiveBorderColor = MaterialTheme.colorScheme.outline
                    )
                ) { Text("正文 UTF-8") }
            }
            OutlinedTextField(
                value = controller.quickHeader,
                onValueChange = { controller.quickHeader = it.uppercase() },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("包头（Hex，可留空）") },
                placeholder = { Text("AA 55") },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    cursorColor = MaterialTheme.colorScheme.primary,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )
            OutlinedTextField(
                value = controller.quickBody,
                onValueChange = { controller.quickBody = if (controller.quickBodyMode == SendMode.Hex) it.uppercase() else it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(if (controller.quickBodyMode == SendMode.Hex) "正文（Hex）" else "正文（UTF-8）") },
                placeholder = { Text(if (controller.quickBodyMode == SendMode.Hex) "01 03 00 00 00 02" else "010300000002") },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    cursorColor = MaterialTheme.colorScheme.primary,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )
            OutlinedTextField(
                value = controller.quickFooter,
                onValueChange = { controller.quickFooter = it.uppercase() },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("包尾（Hex，可留空）") },
                placeholder = { Text("0D 0A") },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    cursorColor = MaterialTheme.colorScheme.primary,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("组包预览", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(quickPreview, fontWeight = FontWeight.SemiBold)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { controller.fillQuickPayloadIntoWriter() },
                    modifier = Modifier.weight(1f),
                    enabled = controller.canWriteSelectedCharacteristic,
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    border = ButtonDefaults.outlinedButtonBorder(enabled = controller.canWriteSelectedCharacteristic).copy(
                        brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outline)
                    )
                ) { Text("填入直接写入") }
                Button(
                    onClick = { controller.sendQuickPayload() },
                    modifier = Modifier.weight(1f),
                    enabled = controller.canWriteSelectedCharacteristic,
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        contentColor = MaterialTheme.colorScheme.onTertiary,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) { Text("快捷发送") }
            }
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
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface
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
