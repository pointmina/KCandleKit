package com.hanto.kcandlekit.core

import kotlinx.coroutines.flow.Flow

/**
 * Optional integration point for real-time price ticks, independent of [CandleRepository].
 *
 * Like [CandleRepository], this imposes no exchange-specific contract — implement it over a
 * WebSocket, polling, or any other push/pull mechanism your data source supports. Omit it
 * entirely if live updates aren't needed.
 */
interface TickerRepository {
    fun priceFlow(market: MarketSpec): Flow<TickerUpdate>
}
