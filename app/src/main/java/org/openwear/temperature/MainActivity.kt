package org.openwear.temperature

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme

class MainActivity : ComponentActivity() {
    private var reader: SamsungSkinTemperatureReader? = null
    private var result by mutableStateOf(
        MeasurementResult(null, null, 0, MeasurementStatus.IDLE, "准备测量腕部皮肤温度")
    )
    private var researchCount by mutableStateOf(0)

    private val permissions: Array<String>
        get() = if (applicationInfo.targetSdkVersion >= 36) {
            arrayOf("com.samsung.android.hardware.sensormanager.permission.READ_ADDITIONAL_HEALTH_DATA")
        } else {
            arrayOf(Manifest.permission.BODY_SENSORS)
        }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.all { it }) startMeasurement()
        else showResult(MeasurementStatus.PERMISSION_DENIED, "系统权限未全部授予。请在手表设置中检查皮肤温度权限")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.let {
            result = MeasurementResult(
                it.getFloat(KEY_MEAN, Float.NaN).takeUnless(Float::isNaN),
                it.getFloat(KEY_SPREAD, Float.NaN).takeUnless(Float::isNaN),
                it.getInt(KEY_COUNT, 0),
                runCatching { MeasurementStatus.valueOf(it.getString(KEY_STATUS).orEmpty()) }
                    .getOrDefault(MeasurementStatus.IDLE),
                it.getString(KEY_DETAIL).orEmpty()
            )
        }
        researchCount = readResearchCount()
        setContent {
            MaterialTheme {
                AppScaffold {
                    TemperatureScreen(
                        result = result,
                        researchCount = researchCount,
                        onMeasure = ::requestMeasurementPermission
                    )
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putFloat(KEY_MEAN, result.meanCelsius ?: Float.NaN)
        outState.putFloat(KEY_SPREAD, result.spreadCelsius ?: Float.NaN)
        outState.putInt(KEY_COUNT, result.sampleCount)
        outState.putString(KEY_STATUS, result.status.name)
        outState.putString(KEY_DETAIL, result.detail)
        super.onSaveInstanceState(outState)
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
        reader?.stop()
        showResult(MeasurementStatus.CONNECTING, "正在连接三星 Health Sensor Service…")
        reader = SamsungSkinTemperatureReader(this) { result = it }.also { it.start() }
    }

    private fun showResult(status: MeasurementStatus, detail: String) {
        result = MeasurementResult(null, null, 0, status, detail)
    }

    private fun readResearchCount(): Int = runCatching {
        org.json.JSONArray(getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_ROWS, "[]")).length()
    }.getOrDefault(0)

    private companion object {
        const val PREFS = "research_observations"
        const val KEY_ROWS = "rows"
        const val KEY_MEAN = "mean"
        const val KEY_SPREAD = "spread"
        const val KEY_COUNT = "count"
        const val KEY_STATUS = "status"
        const val KEY_DETAIL = "detail"
    }
}
