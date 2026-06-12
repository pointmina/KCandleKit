package com.hanto.kcandlekit.core

import kotlin.math.abs

object Indicators {

    /**
     * 단순 이동 평균 (SMA).
     * 데이터가 부족한 앞 구간은 null.
     */
    fun movingAverage(candles: List<Candle>, period: Int): List<Float?> {
        require(period > 0) { "period must be positive" }
        if (candles.isEmpty()) return emptyList()

        val result = ArrayList<Float?>(candles.size)
        var windowSum = 0.0
        for (i in candles.indices) {
            windowSum += candles[i].close
            if (i >= period) windowSum -= candles[i - period].close
            result += if (i < period - 1) null else (windowSum / period).toFloat()
        }
        return result
    }

    /**
     * Average True Range (ATR) — Wilder 방식.
     *
     * True Range = max(high-low, |high-prevClose|, |low-prevClose|)
     * 첫 봉은 prevClose 없으므로 단순 high-low 사용.
     * 초기 ATR은 첫 period개 TR의 단순 평균, 이후 Wilder 지수평활.
     */
    fun atr(candles: List<Candle>, period: Int): List<Float?> {
        require(period > 0) { "period must be positive" }
        if (candles.isEmpty()) return emptyList()

        val result = ArrayList<Float?>(candles.size)
        var atrValue = 0f
        var trSum = 0f

        for (i in candles.indices) {
            val c = candles[i]
            val tr = if (i == 0) {
                c.totalRange
            } else {
                val prevClose = candles[i - 1].close
                maxOf(c.high - c.low, abs(c.high - prevClose), abs(c.low - prevClose))
            }

            when {
                i < period - 1 -> {
                    trSum += tr
                    result += null
                }
                i == period - 1 -> {
                    trSum += tr
                    atrValue = trSum / period
                    result += atrValue
                }
                else -> {
                    // Wilder 지수평활: ATR = (prev * (period-1) + TR) / period
                    atrValue = (atrValue * (period - 1) + tr) / period
                    result += atrValue
                }
            }
        }
        return result
    }

    /**
     * Relative Strength Index (RSI) — Wilder 방식.
     *
     * 초기 AvgGain/Loss는 첫 period개 변화량의 단순 평균,
     * 이후 Wilder 지수평활 적용.
     */
    fun rsi(candles: List<Candle>, period: Int = 14): List<Float?> {
        require(period > 0) { "period must be positive" }
        if (candles.isEmpty()) return emptyList()

        val result = ArrayList<Float?>(candles.size)
        var avgGain = 0f
        var avgLoss = 0f

        for (i in candles.indices) {
            if (i == 0) {
                result += null
                continue
            }
            val change = candles[i].close - candles[i - 1].close
            val gain = if (change > 0f) change else 0f
            val loss = if (change < 0f) -change else 0f

            when {
                i < period -> {
                    avgGain += gain
                    avgLoss += loss
                    result += null
                }
                i == period -> {
                    // 초기 평균: i=1..period 총 period개 변화량
                    avgGain = (avgGain + gain) / period
                    avgLoss = (avgLoss + loss) / period
                    result += calcRsi(avgGain, avgLoss)
                }
                else -> {
                    avgGain = (avgGain * (period - 1) + gain) / period
                    avgLoss = (avgLoss * (period - 1) + loss) / period
                    result += calcRsi(avgGain, avgLoss)
                }
            }
        }
        return result
    }

    private fun calcRsi(avgGain: Float, avgLoss: Float): Float =
        if (avgLoss == 0f) 100f else 100f - (100f / (1f + avgGain / avgLoss))
}
