package org.openwear.temperature

/**
 * 应用共用的数据模型。
 *
 * 说明（重要）：
 * 三星健康传感器 SDK 的 `ValueKey.SkinTemperatureSet` 只提供三个值：
 *  - `OBJECT_TEMPERATURE`：手表传感器接触到的物体温度 —— 佩戴在手腕上时它就是**腕部皮肤温度**；
 *  - `AMBIENT_TEMPERATURE`：手表周围的环境温度；
 *  - `STATUS`：测量状态（0 = 正常，-1 = 错误）。
 *
 * SDK 并**没有**独立的“皮肤温度”数值，因此早期版本里“皮肤温度”和“物体温度”读的是同一个 key，
 * 界面上出现两个一模一样的数字。这里只保留一个腕部皮肤温度通道。
 */

/** 一条落盘的历史记录。 */
internal data class TemperatureRecord(
    val timeMillis: Long,
    val skinCelsius: Float
)

/** 一次传感器采样。 */
internal data class TemperatureSample(
    val skinCelsius: Float,
    val ambientCelsius: Float,
    val timestampMillis: Long
)

/** 佩戴 / 运动状态。 */
internal data class WearState(
    val isWearing: Boolean,
    val isMoving: Boolean,
    val reason: String
)

internal enum class MeasurementMode(val label: String, val estimatedSeconds: Int) {
    QUICK("快速测量", 20),
    ACCURATE("准确测量", 45)
}

internal enum class TrendRange(val label: String, val millis: Long) {
    HOUR("1小时", 3_600_000L),
    DAY("1天", 86_400_000L),
    WEEK("1周", 604_800_000L),
    MONTH("1月", 2_592_000_000L)
}

/** 后台自动测量的间隔。JobScheduler 周期任务最短 15 分钟，这里提供三档以平衡电量与数据密度。 */
internal enum class BackgroundInterval(val label: String, val minutes: Int) {
    EVERY_15_MIN("15 分钟", 15),
    EVERY_30_MIN("30 分钟", 30),
    EVERY_HOUR("1 小时", 60)
}

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

/**
 * 一次测量的结果。
 *
 * @param meanCelsius 腕部皮肤温度均值（°C）
 * @param ambientCelsius 环境温度（°C）
 * @param spreadCelsius 最近若干样本的波动范围（°C）
 */
internal data class MeasurementResult(
    val meanCelsius: Float?,
    val ambientCelsius: Float?,
    val spreadCelsius: Float?,
    val sampleCount: Int,
    val status: MeasurementStatus,
    val detail: String
)
