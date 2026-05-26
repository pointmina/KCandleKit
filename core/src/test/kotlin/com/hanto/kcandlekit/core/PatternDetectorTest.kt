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

    // --- 불리시 엔걸핑 ---

    @Test
    fun `detects bullish engulfing in downtrend`() {
        // prev: 음봉 (open=105, close=100, body=5)
        // curr: 양봉 (open=99, close=106, body=7) — prev 몸통 완전히 감쌈
        val prev = candle(open = 105f, high = 106f, low = 99f, close = 100f)
        val curr = candle(open = 99f, high = 107f, low = 98f, close = 106f)
        val patterns = PatternDetector.detect(downtrendContext() + prev + curr)
        assertTrue(patterns.any { it.pattern == CandlePattern.BULLISH_ENGULFING })
    }

    @Test
    fun `does not detect bullish engulfing in uptrend`() {
        val prev = candle(open = 105f, high = 106f, low = 99f, close = 100f)
        val curr = candle(open = 99f, high = 107f, low = 98f, close = 106f)
        val patterns = PatternDetector.detect(uptrendContext() + prev + curr)
        assertTrue(patterns.none { it.pattern == CandlePattern.BULLISH_ENGULFING })
    }

    @Test
    fun `bullish engulfing has span 2`() {
        val prev = candle(open = 105f, high = 106f, low = 99f, close = 100f)
        val curr = candle(open = 99f, high = 107f, low = 98f, close = 106f)
        val patterns = PatternDetector.detect(downtrendContext() + prev + curr)
        val result = patterns.first { it.pattern == CandlePattern.BULLISH_ENGULFING }
        assertTrue(result.span == 2)
    }

    // --- 베어리시 엔걸핑 ---

    @Test
    fun `detects bearish engulfing in uptrend`() {
        // prev: 양봉 (open=100, close=105, body=5)
        // curr: 음봉 (open=106, close=99, body=7) — prev 몸통 완전히 감쌈
        val prev = candle(open = 100f, high = 106f, low = 99f, close = 105f)
        val curr = candle(open = 106f, high = 107f, low = 98f, close = 99f)
        val patterns = PatternDetector.detect(uptrendContext() + prev + curr)
        assertTrue(patterns.any { it.pattern == CandlePattern.BEARISH_ENGULFING })
    }

    @Test
    fun `bearish engulfing has span 2`() {
        val prev = candle(open = 100f, high = 106f, low = 99f, close = 105f)
        val curr = candle(open = 106f, high = 107f, low = 98f, close = 99f)
        val patterns = PatternDetector.detect(uptrendContext() + prev + curr)
        val result = patterns.first { it.pattern == CandlePattern.BEARISH_ENGULFING }
        assertTrue(result.span == 2)
    }

    // --- 모닝 스타 ---

    @Test
    fun `detects morning star in downtrend`() {
        // c0: 큰 음봉 (body=10, range=12)
        // c1: 작은 별 (body=1)
        // c2: 큰 양봉 (body=8, close=108 ≥ c0 중간값 105)
        val c0 = candle(open = 110f, high = 111f, low = 99f, close = 100f)
        val c1 = candle(open = 99f, high = 101f, low = 97f, close = 100f)
        val c2 = candle(open = 100f, high = 109f, low = 99f, close = 108f)
        val patterns = PatternDetector.detect(downtrendContext() + c0 + c1 + c2)
        assertTrue(patterns.any { it.pattern == CandlePattern.MORNING_STAR })
    }

    @Test
    fun `morning star has span 3`() {
        val c0 = candle(open = 110f, high = 111f, low = 99f, close = 100f)
        val c1 = candle(open = 99f, high = 101f, low = 97f, close = 100f)
        val c2 = candle(open = 100f, high = 109f, low = 99f, close = 108f)
        val patterns = PatternDetector.detect(downtrendContext() + c0 + c1 + c2)
        val result = patterns.first { it.pattern == CandlePattern.MORNING_STAR }
        assertTrue(result.span == 3)
    }

    // --- 이브닝 스타 ---

    @Test
    fun `detects evening star in uptrend`() {
        // c0: 큰 양봉 (body=10, range=12)
        // c1: 작은 별 (body=1)
        // c2: 큰 음봉 (body=8, close=102 ≤ c0 중간값 105)
        val c0 = candle(open = 100f, high = 111f, low = 99f, close = 110f)
        val c1 = candle(open = 111f, high = 113f, low = 109f, close = 110f)
        val c2 = candle(open = 110f, high = 111f, low = 101f, close = 102f)
        val patterns = PatternDetector.detect(uptrendContext() + c0 + c1 + c2)
        assertTrue(patterns.any { it.pattern == CandlePattern.EVENING_STAR })
    }

    @Test
    fun `evening star has span 3`() {
        val c0 = candle(open = 100f, high = 111f, low = 99f, close = 110f)
        val c1 = candle(open = 111f, high = 113f, low = 109f, close = 110f)
        val c2 = candle(open = 110f, high = 111f, low = 101f, close = 102f)
        val patterns = PatternDetector.detect(uptrendContext() + c0 + c1 + c2)
        val result = patterns.first { it.pattern == CandlePattern.EVENING_STAR }
        assertTrue(result.span == 3)
    }

    // --- 쓰리 화이트 솔져스 ---

    @Test
    fun `detects three white soldiers in downtrend`() {
        // c0: 양봉 open=100, close=105 (body=5, range=7 → 71%)
        // c1: 양봉 open=102(c0 몸통 내), close=108 (body=6, range=8 → 75%)
        // c2: 양봉 open=105(c1 몸통 내), close=112 (body=7, range=9 → 78%)
        val c0 = candle(open = 100f, high = 106f, low = 99f, close = 105f)
        val c1 = candle(open = 102f, high = 109f, low = 101f, close = 108f)
        val c2 = candle(open = 105f, high = 113f, low = 104f, close = 112f)
        val patterns = PatternDetector.detect(downtrendContext() + c0 + c1 + c2)
        assertTrue(patterns.any { it.pattern == CandlePattern.THREE_WHITE_SOLDIERS })
    }

    @Test
    fun `three white soldiers has span 3`() {
        val c0 = candle(open = 100f, high = 106f, low = 99f, close = 105f)
        val c1 = candle(open = 102f, high = 109f, low = 101f, close = 108f)
        val c2 = candle(open = 105f, high = 113f, low = 104f, close = 112f)
        val patterns = PatternDetector.detect(downtrendContext() + c0 + c1 + c2)
        val result = patterns.first { it.pattern == CandlePattern.THREE_WHITE_SOLDIERS }
        assertTrue(result.span == 3)
    }
}
