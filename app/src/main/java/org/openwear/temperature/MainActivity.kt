package org.openwear.temperature

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    private var reader: SamsungSkinTemperatureReader? = null
    private var result by mutableStateOf<MeasurementResult?>(null)
    private var permissionMessage by mutableStateOf("")
    private val permissions: Array<String>
        get() = if (Build.VERSION.SDK_INT >= 36) {
            arrayOf("android.permission.health.READ_SKIN_TEMPERATURE")
        } else {
            arrayOf(Manifest.permission.BODY_SENSORS)
        }
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.all { it }) startMeasurement()
        else permissionMessage = "未获得身体传感器或皮肤温度授权。请到系统应用权限中检查授权后重试。"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    TemperatureScreen(
                        result = result,
                        permissionMessage = permissionMessage,
                        onMeasure = ::requestMeasurementPermission,
                        onSaveResearch = ::saveResearchObservation
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        reader?.stop()
        super.onDestroy()
    }

    private fun requestMeasurementPermission() {
        if (permissions.all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }) {
            startMeasurement()
        } else {
            permissionLauncher.launch(permissions)
        }
    }

    private fun startMeasurement() {
        permissionMessage = ""
        result = null
        reader?.stop()
        reader = SamsungSkinTemperatureReader(this) { result = it }.also { it.start() }
    }

    private fun saveResearchObservation(referenceText: String): String {
        val reference = referenceText.toFloatOrNull()
        val skin = result?.meanCelsius
        if (reference == null || reference !in 30f..45f) return "请输入有效的参考温度（30–45°C）。"
        if (skin == null) return "请先完成稳定的皮肤温度测量。"
        return runCatching {
            val prefs = getSharedPreferences("research_observations", MODE_PRIVATE)
            val rows = JSONArray(prefs.getString("rows", "[]"))
            rows.put(
                JSONObject()
                    .put("timeMillis", System.currentTimeMillis())
                    .put("skinCelsius", skin.toDouble())
                    .put("referenceCelsius", reference.toDouble())
                    .put("spreadCelsius", result?.spreadCelsius?.toDouble())
                    .put("samples", result?.sampleCount)
            )
            prefs.edit().putString("rows", rows.toString()).apply()
            "已在本机记录配对观测（${rows.length()} 条）。当前不会据此生成体温估算。"
        }.getOrElse { "保存失败，请重试。" }
    }
}
