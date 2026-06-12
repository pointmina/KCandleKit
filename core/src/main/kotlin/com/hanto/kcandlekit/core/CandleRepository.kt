package com.hanto.kcandlekit.core

interface CandleRepository {
    suspend fun getCandles(market: MarketSpec, interval: CandleIntervalSpec): List<Candle>
}
