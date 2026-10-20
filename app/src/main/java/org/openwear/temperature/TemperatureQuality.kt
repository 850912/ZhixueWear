package org.openwear.temperature

internal object TemperatureQuality {
    const val MIN_SAMPLES = 5
    const val MAX_SAMPLES = 30
    const val MAX_STABLE_SPREAD_C = 0.4f

    fun summarize(samples: List<TemperatureSample>): MeasurementResult? {
        if (samples.size < MIN_SAMPLES) return null
        val recent = samples.takeLast(MIN_SAMPLES)
        val values = recent.map { it.skinCelsius }
        val mean = values.average().toFloat()
        val spread = values.max() - values.min()
        return when {
            spread <= MAX_STABLE_SPREAD_C -> MeasurementResult(
                mean, spread, samples.size, MeasurementStatus.COMPLETE,
                "腕部皮肤温度读数稳定；不代表核心体温。"
            )
            samples.size >= MAX_SAMPLES -> MeasurementResult(
                null, spread, samples.size, MeasurementStatus.UNSTABLE,
                "皮肤温度变化过大，本次测量不稳定；请静坐并重新测量。"
            )
            else -> null
        }
    }

    fun isValidSample(skinCelsius: Float, ambientCelsius: Float): Boolean =
        skinCelsius.isFinite() && ambientCelsius.isFinite() &&
            skinCelsius in 10f..45f && ambientCelsius in -20f..60f
}
