package org.openwear.temperature

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray

@Composable
internal fun TemperatureScreen(
    result: MeasurementResult?,
    permissionMessage: String,
    onMeasure: () -> Unit,
    onSaveResearch: (String) -> String
) {
    var experimentEnabled by remember { mutableStateOf(false) }
    var referenceTemperature by remember { mutableStateOf("") }
    var researchMessage by remember { mutableStateOf("") }
    val context = androidx.compose.ui.platform.LocalContext.current
    var localCount by remember { mutableStateOf(readLocalObservationCount(context)) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("腕部皮肤温度", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text("Galaxy Watch 红外传感器读数；不等同于核心体温。", style = MaterialTheme.typography.bodySmall)
        HorizontalDivider()

        if (result?.meanCelsius != null) {
            Text("%.1f °C".format(result.meanCelsius), fontSize = 32.sp, fontWeight = FontWeight.Medium)
            Text("最近 ${result.sampleCount} 个有效样本，范围 %.1f °C".format(result.spreadCelsius ?: 0f))
        } else {
            Text("尚无有效读数", style = MaterialTheme.typography.titleMedium)
        }
        Text(result?.message ?: "测量前请静坐并贴合佩戴；结果仅供参考。", style = MaterialTheme.typography.bodySmall)
        if (permissionMessage.isNotBlank()) Text(permissionMessage, color = MaterialTheme.colorScheme.error)
        Button(onClick = onMeasure, modifier = Modifier.fillMaxWidth()) {
            Text("测量皮肤温度")
        }

        HorizontalDivider()
        Text("测量条件", fontWeight = FontWeight.SemiBold)
        Text("保持佩戴贴合，在安静、室温环境测量。运动、洗澡或进出不同温度环境后，先等待 30 分钟。", style = MaterialTheme.typography.bodySmall)
        Text("本功能不用于发烧判断、诊断或治疗；有发热疑虑请使用合适的体温计。", style = MaterialTheme.typography.bodySmall)

        HorizontalDivider()
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("实验性体温估算研究", fontWeight = FontWeight.SemiBold)
                Text("自愿记录配对数据；当前没有启用估算模型。", style = MaterialTheme.typography.bodySmall)
            }
            Switch(
                checked = experimentEnabled,
                onCheckedChange = {
                    experimentEnabled = it
                    researchMessage = ""
                    localCount = readLocalObservationCount(context = context)
                }
            )
        }

        if (experimentEnabled) {
            Text("研究登记", style = MaterialTheme.typography.titleSmall)
            Text(
                "完成稳定的皮肤温度测量后，输入同一时间用独立体温计测得的参考值。仅保存在本机；未完成独立验证前不会显示预测体温。",
                style = MaterialTheme.typography.bodySmall
            )
            OutlinedTextField(
                value = referenceTemperature,
                onValueChange = { referenceTemperature = it.filter { ch -> ch.isDigit() || ch == '.' } },
                label = { Text("参考体温 (°C)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    researchMessage = onSaveResearch(referenceTemperature)
                    localCount = readLocalObservationCount(context = context)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("保存配对观测")
            }
            Text("本机配对观测：$localCount 条", style = MaterialTheme.typography.bodySmall)
            if (researchMessage.isNotBlank()) Text(researchMessage, style = MaterialTheme.typography.bodySmall)
            Text(
                "研究限制：手腕皮肤温度会受环境、血流、佩戴松紧和活动影响。少量个人记录不能证明对其他时间或人群有效，也不能用于医疗决策。",
                style = MaterialTheme.typography.bodySmall
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

private fun readLocalObservationCount(context: Context): Int = runCatching {
    JSONArray(context.getSharedPreferences("research_observations", Context.MODE_PRIVATE)
        .getString("rows", "[]")).length()
}.getOrDefault(0)
