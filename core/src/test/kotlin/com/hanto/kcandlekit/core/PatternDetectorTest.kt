package com.hanto.kcandlekit.core

import org.junit.Assert.assertTrue
import org.junit.Test

class PatternDetectorTest {

    @Test
    fun `detect returns empty list for empty candles`() {
        val result = PatternDetector.detect(emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `detect returns empty list for single candle`() {
        val candle = Candle(timestamp = 0L, open = 100f, high = 105f, low = 95f, close = 102f)
        val result = PatternDetector.detect(listOf(candle))
        assertTrue(result.isEmpty())
    }
}
