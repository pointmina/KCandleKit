package com.hanto.kcandlekit.data

import com.hanto.kcandlekit.core.MarketSpec
import com.hanto.kcandlekit.core.TickerRepository
import com.hanto.kcandlekit.core.TickerUpdate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import okhttp3.OkHttpClient

class UpbitTickerRepository(private val client: OkHttpClient) : TickerRepository {

    override fun priceFlow(market: MarketSpec): Flow<TickerUpdate> =
        upbitTickerFlow(client, market.id)
            .map { msg -> TickerUpdate(price = msg.tradePrice, timestamp = msg.tradeTimestamp) }
}
