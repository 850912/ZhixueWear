package org.openwear.temperature

/** 趋势区间的统计结果。 */
internal data class TrendSummary(
    val count: Int,
    val mean: Float?,
    val min: Float?,
    val max: Float?
)

/** 趋势图中的一个点（按时间分桶后的聚合）。 */
internal data class TrendPoint(
    val startMillis: Long,
    val endMillis: Long,
    val meanCelsius: Float,
    val sampleCount: Int
)

/**
 * 趋势统计（纯逻辑，便于单元测试）。
 */
internal object TrendStats {
    /** 图表最多绘制的点数，超过则按时间分桶聚合。 */
    const val MAX_CHART_POINTS = 24

    fun summarize(records: List<TemperatureRecord>): TrendSummary {
        if (records.isEmpty()) return TrendSummary(0, null, null, null)
        val values = records.map { it.skinCelsius }
        return TrendSummary(
            count = values.size,
            mean = values.average().toFloat(),
            min = values.min(),
            max = values.max()
        )
    }

    /**
     * 按时间把记录聚合成不超过 [maxPoints] 个点。
     *
     * 直接画原始点会让“1 个月”视图挤成一团；按时间分桶后，横轴比例才真实，
     * 也能把一次长时间采样合并成单个点。
     */
    fun bucket(records: List<TemperatureRecord>, maxPoints: Int = MAX_CHART_POINTS): List<TrendPoint> {
        if (records.isEmpty() || maxPoints <= 0) return emptyList()
        val sorted = records.sortedBy { it.timeMillis }
        if (sorted.size <= maxPoints) {
            return sorted.map {
                TrendPoint(
                    startMillis = it.timeMillis,
                    endMillis = it.timeMillis,
                    meanCelsius = it.skinCelsius,
                    sampleCount = 1
                )
            }
        }
        val first = sorted.first().timeMillis
        val last = sorted.last().timeMillis
        val span = (last - first).coerceAtLeast(1L)
        val bucketSize = (span / maxPoints).coerceAtLeast(1L)
        val grouped = LinkedHashMap<Long, MutableList<Float>>()
        sorted.forEach { record ->
            val index = ((record.timeMillis - first) / bucketSize).coerceIn(0, maxPoints.toLong() - 1)
            grouped.getOrPut(index) { mutableListOf() }.add(record.skinCelsius)
        }
        return grouped.entries.sortedBy { it.key }.map { (index, values) ->
            val start = first + index * bucketSize
            TrendPoint(
                startMillis = start,
                endMillis = start + bucketSize,
                meanCelsius = values.average().toFloat(),
                sampleCount = values.size
            )
        }
    }
}
