package com.winds.bledebugger

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun DebugScreen(controller: BleController) {
    val header = parseOptionalHex(controller.quickHeader)
    val body = when (controller.quickBodyMode) {
        SendMode.Hex -> parseHex(controller.quickBody)
        SendMode.Utf8 -> controller.quickBody.toByteArray(Charsets.UTF_8)
    }
    val footer = parseOptionalHex(controller.quickFooter)
    val preview = if (header != null && body != null && footer != null) {
        (header + body + footer).toHexString().ifBlank { "--" }
    } else {
        "HEX 格式错误"
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionCard(title = "数据格式", subtitle = "包头和包尾固定使用 HEX，正文可选 HEX 或 UTF-8") {
            FormatSelector(
                mode = controller.quickBodyMode,
                onModeChange = { controller.quickBodyMode = it }
            )
            TextField(
                value = controller.quickHeader,
                onValueChange = { controller.quickHeader = it.uppercase() },
                modifier = Modifier.fillMaxWidth(),
                label = "包头（HEX，可留空）",
                useLabelAsPlaceholder = true,
                singleLine = true
            )
            TextField(
                value = controller.quickBody,
                onValueChange = {
                    controller.quickBody = if (controller.quickBodyMode == SendMode.Hex) it.uppercase() else it
                },
                modifier = Modifier.fillMaxWidth(),
                label = if (controller.quickBodyMode == SendMode.Hex) "正文（HEX）" else "正文（UTF-8）",
                useLabelAsPlaceholder = true,
                singleLine = true
            )
            TextField(
                value = controller.quickFooter,
                onValueChange = { controller.quickFooter = it.uppercase() },
                modifier = Modifier.fillMaxWidth(),
                label = "包尾（HEX，可留空）",
                useLabelAsPlaceholder = true,
                singleLine = true
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "发送预览",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
                Text(
                    text = preview,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    fontFamily = FontFamily.Monospace
                )
            }
            Button(
                onClick = { controller.sendQuickPayload() },
                modifier = Modifier.fillMaxWidth(),
                enabled = controller.canWriteSelectedCharacteristic && header != null && body != null && footer != null,
                colors = ButtonDefaults.buttonColorsPrimary()
            ) { Text("发送") }
        }
    }
}

@Composable
private fun FormatSelector(mode: SendMode, onModeChange: (SendMode) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MiuixTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(18.dp)
            )
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        FormatButton("HEX", mode == SendMode.Hex, Modifier.weight(1f)) { onModeChange(SendMode.Hex) }
        FormatButton("UTF-8", mode == SendMode.Utf8, Modifier.weight(1f)) { onModeChange(SendMode.Utf8) }
    }
}

@Composable
private fun FormatButton(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = if (selected) ButtonDefaults.buttonColorsPrimary() else ButtonDefaults.buttonColors(),
        cornerRadius = 14.dp
    ) {
        Text(
            text = text,
            color = if (selected) {
                MiuixTheme.colorScheme.onPrimary
            } else {
                MiuixTheme.colorScheme.onSecondaryContainer
            }
        )
    }
}

@Composable
fun IncomingMessageBanner(message: String, modifier: Modifier = Modifier) {
    top.yukonga.miuix.kmp.basic.Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MiuixTheme.colorScheme.surfaceContainer,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("收到消息", fontWeight = FontWeight.Bold)
            Text(message, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun LogScreen(controller: BleController) {
    val logs = controller.logs
    val listState = rememberLazyListState()

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.lastIndex)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "共 ${logs.size} 条",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
            IconActionButton(
                iconRes = R.drawable.ic_delete,
                contentDescription = "清空日志",
                onClick = { controller.clearLogs() },
                enabled = logs.isNotEmpty()
            )
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(18.dp),
            color = MiuixTheme.colorScheme.secondaryContainer
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                if (logs.isEmpty()) {
                    Text(
                        "暂无日志",
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = listState,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(logs) { item ->
                            Text(
                                text = "${item.time}  [${item.type}]  ${item.message}",
                                fontFamily = FontFamily.Monospace,
                                style = MiuixTheme.textStyles.body2
                            )
                        }
                    }
                }
            }
        }
    }
}
