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

class MainActivity : ComponentActivity() {
    private var reader: SamsungSkinTemperatureReader? = null

    /**
     * 测量会话代号。
     *
     * 旧实现里，切换测量模式时旧的 reader 仍可能回调，把**上一次**的结果写进界面。
     * 现在每次启动测量都会自增代号，回调先校验代号，过期的回调直接丢弃。
     */
    private var readerGeneration = 0

    private var result by mutableStateOf(
        MeasurementResult(
            meanCelsius = null,
            ambientCelsius = null,
            spreadCelsius = null,
            sampleCount = 0,
            status = MeasurementStatus.IDLE,
            detail = "准备测量腕部皮肤温度"
        )
    )
    private var mode by mutableStateOf(MeasurementMode.ACCURATE)
    private var history by mutableStateOf(emptyList<TemperatureRecord>())
    private var trendRange by mutableStateOf(TrendRange.DAY)
    private var autoMeasure by mutableStateOf(false)
    private var backgroundInterval by mutableStateOf(BackgroundInterval.EVERY_30_MIN)
    private var wearState by mutableStateOf(WearState(false, false, "未检测"))
    private var permissionMessage by mutableStateOf("")

    private val settings by lazy { getSharedPreferences(SETTINGS, MODE_PRIVATE) }

    /**
     * 皮肤温度必需权限。
     *
     * 官方文档：targetSdk ≤ 35 使用 `BODY_SENSORS`；targetSdk ≥ 36 改用
     * `android.permission.health.READ_SKIN_TEMPERATURE`。
     */
    private val sensorPermission: String
        get() = if (applicationInfo.targetSdkVersion >= 36) {
            "android.permission.health.READ_SKIN_TEMPERATURE"
        } else {
            Manifest.permission.BODY_SENSORS
        }

    /** 运动检测所需权限（缺失时只关闭运动检测，不影响测温）。 */
    private val motionPermission = Manifest.permission.ACTIVITY_RECOGNITION

