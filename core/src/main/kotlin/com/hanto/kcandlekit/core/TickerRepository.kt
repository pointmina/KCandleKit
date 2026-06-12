package com.hanto.kcandlekit.core

import kotlinx.coroutines.flow.Flow

interface TickerRepository {
    fun priceFlow(market: MarketSpec): Flow<TickerUpdate>
}
