package org.openwear.temperature

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
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
    researchCount: Int,
    onMeasure: () -> Unit
) {
    val listState = rememberTransformingLazyColumnState()
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
                Button(onClick = onMeasure, modifier = Modifier.fillMaxWidth()) {
                    Text("测量皮肤温度")
                }
            }
            if (result.status !in setOf(MeasurementStatus.IDLE, MeasurementStatus.COMPLETE)) {
                item {
                    Card(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                        Text(result.detail, textAlign = TextAlign.Center)
                    }
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
            item { Text("研究记录", fontWeight = FontWeight.Medium, textAlign = TextAlign.Center) }
            item { Text("本机配对观测：$researchCount 条", textAlign = TextAlign.Center) }
            item { Text("本版本不生成体温估算。", textAlign = TextAlign.Center) }
            item { Spacer(Modifier.height(8.dp)) }
            item { Text("测量提示", fontWeight = FontWeight.Medium, textAlign = TextAlign.Center) }
            item { Text("静坐并贴合佩戴。运动、洗澡或温差环境后等待 30 分钟。", textAlign = TextAlign.Center) }
            item { Text("仅供趋势观察，不用于发烧判断、诊断或治疗。", textAlign = TextAlign.Center) }
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
