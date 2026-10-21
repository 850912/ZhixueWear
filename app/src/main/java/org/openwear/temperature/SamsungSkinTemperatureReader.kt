package org.openwear.temperature

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.samsung.android.service.health.tracking.ConnectionListener
import com.samsung.android.service.health.tracking.DataType
import com.samsung.android.service.health.tracking.HealthTracker
import com.samsung.android.service.health.tracking.HealthTrackerException
import com.samsung.android.service.health.tracking.HealthTrackingService
import com.samsung.android.service.health.tracking.data.DataPoint
import com.samsung.android.service.health.tracking.data.HealthTrackerType
import com.samsung.android.service.health.tracking.data.ValueKey

internal data class TemperatureSample(
    val skinCelsius: Float,
    val ambientCelsius: Float,
    val objectCelsius: Float,
    val timestampMillis: Long
)

internal data class MotionSample(
    val x: Float,
    val y: Float,
    val z: Float,
    val timestampMillis: Long
)

internal data class WearState(
    val isWearing: Boolean,
    val isMoving: Boolean,
    val reason: String
)

internal data class MeasurementResult(
    val skinCelsius: Float?,
    val objectCelsius: Float?,
    val ambientCelsius: Float?,
    val spreadCelsius: Float?,
    val sampleCount: Int,
    val status: MeasurementStatus,
    val detail: String
)

internal enum class MeasurementStatus {
    IDLE,
    CONNECTING,
    MEASURING,
    COMPLETE,
    UNSTABLE,
    SERVICE_MISSING,
    SERVICE_OLD,
    CONNECTION_FAILED,
    UNSUPPORTED,
    PERMISSION_DENIED,
    SDK_POLICY,
    TIMEOUT,
    ERROR,
    NOT_WEARING,
    MOVING
}

internal enum class MeasurementMode(val label: String, val estimatedSeconds: Int) {
    QUICK("快速测量", 25),
    ACCURATE("准确测量", 105)
}