    private val permissions: Array<String>
        get() = arrayOf(sensorPermission, motionPermission)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // 不依赖回调里的 grants（某些系统不会回调全部权限），
        // 直接复查实际授权状态，避免空 map 被当成“全部已授权”。
        if (checkSelfPermission(sensorPermission) == PackageManager.PERMISSION_GRANTED) {
            startMeasurement()
        } else {
            permissionMessage = "缺少皮肤温度权限。请在手表设置 → 应用 → 权限中授予身体传感器权限"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        autoMeasure = settings.getBoolean(KEY_AUTO_MEASURE, false)
        backgroundInterval = runCatching {
            BackgroundInterval.valueOf(
                settings.getString(KEY_BACKGROUND_INTERVAL, null) ?: BackgroundInterval.EVERY_30_MIN.name
            )
        }.getOrDefault(BackgroundInterval.EVERY_30_MIN)

        savedInstanceState?.let { state ->
            result = MeasurementResult(
                meanCelsius = state.getFloat(KEY_MEAN, Float.NaN).takeUnless { it.isNaN() },
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
            wearState = WearState(
                isWearing = state.getBoolean(KEY_WEARING, false),
                isMoving = state.getBoolean(KEY_MOVING, false),
                reason = state.getString(KEY_WEAR_REASON) ?: ""
            )
        }

        setContent {
            BodyTemperatureWearTheme {
                AppScaffold {
                    TemperatureScreen(
                        result = result,
                        mode = mode,
                        trendRange = trendRange,
                        history = history,
                        wearState = wearState,
                        autoMeasure = autoMeasure,
                        backgroundInterval = backgroundInterval,
                        permissionMessage = permissionMessage,
                        onModeChanged = { mode = it },
                        onTrendRangeChanged = { trendRange = it },
                        onAutoMeasureChanged = ::updateAutoMeasure,
                        onBackgroundIntervalChanged = ::updateBackgroundInterval,
                        onMeasure = ::requestMeasurementPermission
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // 后台任务可能刚刚写入新的记录，回到前台时刷新一次。
        history = TemperatureHistoryStore.load(this)
        syncBackgroundSchedule()
    }

    override fun onStop() {
        super.onStop()
        // 离开前台立刻释放传感器，避免在后台空转耗电；
        // 需要持续采样时由后台自动测量任务负责。
        if (result.status == MeasurementStatus.CONNECTING || result.status == MeasurementStatus.MEASURING) {
            result = result.copy(
                status = MeasurementStatus.IDLE,
                detail = "已离开前台，测量已取消"
            )
        }
        reader?.stop()
        reader = null
        readerGeneration++
    }

    override fun onDestroy() {
        reader?.stop()
        reader = null
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putFloat(KEY_MEAN, result.meanCelsius ?: Float.NaN)
        outState.putFloat(KEY_AMBIENT, result.ambientCelsius ?: Float.NaN)
        outState.putFloat(KEY_SPREAD, result.spreadCelsius ?: Float.NaN)
        outState.putInt(KEY_COUNT, result.sampleCount)
        outState.putString(KEY_STATUS, result.status.name)
        outState.putString(KEY_DETAIL, result.detail)
        outState.putString(KEY_MODE, mode.name)
        outState.putString(KEY_TREND, trendRange.name)
        outState.putBoolean(KEY_WEARING, wearState.isWearing)
        outState.putBoolean(KEY_MOVING, wearState.isMoving)
        outState.putString(KEY_WEAR_REASON, wearState.reason)
        super.onSaveInstanceState(outState)
    }

    private fun requestMeasurementPermission() {
        permissionMessage = ""
        val missing = permissions.filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            startMeasurement()
        } else {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun startMeasurement() {
        reader?.stop()
        val generation = ++readerGeneration
        result = MeasurementResult(
            meanCelsius = null,
            ambientCelsius = null,
            spreadCelsius = null,
            sampleCount = 0,
            status = MeasurementStatus.CONNECTING,
            detail = "正在连接三星 Health Sensor Service…"
        )
        val motionAllowed =
            checkSelfPermission(motionPermission) == PackageManager.PERMISSION_GRANTED
        reader = SamsungSkinTemperatureReader(
            context = this,
            mode = mode,
            onResult = callback@{ newResult ->
                if (generation != readerGeneration) return@callback
                result = newResult
                val value = newResult.meanCelsius
                if (value != null && newResult.status == MeasurementStatus.COMPLETE) {
                    TemperatureHistoryStore.append(this, value)
                    history = TemperatureHistoryStore.load(this)
                }
            },
            onWearStateChanged = callback@{ newState ->
                if (generation != readerGeneration) return@callback
                wearState = newState
            },
            detectMotion = motionAllowed
        ).also { it.start() }
    }

    private fun updateAutoMeasure(enabled: Boolean) {
        autoMeasure = enabled
        settings.edit().putBoolean(KEY_AUTO_MEASURE, enabled).apply()
        BackgroundMeasureScheduler.setEnabled(this, enabled, backgroundInterval)
    }

    private fun updateBackgroundInterval(interval: BackgroundInterval) {
        backgroundInterval = interval
        settings.edit().putString(KEY_BACKGROUND_INTERVAL, interval.name).apply()
        if (autoMeasure) {
            // 间隔变化需要重新排期；JobScheduler 会用同一个 job id 覆盖旧任务。
            BackgroundMeasureScheduler.setEnabled(this, true, interval)
        }
    }

    /**
     * 兜底同步后台任务。
     *
     * 只在“设置了自动测量但任务不存在”时补排，避免每次回到前台都重置周期，
     * 那会让下一次采样被无限推迟。
     */
    private fun syncBackgroundSchedule() {
        val scheduled = BackgroundMeasureScheduler.isScheduled(this)
        if (autoMeasure && !scheduled) {
            BackgroundMeasureScheduler.setEnabled(this, true, backgroundInterval)
        } else if (!autoMeasure && scheduled) {
            BackgroundMeasureScheduler.setEnabled(this, false, backgroundInterval)
        }
    }

    private companion object {
        const val SETTINGS = "temperature_settings"
        const val KEY_AUTO_MEASURE = "auto_measure"
        const val KEY_BACKGROUND_INTERVAL = "background_interval"

        const val KEY_MEAN = "mean"
        const val KEY_AMBIENT = "ambient"
        const val KEY_SPREAD = "spread"
        const val KEY_COUNT = "count"
        const val KEY_STATUS = "status"
        const val KEY_DETAIL = "detail"
        const val KEY_MODE = "mode"
        const val KEY_TREND = "trend"
        const val KEY_WEARING = "wearing"
        const val KEY_MOVING = "moving"
        const val KEY_WEAR_REASON = "wear_reason"
    }
}
