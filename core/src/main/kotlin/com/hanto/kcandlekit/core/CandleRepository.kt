package com.hanto.kcandlekit.core

/**
 * The single integration point for fetching historical candles from any data source.
 *
 * `core` places no exchange-specific requirements on an implementation — how [MarketSpec.id]
 * and [CandleIntervalSpec] are mapped to a concrete API (REST, WebSocket, local DB, a mock for
 * tests, etc.) is entirely up to you. Implement this once per data source and the rest of
 * KCandleKit (pattern detection, indicators, chart rendering) works unchanged.
 */
interface CandleRepository {
    suspend fun getCandles(market: MarketSpec, interval: CandleIntervalSpec): List<Candle>
}
