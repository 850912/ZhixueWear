package org.openwear.temperature

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.HorizontalDivider
import androidx.wear.compose.material3.OutlinedTextField
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Switch
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TextButton
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp

@Composable
internal fun TemperatureScreen(
    result: MeasurementResult,
    mode: MeasurementMode,
    trendRange: TrendRange,
    history: List<Pair<Long, Float>>,
    wearState: WearState,
    autoMeasure: Boolean,
    permissionMessage: String,
    onModeChanged: (MeasurementMode) -> Unit,
    onTrendRangeChanged: (TrendRange) -> Unit,
    onAutoMeasureChanged: (Boolean) -> Unit,
    onMeasure: () -> Unit
) {
    val listState = rememberTransformingLazyColumnState()

    val now = System.currentTimeMillis()
    val filtered = history.filter { (t, _) -> now - t <= trendRange.millis }
    val values = filtered.map { it.second }
    val trendMean = if (values.isNotEmpty()) values.average().toFloat() else null
    val trendMin = values.minOrNull()
    val trendMax = values.maxOrNull()

    val isActiveMeasuring = result.status == MeasurementStatus.CONNECTING ||
        result.status == MeasurementStatus.MEASURING

    ScreenScaffold(
        scrollState = listState,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
                .verticalArrangement(Arrangement.spacedBy(6.dp)),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 大标题：腕部皮肤温度
            Text(
                text = "皮肤温度",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 主读数区域
            Text(
                text = result.meanCelsius?.let { "%.1f°".format(it) } ?: "--°",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )

            SubText(text = "腕部皮肤温度", color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(modifier = Modifier.height(8.dp))

            // 环境温度与物体温度小卡片
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TempChip(
                    label = "环境",
                    value = result.ambientCelsius?.let { "%.1f°".format(it) } ?: "--°",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
                TempChip(
                    label = "物体",
                    value = result.objectCelsius?.let { "%.1f°".format(it) } ?: "--°",
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 状态
            Text(
                text = statusLabel(result.status),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = statusColor(result.status)
            )

            Spacer(modifier = Modifier.height(4.dp))

            if (result.meanCelsius != null) {
                Text(
                    text = "${result.sampleCount} 个样本 · 波动 ${"%.1f°".format(result.spreadCelsius ?: 0f)}",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 测量按钮
            Button(
                onClick = onMeasure,
                modifier = Modifier.fillMaxWidth(),
                enabled = wearState.isWearing && !wearState.isMoving && result.status != MeasurementStatus.MEASURING
            ) {
                Text(
                    text = when {
                        wearState.isMoving -> "请保持静息后测量"
                        !wearState.isWearing -> "请贴合佩戴手表"
                        isActiveMeasuring -> "重新${mode.label}"
                        else -> "开始${mode.label}"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 预计用时
            Text(
                text = "预计用时：${mode.estimatedSeconds} 秒",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 测量模式切换
            Text(
                text = "测量模式",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
            RowModeButtons(mode = mode, onModeChanged = onModeChanged)

            Spacer(modifier = Modifier.height(8.dp))

            // 佩戴状态
            WearStatusCard(wearState = wearState)

            Spacer(modifier = Modifier.height(8.dp))

            // 个人趋势
            ListHeader(
                modifier = Modifier.fillMaxWidth(),
                title = {
                    Text(
                        text = "个人趋势 · ${trendRange.label}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                }
            )

            itemRow(
                label = "样本数",
                value = filtered.size.toString(),
                unit = "条"
            )
            itemRow(
                label = "趋势均值",
                value = trendMean?.let { "%.1f°".format(it) } ?: "--°",
                unit = ""
            )
            itemRow(
                label = "范围",
                value = if (trendMin != null && trendMax != null) "${"%.1f°".format(trendMin)} – ${"%.1f°".format(trendMax)}" else "--°",
                unit = ""
            )

            if (filtered.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "最近记录（最近 ${filtered.size} 条）",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                filtered.takeLast(5).reversed().forEach { (time, value) ->
                    itemRow(
                        label = formatTime(time),
                        value = "%.1f°".format(value),
                        unit = ""
                    )
                }
            } else {
                Text(
                    text = "该时段暂无历史记录",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 测量提示
            Text(
                text = "测量提示",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
            Text(
                text = "静坐、贴合佩戴；运动、洗澡或温差环境后等待 30 分钟。",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 自动测量开关
            Switch(
                checked = autoMeasure,
                onCheckedChange = { onAutoMeasureChanged(it) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "静息自动测量",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "手表贴合且静息时自动测量",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 免责声明
            Text(
                text = "重要：腕部皮肤温度 ≠ 核心体温。仅供趋势观察，不用于发烧判断、诊断或治疗。",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyExtraSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun TempChip(label: String, value: String, color: Color) {
    Card(
        modifier = Modifier.weight(1f),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = color
            )
        }
    }
}

@Composable
private fun itemRow(label: String, value: String, unit: String = "") {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            if (unit.isNotEmpty()) {
                Text(text = unit, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun RowModeButtons(mode: MeasurementMode, onModeChanged: (MeasurementMode) -> Unit) {
    androidx.compose.foundation.layout.Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        MeasurementMode.values().forEach { m ->
            Button(
                onClick = { onModeChanged(m) },
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = if (m == mode) "✓ ${m.label}" else m.label,
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun RowRangeButtons(range: TrendRange, onRangeChanged: (TrendRange) -> Unit) {
    androidx.compose.foundation.layout.Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        TrendRange.values().forEach { r ->
            Button(
                onClick = { onRangeChanged(r) },
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = if (r == range) "✓ ${r.label}" else r.label,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }
    }
}

private fun statusLabel(status: MeasurementStatus): String = when (status) {
    MeasurementStatus.IDLE -> "准备就绪"
    MeasurementStatus.CONNECTING -> "正在连接三星服务"
    MeasurementStatus.MEASURING -> "服务已连接 · 等待读数"
    MeasurementStatus.COMPLETE -> "读数稳定"
    MeasurementStatus.UNSTABLE -> "读数不稳定"
    MeasurementStatus.SERVICE_MISSING -> "未安装健康平台"
    MeasurementStatus.SERVICE_OLD -> "健康平台需更新"
    MeasurementStatus.CONNECTION_FAILED -> "服务连接失败"
    MeasurementStatus.UNSUPPORTED -> "此手表不支持"
    MeasurementStatus.PERMISSION_DENIED -> "传感器权限未授予"
    MeasurementStatus.SDK_POLICY -> "平台策略未允许"
    MeasurementStatus.TIMEOUT -> "测量超时"
    MeasurementStatus.ERROR -> "测量错误"
    MeasurementStatus.NOT_WEARING -> "未佩戴手表"
    MeasurementStatus.MOVING -> "手腕运动中"
}

private fun statusColor(status: MeasurementStatus): Color {
    return when (status) {
        MeasurementStatus.IDLE -> MaterialTheme.colorScheme.onSurfaceVariant
        MeasurementStatus.CONNECTING -> MaterialTheme.colorScheme.primary
        MeasurementStatus.MEASURING -> MaterialTheme.colorScheme.primary
        MeasurementStatus.COMPLETE -> MaterialTheme.colorScheme.primaryContainer
        MeasurementStatus.UNSTABLE -> MaterialTheme.colorScheme.warningContainer
        MeasurementStatus.SERVICE_MISSING -> MaterialTheme.colorScheme.errorContainer
        MeasurementStatus.SERVICE_OLD -> MaterialTheme.colorScheme.errorContainer
        MeasurementStatus.CONNECTION_FAILED -> MaterialTheme.colorScheme.errorContainer
        MeasurementStatus.UNSUPPORTED -> MaterialTheme.colorScheme.errorContainer
        MeasurementStatus.PERMISSION_DENIED -> MaterialTheme.colorScheme.errorContainer
        MeasurementStatus.SDK_POLICY -> MaterialTheme.colorScheme.errorContainer
        MeasurementStatus.TIMEOUT -> MaterialTheme.colorScheme.warningContainer
        MeasurementStatus.ERROR -> MaterialTheme.colorScheme.errorContainer
        MeasurementStatus.NOT_WEARING -> MaterialTheme.colorScheme.warningContainer
        MeasurementStatus.MOVING -> MaterialTheme.colorScheme.warningContainer
    }
}

private fun formatTime(millis: Long): String {
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = millis }
    return "%02d:%02d".format(
        cal.get(java.util.Calendar.HOUR_OF_DAY),
        cal.get(java.util.Calendar.MINUTE)
    )
}
