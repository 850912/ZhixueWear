package org.openwear.temperature

internal enum class MeasurementMode(val label: String, val estimatedSeconds: Int) {
    QUICK("快速测量", 25),
    ACCURATE("准确测量", 105)
}

internal enum class TrendRange(val label: String, val millis: Long) {
    HOUR("1小时", 3_600_000L),
    DAY("1天", 86_400_000L),
    WEEK("1周", 604_800_000L),
    MONTH("1月", 2_592_000_000L)
}

internal object TemperatureQuality {
    const val MAX_SAMPLES = 30
    const val MAX_STABLE_SPREAD_C = 0.3f
    const val MIN_SKIN_CELSIUS = 30.0f
    const val MAX_SKIN_CELSIUS = 45.0f
    const val MIN_AMBIENT_CELSIUS = 15.0f
    const val MAX_AMBIENT_CELSIUS = 45.0f

    fun minSamples(mode: MeasurementMode): Int = when (mode) {
        MeasurementMode.QUICK -> 1
        MeasurementMode.ACCURATE -> 5
    }

    fun timeoutMs(mode: MeasurementMode): Long = when (mode) {
        MeasurementMode.QUICK -> 30_000L
        MeasurementMode.ACCURATE -> 105_000L
    }

    fun summarize(samples: List<TemperatureSample>, mode: MeasurementMode): MeasurementResult? {
        val min = minSamples(mode)
        if (samples.size < min) return null
        val recent = samples.takeLast(min)
        val values = recent.map { it.skinCelsius }
        val mean = values.average().toFloat()
        val spread = values.max() - values.min()
        return when (mode) {
            MeasurementMode.QUICK -> MeasurementResult(
                mean, spread, samples.size, MeasurementStatus.COMPLETE,
                "快速测量：单次采样，精度较低，仅供参考；不代表核心体温。"
            )
            MeasurementMode.ACCURATE -> when {
                spread <= MAX_STABLE_SPREAD_C -> MeasurementResult(
                    mean, spread, samples.size, MeasurementStatus.COMPLETE,
                    "准确测量：读数稳定（波动 ≤0.4°C）；腕部皮肤温度，不代表核心体温。"
                )
                samples.size >= MAX_SAMPLES -> MeasurementResult(
                    null, spread, samples.size, MeasurementStatus.UNSTABLE,
                    "皮肤温度变化过大，本次测量不稳定；请静坐、贴合佩戴后重试。"
                )
                else -> null
            }
        }
    }

    fun isValidSample(skinCelsius: Float, ambientCelsius: Float): Boolean =
        skinCelsius.isFinite() && ambientCelsius.isFinite() &&
            skinCelsius in MIN_SKIN_CELSIUS..MAX_SKIN_CELSIUS &&
            ambientCelsius in MIN_AMBIENT_CELSIUS..MAX_AMBIENT_CELSIUS &&
            skinCelsius > ambientCelsius + 1.0f  // 皮肤温度应高于环境温度，否则可能未贴合
}
