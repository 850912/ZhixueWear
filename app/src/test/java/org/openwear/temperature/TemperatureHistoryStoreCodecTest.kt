package org.openwear.temperature

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TemperatureHistoryStoreCodecTest {

    @Test
    fun roundTripsRecords() {
        val records = listOf(
            TemperatureRecord(1_700_000_000_000L, 33.25f),
            TemperatureRecord(1_700_000_060_000L, 34.5f)
        )
        val decoded = TemperatureHistoryStore.decode(TemperatureHistoryStore.encode(records))
        assertEquals(records, decoded)
    }

    @Test
    fun skipsCorruptedLines() {
        val raw = """
            1700000000000,33.25
            broken-line
            1700000060000,not-a-number
            ,12.0
            1700000120000,34.5
        """.trimIndent()
        val decoded = TemperatureHistoryStore.decode(raw)
        assertEquals(2, decoded.size)
        assertEquals(1_700_000_000_000L, decoded[0].timeMillis)
        assertEquals(34.5f, decoded[1].skinCelsius, 0.001f)
    }

    @Test
    fun handlesNullAndBlankPayload() {
        assertTrue(TemperatureHistoryStore.decode(null).isEmpty())
        assertTrue(TemperatureHistoryStore.decode("   ").isEmpty())
    }

    @Test
    fun encodedPayloadIsCompact() {
        val records = (1..100).map { TemperatureRecord(it * 60_000L, 33.5f) }
        val encoded = TemperatureHistoryStore.encode(records)
        // 每行 "时间戳,温度" 约 20 字符，100 条应远小于 JSON 数组的体积。
        assertTrue("编码体积过大：${encoded.length}", encoded.length < 3000)
        assertEquals(100, TemperatureHistoryStore.decode(encoded).size)
    }
}
