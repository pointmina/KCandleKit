package com.hanto.kcandlekit.core

private fun strengthOf(pattern: CandlePattern) = when (pattern) {
    CandlePattern.BULLISH_ENGULFING,
    CandlePattern.MORNING_STAR,
    CandlePattern.BEARISH_ENGULFING,
    CandlePattern.EVENING_STAR,
    CandlePattern.HAMMER,
    CandlePattern.THREE_WHITE_SOLDIERS,
    CandlePattern.SHOOTING_STAR,
    CandlePattern.THREE_BLACK_CROWS -> SignalStrength.STRONG
    else                            -> SignalStrength.NORMAL
}

object PatternDetector {

    // 꼬리가 몸통의 몇 배 이상이어야 유효한 꼬리로 인정
    private const val WICK_BODY_RATIO = 2.0f

    // 반대편 꼬리 최대 허용 (몸통 대비) — 업계 표준 0.3 (TA-Lib 동일)
    // 구 값 0.1은 유효 패턴의 70% 이상을 거부하는 과도한 기준이었음
    private const val MAX_OPPOSITE_WICK_RATIO = 0.3f

    // 도지 판정: 몸통이 전체 범위의 이 비율 이하
    private const val DOJI_BODY_RATIO = 0.1f

    // 추세 판정에 사용할 SMA 기간
    // 5봉 분할 비교 방식은 단봉 급등락에 즉시 추세가 뒤집히는 노이즈 문제가 있어 SMA20으로 교체
    private const val TREND_LOOKBACK = 20

    // 추세 기울기 확인용 오프셋 (현재 SMA vs 4봉 전 SMA 비교)
    private const val TREND_SLOPE_OFFSET = 4

    // 평균 몸통 계산 기간 — 변동성 국면에 따른 적응형 임계값 기준
    private const val AVG_BODY_LOOKBACK = 14

    // 패턴 캔들의 전체 범위 최소 비율 (avgBody 대비) — 소형/노이즈 캔들 패턴 차단
    private const val MIN_RANGE_RATIO = 0.5f

    fun detect(candles: List<Candle>): List<PatternResult> {
        if (candles.size < 2) return emptyList()

        return candles.indices.mapNotNull { index ->
            when {
                // 복합 패턴 (신뢰도 높음) — 단일 패턴보다 먼저 평가
                isThreeWhiteSoldiers(candles, index) ->
                    PatternResult(index, CandlePattern.THREE_WHITE_SOLDIERS, Signal.BULLISH, span = 3, strength = strengthOf(CandlePattern.THREE_WHITE_SOLDIERS))
                isThreeBlackCrows(candles, index) ->
                    PatternResult(index, CandlePattern.THREE_BLACK_CROWS, Signal.BEARISH, span = 3, strength = strengthOf(CandlePattern.THREE_BLACK_CROWS))
                isMorningStar(candles, index) ->
                    PatternResult(index, CandlePattern.MORNING_STAR, Signal.BULLISH, span = 3, strength = strengthOf(CandlePattern.MORNING_STAR))
                isEveningStar(candles, index) ->
                    PatternResult(index, CandlePattern.EVENING_STAR, Signal.BEARISH, span = 3, strength = strengthOf(CandlePattern.EVENING_STAR))
                isBullishEngulfing(candles, index) ->
                    PatternResult(index, CandlePattern.BULLISH_ENGULFING, Signal.BULLISH, span = 2, strength = strengthOf(CandlePattern.BULLISH_ENGULFING))
                isBearishEngulfing(candles, index) ->
                    PatternResult(index, CandlePattern.BEARISH_ENGULFING, Signal.BEARISH, span = 2, strength = strengthOf(CandlePattern.BEARISH_ENGULFING))
                // 단일 캔들 패턴
                isDoji(candles, index) ->
                    PatternResult(index, CandlePattern.DOJI, Signal.NEUTRAL, strength = strengthOf(CandlePattern.DOJI))
                isHammer(candles, index) ->
                    PatternResult(index, CandlePattern.HAMMER, Signal.BULLISH, strength = strengthOf(CandlePattern.HAMMER))
                isHangingMan(candles, index) ->
                    PatternResult(index, CandlePattern.HANGING_MAN, Signal.BEARISH, strength = strengthOf(CandlePattern.HANGING_MAN))
                isInvertedHammer(candles, index) ->
                    PatternResult(index, CandlePattern.INVERTED_HAMMER, Signal.BULLISH, strength = strengthOf(CandlePattern.INVERTED_HAMMER))
                isShootingStar(candles, index) ->
                    PatternResult(index, CandlePattern.SHOOTING_STAR, Signal.BEARISH, strength = strengthOf(CandlePattern.SHOOTING_STAR))
                else -> null
            }
        }
    }

