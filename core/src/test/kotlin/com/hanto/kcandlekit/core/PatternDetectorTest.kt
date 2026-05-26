package com.hanto.kcandlekit.core

import org.junit.Assert.assertTrue
import org.junit.Test

class PatternDetectorTest {

    // --- 헬퍼 ---

    private fun candle(open: Float, high: Float, low: Float, close: Float) =
        Candle(timestamp = 0L, open = open, high = high, low = low, close = close)

    /** 인덱스 0~4: 완만한 하락 추세 컨텍스트 */
    private fun downtrendContext() = listOf(
        candle(110f, 112f, 108f, 108f),
        candle(108f, 110f, 106f, 106f),
        candle(106f, 108f, 104f, 104f),
        candle(104f, 106f, 102f, 102f),
        candle(102f, 104f, 100f, 100f),
    )

    /** 인덱스 0~4: 완만한 상승 추세 컨텍스트 */
    private fun uptrendContext() = listOf(
        candle(100f, 102f, 99f, 102f),
        candle(102f, 104f, 101f, 104f),
        candle(104f, 106f, 103f, 106f),
        candle(106f, 108f, 105f, 108f),
        candle(108f, 110f, 107f, 110f),
    )

    // --- 경계 조건 ---

    @Test
    fun `detect returns empty list for empty candles`() {
        assertTrue(PatternDetector.detect(emptyList()).isEmpty())
    }

    @Test
    fun `detect returns empty list for single candle`() {
        val result = PatternDetector.detect(listOf(candle(100f, 105f, 95f, 102f)))
        assertTrue(result.isEmpty())
    }

    // --- 도지 ---

    @Test
    fun `detects doji when body is less than 10 percent of range`() {
        // bodySize = 0.5, totalRange = 20 → 2.5% < 10%
        val doji = candle(open = 100f, high = 110f, low = 90f, close = 100.5f)
        val patterns = PatternDetector.detect(downtrendContext() + doji)
        assertTrue(patterns.any { it.pattern == CandlePattern.DOJI && it.index == 5 })
    }

    @Test
    fun `does not detect doji when body is large`() {
        // bodySize = 5, totalRange = 10 → 50%
        val notDoji = candle(open = 100f, high = 110f, low = 100f, close = 105f)
        val patterns = PatternDetector.detect(downtrendContext() + notDoji)
        assertTrue(patterns.none { it.pattern == CandlePattern.DOJI && it.index == 5 })
    }

    // --- 해머 ---

    @Test
    fun `detects hammer in downtrend`() {
        // lowerWick=9.8, bodySize=2, upperWick=0.1 → lowerWick/bodySize = 4.9 > 2.0
        val hammer = candle(open = 100f, high = 102.1f, low = 90.2f, close = 102f)
        val patterns = PatternDetector.detect(downtrendContext() + hammer)
        assertTrue(patterns.any { it.pattern == CandlePattern.HAMMER })
    }

    @Test
    fun `does not detect hammer in uptrend`() {
        val hammer = candle(open = 100f, high = 102.1f, low = 90.2f, close = 102f)
        val patterns = PatternDetector.detect(uptrendContext() + hammer)
        assertTrue(patterns.none { it.pattern == CandlePattern.HAMMER })
    }

    // --- 행잉맨 ---

    @Test
    fun `detects hanging man in uptrend`() {
        // 해머와 동일한 형태, 상승 추세에서 등장하면 행잉맨
        val hangingMan = candle(open = 100f, high = 102.1f, low = 90.2f, close = 102f)
        val patterns = PatternDetector.detect(uptrendContext() + hangingMan)
        assertTrue(patterns.any { it.pattern == CandlePattern.HANGING_MAN })
    }

    // --- 슈팅스타 ---

    @Test
    fun `detects shooting star in uptrend`() {
        // upperWick=10, bodySize=2, lowerWick=0.2 → upperWick/bodySize = 5.0 > 2.0
        val shootingStar = candle(open = 110f, high = 120f, low = 107.8f, close = 108f)
        val patterns = PatternDetector.detect(uptrendContext() + shootingStar)
        assertTrue(patterns.any { it.pattern == CandlePattern.SHOOTING_STAR })
    }

    @Test
    fun `does not detect shooting star in downtrend`() {
        val shootingStar = candle(open = 110f, high = 120f, low = 107.8f, close = 108f)
        val patterns = PatternDetector.detect(downtrendContext() + shootingStar)
        assertTrue(patterns.none { it.pattern == CandlePattern.SHOOTING_STAR })
    }

    // --- 역해머 ---

    @Test
    fun `detects inverted hammer in downtrend`() {
        // 슈팅스타와 동일한 형태, 하락 추세에서 등장하면 역해머
        val invertedHammer = candle(open = 100f, high = 110f, low = 99.8f, close = 98f)
        val patterns = PatternDetector.detect(downtrendContext() + invertedHammer)
        assertTrue(patterns.any { it.pattern == CandlePattern.INVERTED_HAMMER })
    }

    // --- 신호 방향 ---

    @Test
    fun `hammer signal is BULLISH`() {
        val hammer = candle(open = 100f, high = 102.1f, low = 90.2f, close = 102f)
        val patterns = PatternDetector.detect(downtrendContext() + hammer)
        val result = patterns.first { it.pattern == CandlePattern.HAMMER }
        assertTrue(result.signal == Signal.BULLISH)
    }

    @Test
    fun `shooting star signal is BEARISH`() {
        val shootingStar = candle(open = 110f, high = 120f, low = 107.8f, close = 108f)
        val patterns = PatternDetector.detect(uptrendContext() + shootingStar)
        val result = patterns.first { it.pattern == CandlePattern.SHOOTING_STAR }
        assertTrue(result.signal == Signal.BEARISH)
    }
}
