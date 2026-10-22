package org.openwear.temperature

import android.content.Context

/**
 * 本地历史记录存储。
 *
 * 设计要点：
 *  - 使用紧凑的 CSV 行格式（`时间戳,温度`），比 JSON 数组更省空间、解析更快；
 *  - 读写都通过同一把锁 + 单一入口，避免“界面写入”和“后台任务写入”并发时互相覆盖丢数据；
 *  - 只保留最近 [MAX_RECORDS] 条，长周期后台测量也不会让存储无限膨胀。
 */
internal object TemperatureHistoryStore {
    /** 最多保留的记录条数。 */
    const val MAX_RECORDS = 1000

    private const val PREFS = "temperature_history"
    private const val KEY_ROWS = "rows"

    private val lock = Any()

    /** 追加一条记录（自动裁剪到 [MAX_RECORDS]）。 */
    fun append(context: Context, skinCelsius: Float, timeMillis: Long = System.currentTimeMillis()) {
        if (!skinCelsius.isFinite()) return
        synchronized(lock) {
            val records = read(context) + TemperatureRecord(timeMillis, skinCelsius)
            write(context, records)
        }
    }

    /** 读取全部记录，按时间升序。 */
    fun load(context: Context): List<TemperatureRecord> = synchronized(lock) { read(context) }

    /** 清空历史。 */
    fun clear(context: Context) {
        synchronized(lock) {
            prefs(context).edit().remove(KEY_ROWS).apply()
        }
    }

    /** 序列化为紧凑文本（纯函数，便于单元测试）。 */
    fun encode(records: List<TemperatureRecord>): String =
        records.joinToString(separator = "\n") { "${it.timeMillis},${it.skinCelsius}" }

    /** 反序列化，自动跳过损坏的行（纯函数，便于单元测试）。 */
    fun decode(raw: String?): List<TemperatureRecord> {
        if (raw.isNullOrBlank()) return emptyList()
        val out = ArrayList<TemperatureRecord>()
        raw.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty()) return@forEach
            val separator = trimmed.indexOf(',')
            if (separator <= 0) return@forEach
            val time = trimmed.substring(0, separator).trim().toLongOrNull() ?: return@forEach
            val value = trimmed.substring(separator + 1).trim().toFloatOrNull() ?: return@forEach
            if (!value.isFinite()) return@forEach
            out.add(TemperatureRecord(time, value))
        }
        return out
    }

    private fun read(context: Context): List<TemperatureRecord> =
        decode(prefs(context).getString(KEY_ROWS, null))

    private fun write(context: Context, records: List<TemperatureRecord>) {
        val kept = if (records.size > MAX_RECORDS) records.takeLast(MAX_RECORDS) else records
        prefs(context).edit().putString(KEY_ROWS, encode(kept)).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
