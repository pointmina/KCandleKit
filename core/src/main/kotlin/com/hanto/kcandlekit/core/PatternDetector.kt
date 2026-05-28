package com.hanto.kcandlekit.core

object PatternDetector {

    // 꼬리가 몸통의 몇 배 이상이어야 유효한 꼬리로 인정
    private const val WICK_BODY_RATIO = 2.0f

    // 반대편 꼬리의 최대 허용 크기 (몸통 대비) — 너무 크면 패턴 무효
    private const val MAX_OPPOSITE_WICK_RATIO = 0.1f

    // 도지 판정: 몸통이 전체 범위의 이 비율 이하이면 매수/매도 세력이 균형 상태
    private const val DOJI_BODY_RATIO = 0.1f

    // 추세 판단에 사용할 이전 캔들 수 — 너무 짧으면 노이즈, 너무 길면 반응 느림
    private const val TREND_LOOKBACK = 5

    fun detect(candles: List<Candle>): List<PatternResult> {
        if (candles.size < 2) return emptyList()

        return candles.indices.mapNotNull { index ->
            when {
                // 복합 패턴 (높은 신뢰도) — 단일 패턴보다 먼저 확인
                isThreeWhiteSoldiers(candles, index) ->
                    PatternResult(index, CandlePattern.THREE_WHITE_SOLDIERS, Signal.BULLISH, span = 3)
                isMorningStar(candles, index) ->
                    PatternResult(index, CandlePattern.MORNING_STAR, Signal.BULLISH, span = 3)
                isEveningStar(candles, index) ->
                    PatternResult(index, CandlePattern.EVENING_STAR, Signal.BEARISH, span = 3)
                isBullishEngulfing(candles, index) ->
                    PatternResult(index, CandlePattern.BULLISH_ENGULFING, Signal.BULLISH, span = 2)
                isBearishEngulfing(candles, index) ->
                    PatternResult(index, CandlePattern.BEARISH_ENGULFING, Signal.BEARISH, span = 2)
                // 단일 캔들 패턴
                isDoji(candles, index) ->
                    PatternResult(index, CandlePattern.DOJI, Signal.NEUTRAL)
                isHammer(candles, index) ->
                    PatternResult(index, CandlePattern.HAMMER, Signal.BULLISH)
                isHangingMan(candles, index) ->
                    PatternResult(index, CandlePattern.HANGING_MAN, Signal.BEARISH)
                isInvertedHammer(candles, index) ->
                    PatternResult(index, CandlePattern.INVERTED_HAMMER, Signal.BULLISH)
                isShootingStar(candles, index) ->
                    PatternResult(index, CandlePattern.SHOOTING_STAR, Signal.BEARISH)
                else -> null
            }
        }
    }

    // 도지: 몸통이 전체 범위의 10% 이하 — 시장 참여자들의 의견 충돌, 방향 전환 가능성
    private fun isDoji(candles: List<Candle>, index: Int): Boolean {
        val c = candles[index]
        if (c.totalRange == 0f) return false
        return c.bodySize / c.totalRange <= DOJI_BODY_RATIO
    }

    // 해머: 긴 아래꼬리 + 짧은 위꼬리 + 하락 추세 — 저점 매수세 유입, 반등 기대
    private fun isHammer(candles: List<Candle>, index: Int): Boolean {
        val c = candles[index]
        if (c.bodySize == 0f) return false
        return c.lowerWick >= c.bodySize * WICK_BODY_RATIO
            && c.upperWick <= c.bodySize * MAX_OPPOSITE_WICK_RATIO
            && isDowntrend(candles, index)
    }

    // 행잉맨: 해머와 동일 형태 + 상승 추세 — 고점에서 매도 압력 첫 출현, 하락 전환 경고
    private fun isHangingMan(candles: List<Candle>, index: Int): Boolean {
        val c = candles[index]
        if (c.bodySize == 0f) return false
        return c.lowerWick >= c.bodySize * WICK_BODY_RATIO
            && c.upperWick <= c.bodySize * MAX_OPPOSITE_WICK_RATIO
            && isUptrend(candles, index)
    }

    // 역해머: 긴 위꼬리 + 짧은 아래꼬리 + 하락 추세 — 매수세의 반등 시도, 다음 캔들 확인 필요
    private fun isInvertedHammer(candles: List<Candle>, index: Int): Boolean {
        val c = candles[index]
        if (c.bodySize == 0f) return false
        return c.upperWick >= c.bodySize * WICK_BODY_RATIO
            && c.lowerWick <= c.bodySize * MAX_OPPOSITE_WICK_RATIO
            && isDowntrend(candles, index)
    }

    // 슈팅스타: 역해머와 동일 형태 + 상승 추세 — 고점에서 매도세 급등, 하락 전환 신호
    private fun isShootingStar(candles: List<Candle>, index: Int): Boolean {
        val c = candles[index]
        if (c.bodySize == 0f) return false
        return c.upperWick >= c.bodySize * WICK_BODY_RATIO
            && c.lowerWick <= c.bodySize * MAX_OPPOSITE_WICK_RATIO
            && isUptrend(candles, index)
    }

    // ── 복합 캔들 패턴 ────────────────────────────────────────────────────────

    // 불리시 엔걸핑: 이전 음봉을 완전히 감싸는 양봉 + 하락 추세 — 강한 매수 전환 신호
    private fun isBullishEngulfing(candles: List<Candle>, index: Int): Boolean {
        if (index < 1) return false
        val prev = candles[index - 1]
        val curr = candles[index]
        if (prev.isBullish || !curr.isBullish) return false          // prev: 음봉, curr: 양봉
        // curr 몸통이 prev 몸통을 완전히 감쌈 (엄격한 부등호 — 경계값은 엔걸핑으로 불인정)
        if (curr.open >= prev.close || curr.close <= prev.open) return false
        // 추세는 패턴 시작(index-1) 기준으로 판단 — 패턴 캔들 자체가 추세 계산을 오염하지 않도록
        return isDowntrend(candles, index - 1)
    }