    // 직전 AVG_BODY_LOOKBACK개 캔들 몸통의 평균 — 현재 봉은 제외(미래 데이터 오염 방지)
    // 데이터 부족 시(시작 구간) 0에 가까운 값 대신 안전한 최솟값 반환
    private fun avgBody(candles: List<Candle>, index: Int): Float {
        val start = maxOf(0, index - AVG_BODY_LOOKBACK)
        val window = candles.subList(start, index)
        if (window.isEmpty()) return 1e-6f
        return window.map { it.bodySize }.average().toFloat().coerceAtLeast(1e-6f)
    }

    // 단일 캔들이 최소한의 의미 있는 범위를 가지는지 확인 (노이즈 봉 제거)
    private fun hasSignificantRange(candles: List<Candle>, index: Int): Boolean =
        candles[index].totalRange >= avgBody(candles, index) * MIN_RANGE_RATIO

    // 도지: 몸통이 전체 범위의 10% 이하
    private fun isDoji(candles: List<Candle>, index: Int): Boolean {
        val c = candles[index]
        if (c.totalRange == 0f) return false
        return c.bodySize / c.totalRange <= DOJI_BODY_RATIO
    }

    // 해머: 긴 아래꼬리 + 짧은 위꼬리 + 하락 추세
    private fun isHammer(candles: List<Candle>, index: Int): Boolean {
        val c = candles[index]
        if (c.bodySize == 0f || !hasSignificantRange(candles, index)) return false
        return c.lowerWick >= c.bodySize * WICK_BODY_RATIO
            && c.upperWick <= c.bodySize * MAX_OPPOSITE_WICK_RATIO
            && isDowntrend(candles, index)
    }

    // 행잉맨: 해머와 동일 형태 + 상승 추세
    private fun isHangingMan(candles: List<Candle>, index: Int): Boolean {
        val c = candles[index]
        if (c.bodySize == 0f || !hasSignificantRange(candles, index)) return false
        return c.lowerWick >= c.bodySize * WICK_BODY_RATIO
            && c.upperWick <= c.bodySize * MAX_OPPOSITE_WICK_RATIO
            && isUptrend(candles, index)
    }

    // 역해머: 긴 위꼬리 + 짧은 아래꼬리 + 하락 추세
    private fun isInvertedHammer(candles: List<Candle>, index: Int): Boolean {
        val c = candles[index]
        if (c.bodySize == 0f || !hasSignificantRange(candles, index)) return false
        return c.upperWick >= c.bodySize * WICK_BODY_RATIO
            && c.lowerWick <= c.bodySize * MAX_OPPOSITE_WICK_RATIO
            && isDowntrend(candles, index)
    }

    // 슈팅스타: 긴 위꼬리 + 짧은 아래꼬리 + 상승 추세
    private fun isShootingStar(candles: List<Candle>, index: Int): Boolean {
        val c = candles[index]
        if (c.bodySize == 0f || !hasSignificantRange(candles, index)) return false
        return c.upperWick >= c.bodySize * WICK_BODY_RATIO
            && c.lowerWick <= c.bodySize * MAX_OPPOSITE_WICK_RATIO
            && isUptrend(candles, index)
    }

    // 불리시 엔걸핑: 이전 음봉을 완전히 감싸는 양봉 + 하락 추세
    // avgBody 기준으로 장악 양봉이 최근 평균 이상 크기여야 함 — 소형 장악은 신호 신뢰도 낮음
    private fun isBullishEngulfing(candles: List<Candle>, index: Int): Boolean {
        if (index < 1) return false
        val prev = candles[index - 1]
        val curr = candles[index]
        if (prev.isBullish || !curr.isBullish) return false
        if (curr.open >= prev.close || curr.close <= prev.open) return false
        return curr.bodySize >= avgBody(candles, index) && isDowntrend(candles, index - 1)
    }

    // 베어리시 엔걸핑: 이전 양봉을 완전히 감싸는 음봉 + 상승 추세
    private fun isBearishEngulfing(candles: List<Candle>, index: Int): Boolean {
        if (index < 1) return false
        val prev = candles[index - 1]
        val curr = candles[index]
        if (!prev.isBullish || curr.isBullish) return false
        if (curr.open <= prev.close || curr.close >= prev.open) return false
        return curr.bodySize >= avgBody(candles, index) && isUptrend(candles, index - 1)
    }

    // 모닝 스타: 큰 음봉 → 작은 별 → 큰 양봉 + 하락 추세
    // c0 몸통이 avgBody 이상이어야 "의미 있는 하락"으로 인정
    private fun isMorningStar(candles: List<Candle>, index: Int): Boolean {
        if (index < 2) return false
        val c0 = candles[index - 2]   // 큰 음봉
        val c1 = candles[index - 1]   // 작은 별
        val c2 = candles[index]        // 큰 양봉
        val avg = avgBody(candles, index - 2)
        if (c0.isBullish) return false
        if (c0.bodySize < c0.totalRange * 0.3f) return false  // c0: 형태 조건
        if (c0.bodySize < avg) return false                    // c0: 맥락 조건 — 평균 이상 크기
        if (c1.bodySize > c0.bodySize * 0.3f) return false    // c1: c0 대비 소형
        if (!c2.isBullish) return false
        if (c2.close < (c0.open + c0.close) / 2f) return false
        if (c2.bodySize < c0.bodySize * 0.5f) return false
        return isDowntrend(candles, index - 2)
    }

