package com.hanto.kcandlekit.core

/**
 * 기술적 지표 계산 유틸리티.
 * 순수 Kotlin — Android/Compose 의존성 없음.
 */
object Indicators {

    /**
     * 단순 이동 평균 (SMA).
     *
     * @param candles 전체 캔들 리스트
     * @param period  이동 평균 기간 (ex: 5, 20, 60)
     * @return 각 인덱스의 MA 값. 데이터가 부족한 앞 구간은 null.
     */
    fun movingAverage(candles: List<Candle>, period: Int): List<Float?> {
        require(period > 0) { "period must be positive" }
        if (candles.isEmpty()) return emptyList()

        // 누적합으로 O(n) 계산 — subList + average() 반복보다 효율적
        val result = ArrayList<Float?>(candles.size)
        var windowSum = 0.0
        for (i in candles.indices) {
            windowSum += candles[i].close
            if (i >= period) windowSum -= candles[i - period].close
            result += if (i < period - 1) null else (windowSum / period).toFloat()
        }
        return result
    }
}
