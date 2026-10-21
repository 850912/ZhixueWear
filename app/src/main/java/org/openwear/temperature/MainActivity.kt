package org.openwear.temperature

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Surface
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    private var reader: SamsungSkinTemperatureReader? = null
    private var result by mutableStateOf(
        MeasurementResult(null, null, null, 0, MeasurementStatus.IDLE, "准备测量腕部皮肤温度")
    )
    private var mode by mutableStateOf(MeasurementMode.ACCURATE)
    private var history by mutableStateOf(loadHistory())
    private var trendRange by mutableStateOf(TrendRange.DAY)
    private var autoMeasure by mutableStateOf(false)
    private var wearState by mutableStateOf(WearState(false, false, "未检测"))
    private var permissionMessage by mutableStateOf("")

    private val permissions: Array<String>
        get() = if (android.os.Build.VERSION.SDK_INT >= 36) {
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
        if (grants.values.all { it }) startAutoMeasure()
        else permissionMessage = "系统权限未全部授予。请在手表设置中检查皮肤温度、身体传感器和活动识别权限"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.let {
            result = MeasurementResult(
                it.getFloat(KEY_MEAN, Float.NaN).takeUnless(Float::isNaN),
                it.getFloat(KEY_OBJECT, Float.NaN).takeUnless(Float::isNaN),
                it.getFloat(KEY_AMBIENT, Float.NaN).takeUnless(Float::isNaN),
                it.getInt(KEY_COUNT, 0),
                runCatching { MeasurementStatus.valueOf(it.getString(KEY_STATUS
                    ?: MeasurementStatus.IDLE.name)) }.getOrDefault(MeasurementStatus.IDLE),
                it.getString(KEY_DETAIL).orEmpty()
            )
            mode = runCatching { MeasurementMode.valueOf(it.getString(KEY_MODE
                ?: MeasurementMode.ACCURATE.name)) }.getOrDefault(MeasurementMode.ACCURATE)
            trendRange = runCatching { TrendRange.valueOf(it.getString(KEY_TREND
                ?: TrendRange.DAY.name)) }.getOrDefault(TrendRange.DAY)
            autoMeasure = it.getBoolean(KEY_AUTO_MEASURE, false)
            wearState = WearState(
                it.getBoolean(KEY_WEARING, false),
                it.getBoolean(KEY_MOVING, false),
                it.getString(KEY_WEAR_REASON).orEmpty()
            )
        }
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
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
            startAutoMeasure()
        } else {
            permissionLauncher.launch(permissions)
        }
    }

    private fun startAutoMeasure() {
        reader?.stop()
        result = MeasurementResult(null, null, null, 0, MeasurementStatus.IDLE, "准备测量腕部皮肤温度")
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
            while (rows.length() > 200) rows.remove(0)
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
        const val KEY_OBJECT = "object"
        const val KEY_AMBIENT = "ambient"
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
