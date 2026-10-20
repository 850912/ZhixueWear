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

internal data class TemperatureSample(
    val skinCelsius: Float,
    val ambientCelsius: Float,
    val timestampMillis: Long
)

internal data class MeasurementResult(
    val meanCelsius: Float?,
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
    ERROR
}

internal class SamsungSkinTemperatureReader(
    context: Context,
    private val onResult: (MeasurementResult) -> Unit
) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val samples = mutableListOf<TemperatureSample>()
    private var service: HealthTrackingService? = null
    private var tracker: HealthTracker? = null
    private var timeout: Runnable? = null
    private var active = false

    fun start() {
        stop()
        samples.clear()
        active = true
        emit(MeasurementStatus.CONNECTING, "正在连接三星 Health Sensor Service…")
        timeout = Runnable {
            finish(MeasurementStatus.TIMEOUT, "30 秒内未收到皮肤温度样本。请确认手表贴合手腕后重试")
        }.also { mainHandler.postDelayed(it, MEASUREMENT_TIMEOUT_MS) }
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
        runCatching { tracker?.unsetEventListener() }
        tracker = null
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
            if (!supported.contains(HealthTrackerType.SKIN_TEMPERATURE_CONTINUOUS)) {
                    finish(MeasurementStatus.UNSUPPORTED, "Health Platform 已连接，但当前手表未提供连续皮肤温度传感器")
                return@post
            }
            try {
                tracker = current.getHealthTracker(HealthTrackerType.SKIN_TEMPERATURE_CONTINUOUS)
                tracker?.setEventListener(trackerListener)
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

    private val trackerListener = object : HealthTracker.TrackerEventListener {
        override fun onDataReceived(dataPoints: List<DataPoint>) {
            mainHandler.post {
            if (!active) return@post
            for (point in dataPoints) {
                val skin = point.getValue(ValueKey.SkinTemperatureSet.OBJECT_TEMPERATURE)
                val ambient = point.getValue(ValueKey.SkinTemperatureSet.AMBIENT_TEMPERATURE)
                val sampleStatus = point.getValue(ValueKey.SkinTemperatureSet.STATUS)
                if (sampleStatus == -1) {
                    finish(MeasurementStatus.ERROR, "三星传感器报告温度数据错误，请清洁传感器并确保与皮肤接触")
                    return@post
                }
                if (skin != null && ambient != null && sampleStatus == 0 && TemperatureQuality.isValidSample(skin, ambient)) {
                    samples += TemperatureSample(skin, ambient, point.timestamp)
                }
            }
            TemperatureQuality.summarize(samples)?.let { summary ->
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
        onResult(MeasurementResult(mean, spread, count, status, detail))
    }

    companion object {
        private const val MEASUREMENT_TIMEOUT_MS = 30_000L
    }
}
