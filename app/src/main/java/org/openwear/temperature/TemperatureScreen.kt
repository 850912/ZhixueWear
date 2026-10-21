package org.openwear.temperature

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text

@Composable
internal fun TemperatureScreen(
    result: MeasurementResult,
    mode: MeasurementMode,
    trendRange: TrendRange,
    history: List<Pair<Long, Float>>,
    permissionMessage: String,
    onModeChanged: (MeasurementMode) -> Unit,
    onTrendRangeChanged: (TrendRange) -> Unit,
    onMeasure: () -> Unit
) {
    val listState = rememberTransformingLazyColumnState()
    val now = System.currentTimeMillis()
    val filtered = history.filter { (t, _) -> now - t <= trendRange.millis }
    val values = filtered.map { it.second }
    val trendMean = if (values.isNotEmpty()) values.average().toFloat() else null
    val trendMin = values.minOrNull()
    val trendMax = values.maxOrNull()

    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            item {
                Text("皮肤温度", fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
            }
            item {
                Text(
                    result.meanCelsius?.let { "%.1f°".format(it) } ?: "--°",
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
            item { Text("腕部读数 · 非核心体温", textAlign = TextAlign.Center) }
            item { Text(statusLabel(result.status), textAlign = TextAlign.Center) }
            if (result.meanCelsius != null) {
                item {
                    Text(
                        "${result.sampleCount} 个样本 · 波动 %.1f°".format(result.spreadCelsius ?: 0f),
                        textAlign = TextAlign.Center
                    )
                }
            }
            item {
                Text("测量模式", fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
            }
            item {
                RowModeButtons(mode, onModeChanged)
            }
            item {
                Text(
                    "预计用时：${mode.estimatedSeconds} 秒",
                    textAlign = TextAlign.Center
                )
            }
            item {
                Button(onClick = onMeasure, modifier = Modifier.fillMaxWidth()) {
                    Text("开始${mode.label}")
                }
            }
            if (permissionMessage.isNotBlank()) {
                item {
                    Card(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                        Text(permissionMessage, textAlign = TextAlign.Center)
                    }
                }
            }
            if (result.status !in setOf(MeasurementStatus.IDLE, MeasurementStatus.COMPLETE)) {
                item {
                    Card(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                        Text(result.detail, textAlign = TextAlign.Center)
                    }
                }
            }
            item {
                Text("个人趋势 · ${trendRange.label}", fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
            }
            item {
                RowRangeButtons(trendRange, onTrendRangeChanged)
            }
            if (filtered.isNotEmpty()) {
                item {
                    Text(
                        "样本 ${filtered.size} · 均值 ${"%.1f°".format(trendMean)} · 范围 ${"%.1f".format(trendMin)}–${"%.1f°".format(trendMax)}",
                        textAlign = TextAlign.Center
                    )
                }
                filtered.takeLast(8).reversed().forEach { (time, value) ->
                    item {
                        Text(
                            "${formatTime(time)}  ${"%.1f°".format(value)}",
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                item { Text("该时段暂无历史记录", textAlign = TextAlign.Center) }
            }
            item {
                Text("静坐、贴合佩戴；运动、洗澡或温差环境后等待 30 分钟。", textAlign = TextAlign.Center)
            }
            item {
                Text("仅供趋势观察，不用于发烧判断、诊断或治疗。", textAlign = TextAlign.Center)
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
                Text(if (m == mode) "✓ ${m.label}" else m.label)
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
                Text(if (r == range) "✓ ${r.label}" else r.label)
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
}

private fun formatTime(millis: Long): String {
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = millis }
    return "%02d:%02d".format(cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE))
}
