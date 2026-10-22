package org.openwear.temperature

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 手腕运动检测。
 *
 * 电量考量：
 *  - 使用系统加速度计 + `SENSOR_DELAY_NORMAL`（约 5Hz），远低于三星 SDK 里 25Hz 的
 *    `ACCELEROMETER_CONTINUOUS`，传感器占用时间与功耗都更小；
 *  - 只在“前台测量进行中”注册监听，测量结束立即注销；
 *  - 后台自动测量**不启用**运动检测，进一步省电。
 */
internal class MotionDetector(context: Context) : SensorEventListener {
    private val sensorManager =
        context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val magnitudes = ArrayList<Float>(WINDOW_SIZE)

    /** 最近一次采样是否检测到手腕运动。 */
    var isMoving: Boolean = false
        private set

    /** 开始监听。传感器不可用时静默降级（不阻塞测量）。 */
    fun start() {
        val manager = sensorManager ?: return
        val sensor = accelerometer ?: return
        magnitudes.clear()
        isMoving = false
        manager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    /** 停止监听并释放传感器。 */
    fun stop() {
        sensorManager?.unregisterListener(this)
        magnitudes.clear()
        isMoving = false
    }

    override fun onSensorChanged(event: SensorEvent?) {
        val values = event?.values ?: return
        if (values.size < 3) return
        val x = values[0]
        val y = values[1]
        val z = values[2]
        val magnitude = sqrt(x * x + y * y + z * z)
        if (!magnitude.isFinite()) return

        magnitudes.add(magnitude)
        while (magnitudes.size > WINDOW_SIZE) {
            magnitudes.removeAt(0)
        }
        if (magnitudes.size < WINDOW_SIZE) return

        var maxDelta = 0f
        for (index in 1 until magnitudes.size) {
            maxDelta = maxOf(maxDelta, abs(magnitudes[index] - magnitudes[index - 1]))
        }
        isMoving = maxDelta > MOVE_DELTA
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private companion object {
        /** 滑动窗口大小。 */
        const val WINDOW_SIZE = 5

        /**
         * 加速度模长在 200ms 间隔下的变化阈值（m/s²）。
         * 静止佩戴时模长稳定在 9.8 附近，波动通常 < 0.2；手腕摆动会超过 1.0。
         */
        const val MOVE_DELTA = 0.8f
    }
}
