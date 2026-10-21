package org.openwear.temperature

internal enum class MeasurementMode(val label: String, val estimatedSeconds: Int) {
    QUICK("快速测量", 25),
    ACCURATE("准确测量", 65)
}

internal object TemperatureQuality {
    const val MAX_SAMPLES = 30
    const val MAX_STABLE_SPREAD_C = 0.4f

    fun minSamples(mode: MeasurementMode): Int = when (mode) {
        MeasurementMode.QUICK -> 1
        MeasurementMode.ACCURATE -> 3
    }

    fun timeoutMs(mode: MeasurementMode): Long = when (mode) {
        MeasurementMode.QUICK -> 30_000L
        MeasurementMode.ACCURATE -> 65_000L
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
                "快速测量：腕部皮肤温度，不代表核心体温。"
            )
            MeasurementMode.ACCURATE -> when {
                spread <= MAX_STABLE_SPREAD_C -> MeasurementResult(
                    mean, spread, samples.size, MeasurementStatus.COMPLETE,
                    "准确测量：腕部皮肤温度读数稳定；不代表核心体温。"
                )
                samples.size >= MAX_SAMPLES -> MeasurementResult(
                    null, spread, samples.size, MeasurementStatus.UNSTABLE,
                    "皮肤温度变化过大，本次测量不稳定；请静坐并重新测量。"
                )
                else -> null
            }
        }
    }

    fun isValidSample(skinCelsius: Float, ambientCelsius: Float): Boolean =
        skinCelsius.isFinite() && ambientCelsius.isFinite() &&
            skinCelsius in 10f..45f && ambientCelsius in -20f..60f
}
