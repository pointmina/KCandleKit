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
