package org.openwear.temperature

/**
 * 采样质量判定（纯逻辑，便于单元测试）。
 */
internal object TemperatureQuality {
    /** 滑动窗口内保留的最大样本数。 */
    const val MAX_SAMPLES = 30

    /** 判定“读数稳定”的最大波动（°C）。 */
    const val MAX_STABLE_SPREAD_C = 0.3f

    /** 腕部皮肤温度的合理区间（°C）。 */
    const val MIN_SKIN_CELSIUS = 26.0f
    const val MAX_SKIN_CELSIUS = 45.0f

    /** 环境温度的合理区间（°C）。 */
    const val MIN_AMBIENT_CELSIUS = -20.0f
    const val MAX_AMBIENT_CELSIUS = 60.0f

    /** 判定“手表已贴合手腕”的最小皮肤-环境温差（°C）。 */
    const val WEAR_DELTA_C = 2.0f

    /** 样本有效性的最小皮肤-环境温差（°C）。 */
    const val VALID_DELTA_C = 1.0f

    fun minSamples(mode: MeasurementMode): Int = when (mode) {
        MeasurementMode.QUICK -> 1
        MeasurementMode.ACCURATE -> 5
    }

    /** 整个测量的最长等待时间。 */
    fun timeoutMs(mode: MeasurementMode): Long = when (mode) {
        MeasurementMode.QUICK -> 30_000L
        MeasurementMode.ACCURATE -> 75_000L
    }

    /** 超过该时间仍然无法稳定，则判定为“不稳定”。 */
    fun unstableAfterMs(mode: MeasurementMode): Long = when (mode) {
        MeasurementMode.QUICK -> 20_000L
        MeasurementMode.ACCURATE -> 45_000L
    }

    /**
     * 判断一个样本是否可用。
     *
     * 皮肤温度必须显著高于环境温度，否则说明手表没有贴在皮肤上。
     */
    fun isValidSample(skinCelsius: Float, ambientCelsius: Float): Boolean =
        skinCelsius.isFinite() && ambientCelsius.isFinite() &&
            skinCelsius in MIN_SKIN_CELSIUS..MAX_SKIN_CELSIUS &&
            ambientCelsius in MIN_AMBIENT_CELSIUS..MAX_AMBIENT_CELSIUS &&
            skinCelsius - ambientCelsius >= VALID_DELTA_C

    /**
     * 根据皮肤温度与环境温度的差值推断佩戴状态。
     *
     * 手表离开手腕时，传感器读到的温度会迅速趋近环境温度，这是最省电的佩戴判定方式
     * （不需要额外的传感器，也不需要额外的唤醒）。
     */
    fun wearState(skinCelsius: Float, ambientCelsius: Float, isMoving: Boolean): WearState {
        if (!skinCelsius.isFinite() || !ambientCelsius.isFinite()) {
            return WearState(false, isMoving, "未获得有效读数")
        }
        val delta = skinCelsius - ambientCelsius
        val wearing = skinCelsius >= MIN_SKIN_CELSIUS && delta >= WEAR_DELTA_C
        val reason = when {
            !wearing -> "皮肤温度接近环境温度，手表可能未贴合手腕"
            isMoving -> "检测到手腕运动，请保持静息"
            else -> "佩戴正常"
        }
        return WearState(wearing, isMoving, reason)
    }

    /**
     * 汇总样本。返回 null 表示“还不足以给出结果”，需要继续采样。
     */
    fun summarize(samples: List<TemperatureSample>, mode: MeasurementMode): MeasurementResult? {
        val min = minSamples(mode)
        if (samples.size < min) return null
        val recent = samples.takeLast(min)
        val values = recent.map { it.skinCelsius }
        val mean = values.average().toFloat()
        val spread = values.max() - values.min()
        val ambient = recent.map { it.ambientCelsius }.average().toFloat()
        return when (mode) {
            MeasurementMode.QUICK -> MeasurementResult(
                meanCelsius = mean,
                ambientCelsius = ambient,
                spreadCelsius = spread,
                sampleCount = samples.size,
                status = MeasurementStatus.COMPLETE,
                detail = "快速测量：单次采样，精度较低，仅供参考；腕部皮肤温度 ≠ 核心体温。"
            )
            MeasurementMode.ACCURATE -> if (spread <= MAX_STABLE_SPREAD_C) {
                MeasurementResult(
                    meanCelsius = mean,
                    ambientCelsius = ambient,
                    spreadCelsius = spread,
                    sampleCount = samples.size,
                    status = MeasurementStatus.COMPLETE,
                    detail = "准确测量：读数稳定（波动 ≤${MAX_STABLE_SPREAD_C}°C）；腕部皮肤温度，不代表核心体温。"
                )
            } else {
                null
            }
        }
    }
}
