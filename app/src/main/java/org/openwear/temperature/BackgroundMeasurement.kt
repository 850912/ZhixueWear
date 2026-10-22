package org.openwear.temperature

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.HandlerThread
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 后台采样流程（与界面无关，可在任意线程调用）。
 */
internal object BackgroundMeasurement {

    /**
     * 执行一次后台采样：连接三星健康服务 -> 取到一个有效样本 -> 立刻断开并落库。
     *
     * @return true 表示本次失败、值得稍后重试。
     */
    fun runOnce(context: Context): Boolean {
        if (!hasPermission(context)) return false

        val worker = HandlerThread("skin-temperature-sampler").apply { start() }
        val latch = CountDownLatch(1)
        var outcome: MeasurementResult? = null

        val reader = SamsungSkinTemperatureReader(
            context = context,
            mode = MeasurementMode.QUICK,
            onResult = { result ->
                outcome = result
                latch.countDown()
            },
            // 后台不开启运动检测：少一个传感器、少一次唤醒。
            detectMotion = false,
            timeoutOverrideMs = SAMPLE_BUDGET_MS,
            handler = Handler(worker.looper)
        )

        try {
            reader.start()
            latch.await(WAIT_SECONDS, TimeUnit.SECONDS)
        } finally {
            reader.stop()
            worker.quitSafely()
        }

        val result = outcome ?: return false
        if (result.status != MeasurementStatus.COMPLETE) return false
        val value = result.meanCelsius ?: return false
        TemperatureHistoryStore.append(context, value)
        return false
    }

    /** 目标 SDK 36+ 使用新的健康权限，之前使用通用的身体传感器权限。 */
    private fun hasPermission(context: Context): Boolean {
        val permission = if (context.applicationInfo.targetSdkVersion >= 36) {
            "android.permission.health.READ_SKIN_TEMPERATURE"
        } else {
            Manifest.permission.BODY_SENSORS
        }
        return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }

    /** 单次后台采样允许的最长采样时间（毫秒）。 */
    private const val SAMPLE_BUDGET_MS = 20_000L

    /** 等待结果的最长时间（秒），略大于采样预算，避免边界抖动。 */
    private const val WAIT_SECONDS = 35L
}
