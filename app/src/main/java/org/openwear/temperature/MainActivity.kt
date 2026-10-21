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
        MeasurementResult(
            meanCelsius = null,
            objectCelsius = null,
            ambientCelsius = null,
            spreadCelsius = null,
            sampleCount = 0,
            status = MeasurementStatus.IDLE,
            detail = "准备测量腕部皮肤温度"
        )
    )
    private var mode by mutableStateOf(MeasurementMode.ACCURATE)
    private var history by mutableStateOf(loadHistory())
    private var trendRange by mutableStateOf(TrendRange.DAY)
    private var autoMeasure by mutableStateOf(false)
    private var wearState by mutableStateOf(WearState(false, false, "未检测"))
    private var permissionMessage by mutableStateOf("")

    private val permissions: Array<String>
        get() = if (applicationInfo.targetSdkVersion >= 36) {
            arrayOf(
                "android.permission.health.READ_SKIN_TEMPERATURE",
                "com.samsung.android.hardware.sensormanager.permission.READ_ADDITIONAL_HEALTH_DATA",
                Manifest.permission.ACTIVITY_RECOGNITION
            )
        } else {
            arrayOf(
                Manifest.permission.BODY_SENSORS,
                Manifest.permission.ACTIVITY_RECOGNITION
            )
        }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.all { it }) startMeasurement()
        else permissionMessage = "系统权限未全部授予。请在手表设置中检查皮肤温度、身体传感器和活动识别权限"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.let { state ->
            result = MeasurementResult(
                meanCelsius = state.getFloat(KEY_MEAN, Float.NaN).takeUnless { it.isNaN() },
                objectCelsius = state.getFloat(KEY_OBJECT, Float.NaN).takeUnless { it.isNaN() },
                ambientCelsius = state.getFloat(KEY_AMBIENT, Float.NaN).takeUnless { it.isNaN() },
                spreadCelsius = state.getFloat(KEY_SPREAD, Float.NaN).takeUnless { it.isNaN() },
                sampleCount = state.getInt(KEY_COUNT, 0),
                status = runCatching {
                    MeasurementStatus.valueOf(state.getString(KEY_STATUS) ?: MeasurementStatus.IDLE.name)
                }.getOrDefault(MeasurementStatus.IDLE),
                detail = state.getString(KEY_DETAIL) ?: ""
            )
            mode = runCatching {
                MeasurementMode.valueOf(state.getString(KEY_MODE) ?: MeasurementMode.ACCURATE.name)
            }.getOrDefault(MeasurementMode.ACCURATE)
            trendRange = runCatching {
                TrendRange.valueOf(state.getString(KEY_TREND) ?: TrendRange.DAY.name)
            }.getOrDefault(TrendRange.DAY)
            autoMeasure = state.getBoolean(KEY_AUTO_MEASURE, false)
            wearState = WearState(
                isWearing = state.getBoolean(KEY_WEARING, false),
                isMoving = state.getBoolean(KEY_MOVING, false),
                reason = state.getString(KEY_WEAR_REASON) ?: ""
            )
        }
        setContent {
            MaterialTheme {
                AppScaffold {
                    TemperatureScreen(
                        result = result,
                        mode = mode,
                        trendRange = trendRange,
                        history = history,
                        wearState = wearState,
                        autoMeasure = autoMeasure,
                        permissionMessage = permissionMessage,
                        onModeChanged = { mode = it },
                        onTrendRangeChanged = { trendRange = it },
                        onAutoMeasureChanged = { autoMeasure = it },
                        onMeasure = ::requestMeasurementPermission
                    )
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putFloat(KEY_MEAN, result.meanCelsius ?: Float.NaN)
        outState.putFloat(KEY_OBJECT, result.objectCelsius ?: Float.NaN)
        outState.putFloat(KEY_AMBIENT, result.ambientCelsius ?: Float.NaN)
        outState.putFloat(KEY_SPREAD, result.spreadCelsius ?: Float.NaN)
        outState.putInt(KEY_COUNT, result.sampleCount)
        outState.putString(KEY_STATUS, result.status.name)
        outState.putString(KEY_DETAIL, result.detail)
        outState.putString(KEY_MODE, mode.name)
        outState.putString(KEY_TREND, trendRange.name)
        outState.putBoolean(KEY_AUTO_MEASURE, autoMeasure)
        outState.putBoolean(KEY_WEARING, wearState.isWearing)
        outState.putBoolean(KEY_MOVING, wearState.isMoving)
        outState.putString(KEY_WEAR_REASON, wearState.reason)
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
        result = result.copy(
            meanCelsius = null,
            objectCelsius = null,
            ambientCelsius = null,
            spreadCelsius = null,
            sampleCount = 0,
            status = MeasurementStatus.CONNECTING,
            detail = "正在连接三星 Health Sensor Service…"
        )
        reader = SamsungSkinTemperatureReader(this, mode) { newResult ->
            result = newResult
            if (newResult.meanCelsius != null) saveHistory(newResult.meanCelsius)
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
            while (rows.length() > 200) rows.remove(0)
            prefs.edit().putString(KEY_ROWS, rows.toString()).apply()
            history = loadHistory()
        }
    }

    private fun loadHistory(): List<Pair<Long, Float>> = runCatching {
        val rows = JSONArray(getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_ROWS, "[]"))
        (0 until rows.length()).map { index ->
            val row = rows.getJSONObject(index)
            row.getLong("timeMillis") to row.getDouble("skinCelsius").toFloat()
        }
    }.getOrDefault(emptyList())

    private companion object {
        const val PREFS = "temperature_history"
        const val KEY_ROWS = "rows"
        const val KEY_MEAN = "mean"
        const val KEY_OBJECT = "object"
        const val KEY_AMBIENT = "ambient"
        const val KEY_SPREAD = "spread"
        const val KEY_COUNT = "count"
        const val KEY_STATUS = "status"
        const val KEY_DETAIL = "detail"
        const val KEY_MODE = "mode"
        const val KEY_TREND = "trend"
        const val KEY_AUTO_MEASURE = "auto_measure"
        const val KEY_WEARING = "wearing"
        const val KEY_MOVING = "moving"
        const val KEY_WEAR_REASON = "wear_reason"
    }
}
