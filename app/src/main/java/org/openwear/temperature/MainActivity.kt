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
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    private var reader: SamsungSkinTemperatureReader? = null
    private var result by mutableStateOf(
        MeasurementResult(null, null, 0, MeasurementStatus.IDLE, "准备测量腕部皮肤温度")
    )
    private var mode by mutableStateOf(MeasurementMode.ACCURATE)
    private var history by mutableStateOf(loadHistory())
    private var permissionMessage by mutableStateOf("")

    private val permissions: Array<String>
        get() = arrayOf(Manifest.permission.BODY_SENSORS)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.all { it }) startMeasurement()
        else permissionMessage = "系统权限未全部授予。请在手表设置中检查皮肤温度权限"
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
            mode = runCatching { MeasurementMode.valueOf(it.getString(KEY_MODE).orEmpty()) }
                .getOrDefault(MeasurementMode.ACCURATE)
        }
        setContent {
            MaterialTheme {
                AppScaffold {
                    TemperatureScreen(
                        result = result,
                        mode = mode,
                        history = history,
                        permissionMessage = permissionMessage,
                        onModeChanged = { mode = it },
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
        outState.putString(KEY_MODE, mode.name)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        reader?.stop()
        super.onDestroy()
    }

    private fun requestMeasurementPermission() {
        permissionMessage = ""
        if (permissions.all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }) {
            startMeasurement()
        } else {
            permissionLauncher.launch(permissions)
        }
    }

    private fun startMeasurement() {
        reader?.stop()
        result = MeasurementResult(null, null, 0, MeasurementStatus.CONNECTING, "正在连接三星 Health Sensor Service…")
        reader = SamsungSkinTemperatureReader(this, mode) { r ->
            result = r
            if (r.meanCelsius != null) saveHistory(r.meanCelsius!!)
        }.also { it.start() }
    }

    private fun saveHistory(skin: Float) {
        runCatching {
            val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
            val rows = JSONArray(prefs.getString(KEY_ROWS, "[]"))
            rows.put(
                JSONObject()
                    .put("timeMillis", System.currentTimeMillis())
                    .put("skinCelsius", skin.toDouble())
            )
            while (rows.length() > 20) rows.remove(0)
            prefs.edit().putString(KEY_ROWS, rows.toString()).apply()
            history = loadHistory()
        }
    }

    private fun loadHistory(): List<Pair<Long, Float>> = runCatching {
        val rows = JSONArray(getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_ROWS, "[]"))
        (0 until rows.length()).map { i ->
            val o = rows.getJSONObject(i)
            o.getLong("timeMillis") to o.getDouble("skinCelsius").toFloat()
        }
    }.getOrDefault(emptyList())

    private companion object {
        const val PREFS = "temperature_history"
        const val KEY_ROWS = "rows"
        const val KEY_MEAN = "mean"
        const val KEY_SPREAD = "spread"
        const val KEY_COUNT = "count"
        const val KEY_STATUS = "status"
        const val KEY_DETAIL = "detail"
        const val KEY_MODE = "mode"
    }
}
