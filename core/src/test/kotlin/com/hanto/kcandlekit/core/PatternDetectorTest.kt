package com.hanto.kcandlekit.core

import org.junit.Assert.assertTrue
import org.junit.Test

class PatternDetectorTest {

    // --- 헬퍼 ---

    private fun candle(open: Float, high: Float, low: Float, close: Float) =
        Candle(timestamp = 0L, open = open, high = high, low = low, close = close)

    /**
     * 25봉 하락 추세 컨텍스트 — close: 148 → 100 (2씩 감소)
     *
     * SMA20 기반 추세 판정에는 최소 20봉이 필요하며,
     * 기울기 확인(4봉 오프셋)까지 포함하면 24봉 이상을 권장.
     * 25봉을 표준으로 사용해 모든 패턴의 추세 판정이 안정적으로 작동하도록 한다.
     */
    private fun downtrendContext(): List<Candle> = (0 until 25).map { i ->
        val close = 148f - 2f * i          // 148, 146, ..., 100
        candle(close + 2f, close + 3f, close - 1f, close)
    }

    /**
     * 25봉 상승 추세 컨텍스트 — close: 62 → 110 (2씩 증가)
     */
    private fun uptrendContext(): List<Candle> = (0 until 25).map { i ->
        val close = 62f + 2f * i           // 62, 64, ..., 110
        candle(close - 2f, close + 1f, close - 3f, close)
    }

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
        assertTrue(patterns.any { it.pattern == CandlePattern.DOJI && it.index == 25 })
    }

    @Test
    fun `does not detect doji when body is large`() {
        // bodySize = 5, totalRange = 10 → 50%
        val notDoji = candle(open = 100f, high = 110f, low = 100f, close = 105f)
        val patterns = PatternDetector.detect(downtrendContext() + notDoji)
        assertTrue(patterns.none { it.pattern == CandlePattern.DOJI && it.index == 25 })
    }

    // --- 해머 ---

    @Test
    fun `detects hammer in downtrend`() {
        // lowerWick=9.8, bodySize=2, upperWick=0.1 → lowerWick/bodySize=4.9 > 2.0
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
        // upperWick=10, bodySize=2, lowerWick=0.2 → upperWick/bodySize=5.0 > 2.0
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
        // body=2, upperWick=10, lowerWick=0.5(=body*0.25 < 0.3 허용 범위)
        // 주의: 구 테스트 데이터(low=99.8, close=98)는 low>close로 OHLC 불변식 위반이었음
        val invertedHammer = candle(open = 100f, high = 110f, low = 97.5f, close = 98f)
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
    fun `hammer strength is NORMAL`() {
        val hammer = candle(open = 100f, high = 102.1f, low = 90.2f, close = 102f)
        val patterns = PatternDetector.detect(downtrendContext() + hammer)
        val result = patterns.first { it.pattern == CandlePattern.HAMMER }
        assertTrue(result.strength == SignalStrength.NORMAL)
    }

    @Test
    fun `inverted hammer strength is NORMAL`() {
        val invertedHammer = candle(open = 100f, high = 110f, low = 97.5f, close = 98f)
        val patterns = PatternDetector.detect(downtrendContext() + invertedHammer)
        val result = patterns.first { it.pattern == CandlePattern.INVERTED_HAMMER }
        assertTrue(result.strength == SignalStrength.NORMAL)
    }

    @Test
    fun `shooting star signal is BEARISH`() {
        val shootingStar = candle(open = 110f, high = 120f, low = 107.8f, close = 108f)
        val patterns = PatternDetector.detect(uptrendContext() + shootingStar)
        val result = patterns.first { it.pattern == CandlePattern.SHOOTING_STAR }
        assertTrue(result.signal == Signal.BEARISH)
    }

    @Test
    fun `shooting star strength is NORMAL`() {
        val shootingStar = candle(open = 110f, high = 120f, low = 107.8f, close = 108f)
        val patterns = PatternDetector.detect(uptrendContext() + shootingStar)
        val result = patterns.first { it.pattern == CandlePattern.SHOOTING_STAR }
        assertTrue(result.strength == SignalStrength.NORMAL)
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

    @Test
    fun `three white soldiers strength is STRONG`() {
        val c0 = candle(open = 100f, high = 106f, low = 99f, close = 105f)
        val c1 = candle(open = 102f, high = 109f, low = 101f, close = 108f)
        val c2 = candle(open = 105f, high = 113f, low = 104f, close = 112f)
        val patterns = PatternDetector.detect(downtrendContext() + c0 + c1 + c2)
        val result = patterns.first { it.pattern == CandlePattern.THREE_WHITE_SOLDIERS }
        assertTrue(result.strength == SignalStrength.STRONG)
    }

    // --- 흑삼병 ---

    @Test
    fun `detects three black crows in uptrend`() {
        // c0: 음봉 open=110, close=105 (body=5, range=7 → 71%)
        // c1: 음봉 open=108(c0 몸통 내), close=102 (body=6, range=8)
        // c2: 음봉 open=105(c1 몸통 내), close=98  (body=7, range=9)
        val c0 = candle(open = 110f, high = 111f, low = 104f, close = 105f)
        val c1 = candle(open = 108f, high = 109f, low = 101f, close = 102f)
        val c2 = candle(open = 105f, high = 106f, low = 97f,  close = 98f)
        val patterns = PatternDetector.detect(uptrendContext() + c0 + c1 + c2)
        assertTrue(patterns.any { it.pattern == CandlePattern.THREE_BLACK_CROWS })
    }

    @Test
    fun `three black crows has span 3 and STRONG strength`() {
        val c0 = candle(open = 110f, high = 111f, low = 104f, close = 105f)
        val c1 = candle(open = 108f, high = 109f, low = 101f, close = 102f)
        val c2 = candle(open = 105f, high = 106f, low = 97f,  close = 98f)
        val patterns = PatternDetector.detect(uptrendContext() + c0 + c1 + c2)
        val result = patterns.first { it.pattern == CandlePattern.THREE_BLACK_CROWS }
        assertTrue(result.span == 3)
        assertTrue(result.strength == SignalStrength.STRONG)
        assertTrue(result.signal == Signal.BEARISH)
    }

    @Test
    fun `does not detect three black crows in downtrend`() {
        val c0 = candle(open = 110f, high = 111f, low = 104f, close = 105f)
        val c1 = candle(open = 108f, high = 109f, low = 101f, close = 102f)
        val c2 = candle(open = 105f, high = 106f, low = 97f,  close = 98f)
        val patterns = PatternDetector.detect(downtrendContext() + c0 + c1 + c2)
        assertTrue(patterns.none { it.pattern == CandlePattern.THREE_BLACK_CROWS })
    }
}