    // 이브닝 스타: 큰 양봉 → 작은 별 → 큰 음봉 + 상승 추세
    private fun isEveningStar(candles: List<Candle>, index: Int): Boolean {
        if (index < 2) return false
        val c0 = candles[index - 2]
        val c1 = candles[index - 1]
        val c2 = candles[index]
        val avg = avgBody(candles, index - 2)
        if (!c0.isBullish) return false
        if (c0.bodySize < c0.totalRange * 0.3f) return false
        if (c0.bodySize < avg) return false
        if (c1.bodySize > c0.bodySize * 0.3f) return false
        if (c2.isBullish) return false
        if (c2.close > (c0.open + c0.close) / 2f) return false
        if (c2.bodySize < c0.bodySize * 0.5f) return false
        return isUptrend(candles, index - 2)
    }

    // 쓰리 화이트 솔져스: 3개 연속 양봉, 각 시가가 전봉 몸통 내부에서 출발하며 종가 상승 + 하락 추세
    private fun isThreeWhiteSoldiers(candles: List<Candle>, index: Int): Boolean {
        if (index < 2) return false
        val c0 = candles[index - 2]
        val c1 = candles[index - 1]
        val c2 = candles[index]
        if (!c0.isBullish || !c1.isBullish || !c2.isBullish) return false
        if (c1.close <= c0.close || c2.close <= c1.close) return false
        if (c1.open < c0.open || c1.open > c0.close) return false
        if (c2.open < c1.open || c2.open > c1.close) return false
        if (c0.totalRange > 0f && c0.bodySize / c0.totalRange < 0.5f) return false
        if (c1.totalRange > 0f && c1.bodySize / c1.totalRange < 0.5f) return false
        if (c2.totalRange > 0f && c2.bodySize / c2.totalRange < 0.5f) return false
        return isDowntrend(candles, index - 2)
    }

    // 흑삼병: 음봉 3개가 연속 하락 마감 + 각 캔들이 직전 몸통 안에서 시가 형성 + 상승 추세
    private fun isThreeBlackCrows(candles: List<Candle>, index: Int): Boolean {
        if (index < 2) return false
        val c0 = candles[index - 2]
        val c1 = candles[index - 1]
        val c2 = candles[index]
        if (c0.isBullish || c1.isBullish || c2.isBullish) return false
        if (c1.close >= c0.close || c2.close >= c1.close) return false
        // 각 캔들의 시가가 직전 음봉 몸통 안에서 출발 (c0.close <= c1.open <= c0.open)
        if (c1.open > c0.open || c1.open < c0.close) return false
        if (c2.open > c1.open || c2.open < c1.close) return false
        // 실체 비중이 전체 범위의 50% 이상 (작은 도지 제외)
        if (c0.totalRange > 0f && c0.bodySize / c0.totalRange < 0.5f) return false
        if (c1.totalRange > 0f && c1.bodySize / c1.totalRange < 0.5f) return false
        if (c2.totalRange > 0f && c2.bodySize / c2.totalRange < 0.5f) return false
        return isUptrend(candles, index - 2)
    }

    // SMA20 기반 하락 추세 판정 — 이중 조건: 가격 위치 + SMA 기울기
    // 구 방식(5봉 분할 평균)은 단봉 급등락에 추세 방향이 즉시 뒤집히는 문제가 있었음
    private fun isDowntrend(candles: List<Candle>, index: Int): Boolean {
        if (index < TREND_LOOKBACK) return false
        val sma = candles.subList(index - TREND_LOOKBACK + 1, index + 1)
            .map { it.close.toDouble() }.average()
        if (candles[index].close >= sma) return false   // 가격이 SMA 아래에 있어야 함
        // SMA 기울기 확인: 현재 SMA < 과거 SMA (하락하는 추세선)
        if (index >= TREND_LOOKBACK + TREND_SLOPE_OFFSET) {
            val smaPrev = candles
                .subList(index - TREND_LOOKBACK - TREND_SLOPE_OFFSET, index - TREND_SLOPE_OFFSET)
                .map { it.close.toDouble() }.average()
            return sma < smaPrev
        }
        return true  // 기울기 계산 데이터 부족 시 가격 위치만으로 판정
    }

    private fun isUptrend(candles: List<Candle>, index: Int): Boolean {
        if (index < TREND_LOOKBACK) return false
        val sma = candles.subList(index - TREND_LOOKBACK + 1, index + 1)
            .map { it.close.toDouble() }.average()
        if (candles[index].close <= sma) return false
        if (index >= TREND_LOOKBACK + TREND_SLOPE_OFFSET) {
            val smaPrev = candles
                .subList(index - TREND_LOOKBACK - TREND_SLOPE_OFFSET, index - TREND_SLOPE_OFFSET)
                .map { it.close.toDouble() }.average()
            return sma > smaPrev
        }
        return true
    }
}