internal class SamsungSkinTemperatureReader(
    context: Context,
    private val mode: MeasurementMode,
    private val onResult: (MeasurementResult) -> Unit
) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val samples = mutableListOf<TemperatureSample>()
    private val motionSamples = mutableListOf<MotionSample>()
    private var service: HealthTrackingService? = null
    private var skinTracker: HealthTracker? = null
    private var motionTracker: HealthTracker? = null
    private var timeout: Runnable? = null
    private var autoMeasureTimeout: Runnable? = null
    private var active = false
    private var lastWearState = WearState(false, false, "未检测")

    // 佩戴检测：皮肤温度低于此值且低于环境温度+2°C，判定为未佩戴
    private const val WEAR_THRESHOLD_C = 31.0f
    private const val SKIN_ABOVE_AMBIENT_MIN = 2.0f

    // 运动检测：加速度变化超过此值判定为运动
    private const val MOTION_THRESHOLD = 0.15f

    // 电量优化：采样间隔（毫秒），避免过于频繁唤醒 CPU
    private const val SAMPLE_INTERVAL_MS = 2000L

    private var lastSampleTime = 0L

    fun start() {
        stop()
        samples.clear()
        motionSamples.clear()
        lastSampleTime = 0
        active = true
        emit(MeasurementStatus.CONNECTING, "正在连接三星 Health Sensor Service…")
        timeout = Runnable {
            finish(MeasurementStatus.TIMEOUT, "${mode.estimatedSeconds} 秒内未收到稳定皮肤温度样本。请确认手表贴合手腕后重试")
        }.also { mainHandler.postDelayed(it, TemperatureQuality.timeoutMs(mode)) }
        try {
            service = HealthTrackingService(connectionListener, appContext).also { it.connectService() }
        } catch (error: SecurityException) {
            finish(MeasurementStatus.PERMISSION_DENIED, "系统拒绝访问。请检查皮肤温度/身体传感器权限")
        } catch (error: Throwable) {
            finish(MeasurementStatus.CONNECTION_FAILED, "SDK 初始化失败：${error.javaClass.simpleName}")
        }
    }

    fun stop() {
        active = false
        timeout?.let(mainHandler::removeCallbacks)
        timeout = null
        autoMeasureTimeout?.let(mainHandler::removeCallbacks)
        autoMeasureTimeout = null
        runCatching { skinTracker?.unsetEventListener() }
        skinTracker = null
        runCatching { motionTracker?.unsetEventListener() }
        motionTracker = null
        runCatching { service?.disconnectService() }
        service = null
    }

    fun startAutoMeasure() {
        // 自动测量前先等待佩戴稳定
        timeout = Runnable {
            if (!lastWearState.isWearing) {
                finish(MeasurementStatus.NOT_WEARING, "未检测到手表佩戴，请确保手表贴合手腕")
            } else if (lastWearState.isMoving) {
                finish(MeasurementStatus.MOVING, "检测到手腕运动，请保持静息后重试")
            } else {
                start()
            }
        }.also { mainHandler.postDelayed(it, 5_000L) }
    }

    private val connectionListener = object : ConnectionListener {
        override fun onConnectionSuccess() {
            mainHandler.post {
                if (!active) return@post
                val current = service ?: return@post
                val supported = runCatching { current.trackingCapability.supportHealthTrackerTypes }
                    .getOrElse {
                        finish(MeasurementStatus.ERROR, "读取传感器能力失败：${it.javaClass.simpleName}")
                        return@post
                    }
                // 检查皮肤温度 tracker（按需模式）
                if (!supported.contains(HealthTrackerType.SKIN_TEMPERATURE_ON_DEMAND)) {
                    finish(MeasurementStatus.UNSUPPORTED, "Health Platform 已连接，但当前手表未提供按需皮肤温度传感器")
                    return@post
                }
                try {
                    skinTracker = current.getHealthTracker(HealthTrackerType.SKIN_TEMPERATURE_ON_DEMAND)
                    skinTracker?.setEventListener(skinTrackerListener)
                        ?: finish(MeasurementStatus.ERROR, "SDK 未返回皮肤温度 tracker")
                    if (active) emit(MeasurementStatus.MEASURING, "传感器已启动，等待有效样本…")
                } catch (error: SecurityException) {
                    finish(MeasurementStatus.PERMISSION_DENIED, "三星 Health Sensor SDK 权限被拒绝")
                } catch (error: UnsupportedOperationException) {
                    finish(MeasurementStatus.UNSUPPORTED, error.message ?: "当前设备不支持按需皮肤温度")
                } catch (error: Throwable) {
                    finish(MeasurementStatus.ERROR, "创建温度 tracker 失败：${error.message ?: error.javaClass.simpleName}")
                }
            }
        }

        override fun onConnectionEnded() {
            mainHandler.post {
                if (active) finish(MeasurementStatus.CONNECTION_FAILED, "三星 Health Sensor Service 已断开")
            }
        }

        override fun onConnectionFailed(exception: HealthTrackerException) {
            mainHandler.post {
                val code = exception.errorCode
                val status = when (code) {
                    HealthTrackerException.PACKAGE_NOT_INSTALLED -> MeasurementStatus.SERVICE_MISSING
                    HealthTrackerException.OLD_PLATFORM_VERSION -> MeasurementStatus.SERVICE_OLD
                    else -> MeasurementStatus.CONNECTION_FAILED
                }
                val hint = when (status) {
                    MeasurementStatus.SERVICE_MISSING -> "未安装三星 Health Platform / Sensor Service"
                    MeasurementStatus.SERVICE_OLD -> "Health Platform 版本过旧，请从 Galaxy Store 更新"
                    else -> "连接失败（SDK 错误码 $code）：${exception.message ?: "请确认设备和 Health Platform"}"
                }
                finish(status, hint)
            }
        }
    }

    private val skinTrackerListener = object : HealthTracker.TrackerEventListener {
        override fun onDataReceived(dataPoints: List<DataPoint>) {
            mainHandler.post {
                if (!active) return@post
                val now = System.currentTimeMillis()
                // 电量优化：限制采样频率，避免过于频繁处理
                if (now - lastSampleTime < SAMPLE_INTERVAL_MS) return@post
                lastSampleTime = now

                for (point in dataPoints) {
                    val skin = point.getValue(ValueKey.SkinTemperatureSet.OBJECT_TEMPERATURE)
                    val ambient = point.getValue(ValueKey.SkinTemperatureSet.AMBIENT_TEMPERATURE)
                    val objectTemp = point.getValue(ValueKey.SkinTemperatureSet.OBJECT_TEMPERATURE)
                    val sampleStatus = point.getValue(ValueKey.SkinTemperatureSet.STATUS)
                    if (sampleStatus != null && sampleStatus != 0) {
                        finish(MeasurementStatus.ERROR, "三星传感器报告温度数据错误（状态 $sampleStatus），请清洁传感器并确保与皮肤接触")
                        return@post
                    }
                    if (skin != null && ambient != null && TemperatureQuality.isValidSample(skin, ambient)) {
                        samples += TemperatureSample(skin, ambient, objectTemp ?: skin, point.timestamp)
                        lastWearState = lastWearState.copy(isWearing = true)
                    } else if (skin != null && skin < TemperatureQuality.MIN_SKIN_CELSIUS) {
                        lastWearState = lastWearState.copy(isWearing = false, reason = "皮肤温度过低，可能未佩戴")
                    }
                }
                TemperatureQuality.summarize(samples, mode)?.let { summary ->
                    finish(summary.status, summary.detail, summary.meanCelsius, summary.spreadCelsius)
                }
                if (samples.size >= TemperatureQuality.MAX_SAMPLES) {
                    finish(MeasurementStatus.UNSTABLE, "收到样本但波动仍较大；请静坐、贴合佩戴后重试")
                }
            }
        }

        override fun onFlushCompleted() = Unit

        override fun onError(error: HealthTracker.TrackerError) {
            mainHandler.post {
                val status = when (error) {
                    HealthTracker.TrackerError.PERMISSION_ERROR -> MeasurementStatus.PERMISSION_DENIED
                    HealthTracker.TrackerError.SDK_POLICY_ERROR -> MeasurementStatus.SDK_POLICY
                    else -> MeasurementStatus.ERROR
                }
                val hint = when (status) {
                    MeasurementStatus.PERMISSION_DENIED -> "三星传感器授权被拒绝。请检查系统权限并重新授权"
                    MeasurementStatus.SDK_POLICY -> "三星 SDK 策略拒绝此请求；请确认已加入健康研究并启用平台开发者模式"
                    else -> "温度 tracker 错误：$error"
                }
                finish(status, hint)
            }
        }
    }

    // 运动检测 tracker（仅在测量期间启用，节省电量）
    private val motionTrackerListener = object : HealthTracker.TrackerEventListener {
        override fun onDataReceived(dataPoints: List<DataPoint>) {
            mainHandler.post {
                if (!active) return@post
                for (point in dataPoints) {
                    val x = point.getValue(ValueKey.AccelerometerSet.ACCELEROMETER_X)
                    val y = point.getValue(ValueKey.AccelerometerSet.ACCELEROMETER_Y)
                    val z = point.getValue(ValueKey.AccelerometerSet.ACCELEROMETER_Z)
                    if (x != null && y != null && z != null) {
                        motionSamples += MotionSample(x, y, z, point.timestamp)
                        if (motionSamples.size > 20) motionSamples.removeAt(0)
                        updateMotionState()
                    }
                }
            }
        }

        override fun onFlushCompleted() = Unit

        override fun onError(error: HealthTracker.TrackerError) {
            // 运动检测失败不影响主流程
        }
    }

    private fun updateMotionState() {
        if (motionSamples.size < 5) return
        val recent = motionSamples.takeLast(5)
        var maxDelta = 0f
        for (i in 1 until recent.size) {
            val prev = recent[i - 1]
            val curr = recent[i]
            val delta = (curr.x - prev.x).absoluteValue + (curr.y - prev.y).absoluteValue + (curr.z - prev.z).absoluteValue
            maxDelta = maxOf(maxDelta, delta)
        }
        lastWearState = lastWearState.copy(isMoving = maxDelta > MOTION_THRESHOLD)
    }

    private fun finish(
        status: MeasurementStatus,
        detail: String,
        mean: Float? = null,
        spread: Float? = null
    ) {
        if (!active) return
        val count = samples.size
        stop()
        emit(status, detail, mean, spread, count)
    }

    private fun emit(
        status: MeasurementStatus,
        detail: String,
        mean: Float? = null,
        spread: Float? = null,
        count: Int = samples.size
    ) {
        onResult(MeasurementResult(mean, spread, samples.size, status, detail))
    }

    companion object {
        private const val MEASUREMENT_TIMEOUT_MS = 60_000L
    }
}