    // 베어리시 엔걸핑: 이전 양봉을 완전히 감싸는 음봉 + 상승 추세 — 강한 매도 전환 신호
    private fun isBearishEngulfing(candles: List<Candle>, index: Int): Boolean {
        if (index < 1) return false
        val prev = candles[index - 1]
        val curr = candles[index]
        if (!prev.isBullish || curr.isBullish) return false           // prev: 양봉, curr: 음봉
        // curr 몸통이 prev 몸통을 완전히 감쌈 (엄격한 부등호)
        if (curr.open <= prev.close || curr.close >= prev.open) return false
        return isUptrend(candles, index - 1)
    }

    // 모닝 스타: 큰 음봉 → 작은 별 → 큰 양봉 + 하락 추세 — 강세 반전 3캔들 패턴
    private fun isMorningStar(candles: List<Candle>, index: Int): Boolean {
        if (index < 2) return false
        val c0 = candles[index - 2]   // 큰 음봉
        val c1 = candles[index - 1]   // 작은 별 (방향 무관)
        val c2 = candles[index]        // 큰 양봉
        if (c0.isBullish) return false                                // c0: 음봉
        if (c0.bodySize < c0.totalRange * 0.3f) return false         // c0: 의미있는 몸통
        if (c1.bodySize > c0.bodySize * 0.3f) return false           // c1: 별 (몸통 ≤ c0의 30%)
        if (!c2.isBullish) return false                               // c2: 양봉
        if (c2.close < (c0.open + c0.close) / 2f) return false       // c2: c0 몸통 중간 이상 회복
        if (c2.bodySize < c0.bodySize * 0.5f) return false           // c2: c0 절반 이상 몸통
        return isDowntrend(candles, index - 2)                        // 패턴 시작 전 추세 기준
    }

    // 이브닝 스타: 큰 양봉 → 작은 별 → 큰 음봉 + 상승 추세 — 약세 반전 3캔들 패턴
    private fun isEveningStar(candles: List<Candle>, index: Int): Boolean {
        if (index < 2) return false
        val c0 = candles[index - 2]   // 큰 양봉
        val c1 = candles[index - 1]   // 작은 별
        val c2 = candles[index]        // 큰 음봉
        if (!c0.isBullish) return false                               // c0: 양봉
        if (c0.bodySize < c0.totalRange * 0.3f) return false         // c0: 의미있는 몸통
        if (c1.bodySize > c0.bodySize * 0.3f) return false           // c1: 별
        if (c2.isBullish) return false                                // c2: 음봉
        if (c2.close > (c0.open + c0.close) / 2f) return false      // c2: c0 몸통 중간 이하로 하락
        if (c2.bodySize < c0.bodySize * 0.5f) return false           // c2: c0 절반 이상 몸통
        return isUptrend(candles, index - 2)                          // 패턴 시작 전 추세 기준
    }

    // 쓰리 화이트 솔져스: 3개 연속 양봉, 각 캔들이 이전 몸통 안에서 시작해 더 높이 마감 + 하락 추세
    private fun isThreeWhiteSoldiers(candles: List<Candle>, index: Int): Boolean {
        if (index < 2) return false
        val c0 = candles[index - 2]
        val c1 = candles[index - 1]
        val c2 = candles[index]
        if (!c0.isBullish || !c1.isBullish || !c2.isBullish) return false    // 셋 다 양봉
        if (c1.close <= c0.close || c2.close <= c1.close) return false        // 종가 지속 상승
        if (c1.open < c0.open || c1.open > c0.close) return false             // c1 open: c0 몸통 내부
        if (c2.open < c1.open || c2.open > c1.close) return false             // c2 open: c1 몸통 내부
        // 각 캔들의 몸통이 전체 범위의 50% 이상 (꼬리가 지나치게 길지 않아야 함)
        if (c0.totalRange > 0f && c0.bodySize / c0.totalRange < 0.5f) return false
        if (c1.totalRange > 0f && c1.bodySize / c1.totalRange < 0.5f) return false
        if (c2.totalRange > 0f && c2.bodySize / c2.totalRange < 0.5f) return false
        return isDowntrend(candles, index - 2)                        // 패턴 시작 전 추세 기준
    }

    // 직전 TREND_LOOKBACK개 캔들을 전반/후반으로 나눠 후반 평균이 낮으면 하락 추세
    private fun isDowntrend(candles: List<Candle>, index: Int): Boolean {
        if (index < TREND_LOOKBACK) return false
        val mid = index - TREND_LOOKBACK / 2
        val earlyAvg = candles.subList(index - TREND_LOOKBACK, mid).map { it.close.toDouble() }.average()
        val recentAvg = candles.subList(mid, index).map { it.close.toDouble() }.average()
        return recentAvg < earlyAvg
    }

    private fun isUptrend(candles: List<Candle>, index: Int): Boolean {
        if (index < TREND_LOOKBACK) return false
        val mid = index - TREND_LOOKBACK / 2
        val earlyAvg = candles.subList(index - TREND_LOOKBACK, mid).map { it.close.toDouble() }.average()
        val recentAvg = candles.subList(mid, index).map { it.close.toDouble() }.average()
        return recentAvg > earlyAvg
    }
}
