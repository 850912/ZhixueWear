package org.openwear.temperature

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.samsung.android.service.health.tracking.ConnectionListener
import com.samsung.android.service.health.tracking.HealthTracker
import com.samsung.android.service.health.tracking.HealthTrackerException
import com.samsung.android.service.health.tracking.HealthTrackingService
import com.samsung.android.service.health.tracking.data.DataPoint
import com.samsung.android.service.health.tracking.data.HealthTrackerType
import com.samsung.android.service.health.tracking.data.ValueKey

/**
 * 腕部皮肤温度读取器。
 *
 * 相比旧版实现的修复点：
 *  1. **数值来源修正**：SDK 的 `SkinTemperatureSet` 只有 `OBJECT_TEMPERATURE`（贴着皮肤时
 *     就是腕部皮肤温度）、`AMBIENT_TEMPERATURE` 和 `STATUS`。旧代码把 `OBJECT_TEMPERATURE`
 *     同时当成“皮肤温度”和“物体温度”，界面出现两个完全相同的数字。现在只保留一个通道，
 *     并按 SDK 文档读取 `STATUS`（0 正常 / -1 错误），错误数据点会被丢弃。
 *  2. **会话令牌（sessionId）**：`disconnectService()` 是异步的，旧连接的
 *     `onConnectionEnded` 回调可能在新的 `start()` 之后才到达，从而把**新**的一次测量
 *     误判为“连接中断”。现在每次 `start()` 都会生成新的令牌，所有回调都先校验令牌。
 *  3. **运动检测真正生效**：旧代码定义了 `motionTrackerListener` 却从未创建过
 *     `motionTracker`，`HealthTrackerType.ACCELEROMETER` 从未被使用，运动检测是死代码。
 *     现在改用系统加速度计（5Hz），只在测量期间注册。
 *  4. **佩戴状态回传**：旧代码算出了 `lastWearState` 却从未传给界面，
 *     界面的“佩戴检测”永远显示“未佩戴手表”。现在通过 `onWearStateChanged` 实时回传。
 *  5. **节流改用 `elapsedRealtime()`**：旧代码用墙上时钟做节流，NTP 校时回拨会导致采样被长时间抑制。
 *  6. **不再丢弃整批数据**：旧代码的 3 秒节流会直接 `return` 掉整批数据点，
 *     连带丢掉同一批里的错误状态与佩戴判定。
 *
 * 电量考量：
 *  - 使用 `SKIN_TEMPERATURE_CONTINUOUS`（官方文档明确 on-demand tracker 仅适用于前台、
 *    且单次不超过 30 秒），测量结束立刻 `unsetEventListener()` + `disconnectService()`；
 *  - 运动检测仅在前台测量期间开启，后台自动测量不启用；
 *  - 采样窗口只保留最近 [TemperatureQuality.MAX_SAMPLES] 个样本，内存与 CPU 开销恒定。
 */
