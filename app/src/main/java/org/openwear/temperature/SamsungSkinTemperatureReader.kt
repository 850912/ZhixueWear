package org.openwear.temperature

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.samsung.android.service.health.tracking.ConnectionListener
import com.samsung.android.service.health.tracking.HealthTracker
import com.samsung.android.service.health.tracking.HealthTrackerException
import com.samsung.android.service.health.tracking.HealthTrackingService
import com.samsung.android.service.health.tracking.data.DataPoint
import com.samsung.android.service.health.tracking.data.HealthTrackerType
import com.samsung.android.service.health.tracking.data.ValueKey
import kotlin.math.abs

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
    val meanCelsius: Float?,
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
    private var active = false
    private var lastWearState = WearState(false, false, "未检测")

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
            finish(MeasurementStatus.PERMISSION_DENIED, "系统拒绝访问：${error.message ?: "请检查皮肤温度/身体传感器权限"}")
        } catch (error: Throwable) {
            finish(MeasurementStatus.CONNECTION_FAILED, "SDK 初始化失败：${error.message ?: error.javaClass.simpleName}")
        }
    }

    fun stop() {
        active = false
        timeout?.let(mainHandler::removeCallbacks)
        timeout = null
        runCatching { skinTracker?.unsetEventListener() }
        skinTracker = null
        runCatching { motionTracker?.unsetEventListener() }
        motionTracker = null
        runCatching { service?.disconnectService() }
        service = null
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
                val trackerType = when {
                    supported.contains(HealthTrackerType.SKIN_TEMPERATURE_ON_DEMAND) ->
                        HealthTrackerType.SKIN_TEMPERATURE_ON_DEMAND
                    supported.contains(HealthTrackerType.SKIN_TEMPERATURE_CONTINUOUS) ->
                        HealthTrackerType.SKIN_TEMPERATURE_CONTINUOUS
                    supported.contains(HealthTrackerType.SKIN_TEMPERATURE) ->
                        HealthTrackerType.SKIN_TEMPERATURE
                    else -> {
                        finish(MeasurementStatus.UNSUPPORTED, "Health Platform 已连接，但当前手表未提供皮肤温度传感器")
                        return@post
                    }
                }
                try {
                    skinTracker = current.getHealthTracker(trackerType)
                    skinTracker?.setEventListener(skinTrackerListener)
                        ?: finish(MeasurementStatus.ERROR, "SDK 未返回皮肤温度 tracker")
                    if (active) emit(MeasurementStatus.MEASURING, "传感器已启动，等待有效样本…")
                } catch (error: SecurityException) {
                    finish(MeasurementStatus.PERMISSION_DENIED, "三星 Health Sensor SDK 权限被拒绝：${error.message ?: "请检查身体传感器与皮肤温度权限"}")
                } catch (error: UnsupportedOperationException) {
                    finish(MeasurementStatus.UNSUPPORTED, error.message ?: "当前设备不支持皮肤温度测量")
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
                    if (skin != null && ambient != null && objectTemp != null &&
                        TemperatureQuality.isValidSample(skin, ambient, objectTemp)
                    ) {
                        samples += TemperatureSample(skin, ambient, objectTemp, point.timestamp)
                        lastWearState = lastWearState.copy(isWearing = true, reason = "佩戴正常")
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

        override fun onError(error: HealthTracker.TrackerError) = Unit
    }

    private fun updateMotionState() {
        if (motionSamples.size < 5) return
        val recent = motionSamples.takeLast(5)
        var maxDelta = 0f
        for (i in 1 until recent.size) {
            val prev = recent[i - 1]
            val curr = recent[i]
            val delta = abs(curr.x - prev.x) + abs(curr.y - prev.y) + abs(curr.z - prev.z)
            maxDelta = maxOf(maxDelta, delta)
        }
        lastWearState = lastWearState.copy(
            isMoving = maxDelta > MOTION_THRESHOLD,
            reason = if (maxDelta > MOTION_THRESHOLD) "检测到手腕运动" else lastWearState.reason
        )
    }

    private fun finish(
        status: MeasurementStatus,
        detail: String,
        mean: Float? = null,
        spread: Float? = null
    ) {
        if (!active) return
        val count = samples.size
        val last = samples.lastOrNull()
        stop()
        emit(status, detail, mean, spread, count, last?.objectCelsius, last?.ambientCelsius)
    }

    private fun emit(
        status: MeasurementStatus,
        detail: String,
        mean: Float? = null,
        spread: Float? = null,
        count: Int = samples.size,
        objectCelsius: Float? = null,
        ambientCelsius: Float? = null
    ) {
        onResult(
            MeasurementResult(
                mean,
                objectCelsius,
                ambientCelsius,
                spread,
                count,
                status,
                detail
            )
        )
    }

    private companion object {
        const val WEAR_THRESHOLD_C = 31.0f
        const val SKIN_ABOVE_AMBIENT_MIN = 2.0f
        const val MOTION_THRESHOLD = 0.15f
        const val SAMPLE_INTERVAL_MS = 2000L
    }
}