internal class SamsungSkinTemperatureReader(
    context: Context,
    private val mode: MeasurementMode,
    private val onResult: (MeasurementResult) -> Unit,
    private val onWearStateChanged: (WearState) -> Unit = {},
    private val detectMotion: Boolean = true,
    private val timeoutOverrideMs: Long? = null,
    private val handler: Handler = Handler(Looper.getMainLooper())
) {
    private val appContext = context.applicationContext
    private val samples = ArrayList<TemperatureSample>(TemperatureQuality.MAX_SAMPLES)
    private val motionDetector = MotionDetector(appContext)

    private var service: HealthTrackingService? = null
    private var tracker: HealthTracker? = null
    private var timeoutRunnable: Runnable? = null
    private var unstableRunnable: Runnable? = null
    private var flushRunnable: Runnable? = null

    private var active = false
    private var sessionId = 0
    private var startedAtElapsed = 0L
    private var lastAcceptedElapsed = 0L
    private var notWearingSinceElapsed = 0L
    private var lastWearState = WearState(false, false, "未检测")

    /** 开始一次测量。重复调用会先中止上一次测量。 */
    fun start() {
        stop()
        sessionId++
        val token = sessionId

        samples.clear()
        active = true
        startedAtElapsed = SystemClock.elapsedRealtime()
        lastAcceptedElapsed = 0L
        notWearingSinceElapsed = 0L
        lastWearState = WearState(false, false, "未检测")

        emit(MeasurementStatus.CONNECTING, "正在连接三星 Health Sensor Service…")

        val budget = timeoutOverrideMs ?: TemperatureQuality.timeoutMs(mode)
        timeoutRunnable = Runnable {
            if (!isCurrent(token)) return@Runnable
            finish(
                MeasurementStatus.TIMEOUT,
                "${mode.estimatedSeconds} 秒内未获得稳定读数。请确认手表已贴合手腕后重试"
            )
        }.also { handler.postDelayed(it, budget) }

        if (mode == MeasurementMode.ACCURATE) {
            unstableRunnable = Runnable {
                if (!isCurrent(token)) return@Runnable
                finish(
                    MeasurementStatus.UNSTABLE,
                    "皮肤温度波动过大，本次测量不稳定；请静坐并重新测量"
                )
            }.also { handler.postDelayed(it, TemperatureQuality.unstableAfterMs(mode)) }
        }

        try {
            service = HealthTrackingService(connectionListener(token), appContext)
                .also { it.connectService() }
        } catch (error: SecurityException) {
            finish(
                MeasurementStatus.PERMISSION_DENIED,
                "系统拒绝访问：${error.message ?: "请检查皮肤温度/身体传感器权限"}"
            )
        } catch (error: Throwable) {
            finish(
                MeasurementStatus.CONNECTION_FAILED,
                "SDK 初始化失败：${error.message ?: error.javaClass.simpleName}"
            )
        }
    }

    /** 停止测量并释放传感器。 */
    fun stop() {
        active = false
        // 让所有在途回调立即失效，避免旧连接/旧数据点影响下一次测量。
        sessionId++
        timeoutRunnable?.let(handler::removeCallbacks)
        timeoutRunnable = null
        unstableRunnable?.let(handler::removeCallbacks)
        unstableRunnable = null
        flushRunnable?.let(handler::removeCallbacks)
        flushRunnable = null
        motionDetector.stop()
        runCatching { tracker?.unsetEventListener() }
        tracker = null
        runCatching { service?.disconnectService() }
        service = null
    }

    private fun isCurrent(token: Int): Boolean = active && token == sessionId

    private fun connectionListener(token: Int) = object : ConnectionListener {
        override fun onConnectionSuccess() {
            handler.post {
                if (!isCurrent(token)) return@post
                onServiceConnected(token)
            }
        }

        override fun onConnectionEnded() {
            handler.post {
                if (!isCurrent(token)) return@post
                finish(
                    MeasurementStatus.CONNECTION_FAILED,
                    "与三星健康服务的连接已中断，请重试"
                )
            }
        }

        override fun onConnectionFailed(error: HealthTrackerException) {
            handler.post {
                if (!isCurrent(token)) return@post
                finish(statusForConnectionError(error), hintForConnectionError(error))
            }
        }
    }

    private fun statusForConnectionError(error: HealthTrackerException): MeasurementStatus =
        when (error.errorCode) {
            HealthTrackerException.PACKAGE_NOT_INSTALLED -> MeasurementStatus.SERVICE_MISSING
            HealthTrackerException.OLD_PLATFORM_VERSION -> MeasurementStatus.SERVICE_OLD
            else -> MeasurementStatus.CONNECTION_FAILED
        }

    private fun hintForConnectionError(error: HealthTrackerException): String =
        when (statusForConnectionError(error)) {
            MeasurementStatus.SERVICE_MISSING ->
                "未检测到三星健康服务应用，请先安装或更新 Samsung Health"
            MeasurementStatus.SERVICE_OLD ->
                "三星健康服务版本过旧，请在应用商店更新 Samsung Health"
            else -> "连接三星健康服务失败：${error.message ?: "错误码 ${error.errorCode}"}"
        }

    private fun onServiceConnected(token: Int) {
        val currentService = service
        if (currentService == null) {
            finish(MeasurementStatus.CONNECTION_FAILED, "健康服务连接已失效，请重试")
            return
        }
        if (!supportsSkinTemperature(currentService)) {
            finish(
                MeasurementStatus.UNSUPPORTED,
                "该手表不支持皮肤温度测量，请使用支持 Galaxy Watch 皮肤温度的机型"
            )
            return
        }
        try {
            val healthTracker = currentService.getHealthTracker(
                HealthTrackerType.SKIN_TEMPERATURE_CONTINUOUS
            )
            tracker = healthTracker
            healthTracker.setEventListener(trackerListener(token))
            scheduleFlush(token)
            if (detectMotion) {
                motionDetector.start()
            }
            emit(MeasurementStatus.MEASURING, "正在采集皮肤温度样本…")
        } catch (error: HealthTrackerException) {
            finish(
                MeasurementStatus.CONNECTION_FAILED,
                "无法启动温度传感器：${error.message ?: "错误码 ${error.errorCode}"}"
            )
        } catch (error: IllegalStateException) {
            finish(MeasurementStatus.CONNECTION_FAILED, "健康服务尚未就绪，请稍后重试")
        } catch (error: Throwable) {
            finish(
                MeasurementStatus.UNSUPPORTED,
                "启动皮肤温度测量失败：${error.message ?: error.javaClass.simpleName}"
            )
        }
    }

    private fun supportsSkinTemperature(currentService: HealthTrackingService): Boolean =
        runCatching {
            val supported = currentService.trackingCapability.supportHealthTrackerTypes
            supported.isEmpty() || supported.contains(HealthTrackerType.SKIN_TEMPERATURE_CONTINUOUS)
        }.getOrDefault(true)

    /**
     * 周期性请求一次数据。
     *
     * 官方文档说明：屏幕点亮时连续 tracker 每次测量只给一个数据点，
     * 主动 `flush()` 可以更快拿到首个读数。5 秒一次在测量窗口内最多触发十几次，
     * 开销可以忽略，却显著缩短首次读数等待时间。
     */
    private fun scheduleFlush(token: Int) {
        val runnable = object : Runnable {
            override fun run() {
                if (!isCurrent(token)) return
                runCatching { tracker?.flush() }
                handler.postDelayed(this, FLUSH_INTERVAL_MS)
            }
        }
        flushRunnable = runnable
        handler.postDelayed(runnable, FLUSH_INTERVAL_MS)
        runCatching { tracker?.flush() }
    }

    private fun trackerListener(token: Int) = object : HealthTracker.TrackerEventListener {
        override fun onDataReceived(dataPoints: List<DataPoint>) {
            handler.post {
                if (!isCurrent(token)) return@post
                handleDataPoints(dataPoints)
            }
        }

        override fun onFlushCompleted() = Unit

        override fun onError(error: HealthTracker.TrackerError) {
            handler.post {
                if (!isCurrent(token)) return@post
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

    private fun handleDataPoints(dataPoints: List<DataPoint>) {
        for (point in dataPoints) {
            // STATUS: 0 = 正常，-1 = 错误。错误数据点直接丢弃。
            val status = asNumber(point.getValue(ValueKey.SkinTemperatureSet.STATUS))?.toInt()
            if (status != null && status != STATUS_NORMAL) continue

            // SDK 只有 OBJECT_TEMPERATURE（= 腕部皮肤温度）与 AMBIENT_TEMPERATURE 两个温度值。
            val skin = asNumber(point.getValue(ValueKey.SkinTemperatureSet.OBJECT_TEMPERATURE))
                ?.toFloat() ?: continue
            val ambient = asNumber(point.getValue(ValueKey.SkinTemperatureSet.AMBIENT_TEMPERATURE))
                ?.toFloat() ?: continue

            val moving = detectMotion && motionDetector.isMoving
            val wear = TemperatureQuality.wearState(skin, ambient, moving)
            if (wear != lastWearState) {
                lastWearState = wear
                onWearStateChanged(wear)
            }

            val now = SystemClock.elapsedRealtime()
            if (!wear.isWearing) {
                // 刚上电时传感器可能还没稳定，给 5 秒宽限期再判定未佩戴。
                if (notWearingSinceElapsed == 0L) {
                    notWearingSinceElapsed = now
                } else if (now - notWearingSinceElapsed >= NOT_WEARING_GRACE_MS) {
                    finish(
                        MeasurementStatus.NOT_WEARING,
                        "检测到手表未贴合手腕，本次测量已取消"
                    )
                    return
                }
                continue
            }
            notWearingSinceElapsed = 0L

            if (wear.isMoving) {
                // 运动中不采样，等手腕静止后继续。
                continue
            }
            if (!TemperatureQuality.isValidSample(skin, ambient)) continue

            if (lastAcceptedElapsed != 0L && now - lastAcceptedElapsed < MIN_SAMPLE_INTERVAL_MS) {
                continue
            }
            lastAcceptedElapsed = now

            samples.add(TemperatureSample(skin, ambient, point.timestamp))
            while (samples.size > TemperatureQuality.MAX_SAMPLES) {
                samples.removeAt(0)
            }
            evaluate()
            if (!active) return
        }
    }

    /**
     * 以数值形式读取一个数据点字段。
     *
     * SDK 的 `getValue` 在字节码层面返回 `Object`，不同固件可能给出 `Float` 或 `Double`，
     * 这里统一按 `Number` 读取，避免因为装箱类型不同而丢样本。
     */
    private fun asNumber(raw: Any?): Number? = raw as? Number

    private fun evaluate() {
        val summary = TemperatureQuality.summarize(samples, mode) ?: return
        finish(
            MeasurementStatus.COMPLETE,
            summary.detail,
            summary.meanCelsius,
            summary.spreadCelsius,
            summary.ambientCelsius
        )
    }

    private fun finish(
        status: MeasurementStatus,
        detail: String,
        mean: Float? = null,
        spread: Float? = null,
        ambient: Float? = null
    ) {
        if (!active) return
        val count = samples.size
        stop()
        emit(status, detail, mean, spread, count, ambient)
    }

    private fun emit(
        status: MeasurementStatus,
        detail: String,
        mean: Float? = null,
        spread: Float? = null,
        count: Int = samples.size,
        ambient: Float? = null
    ) {
        onResult(
            MeasurementResult(
                meanCelsius = mean,
                ambientCelsius = ambient,
                spreadCelsius = spread,
                sampleCount = count,
                status = status,
                detail = detail
            )
        )
    }

    private companion object {
        /** SDK 文档：STATUS = 0 表示正常。 */
        const val STATUS_NORMAL = 0

        /** 请求数据的间隔。 */
        const val FLUSH_INTERVAL_MS = 5_000L

        /** 两次有效样本之间的最小间隔，避免窗口被同一个瞬间的数据填满。 */
        const val MIN_SAMPLE_INTERVAL_MS = 1_500L

        /** 判定“未佩戴”之前的宽限时间。 */
        const val NOT_WEARING_GRACE_MS = 5_000L
    }
}
