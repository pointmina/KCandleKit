package com.hanto.kcandlekit.data

import com.hanto.kcandlekit.core.Candle
import com.hanto.kcandlekit.core.CandleIntervalSpec
import com.hanto.kcandlekit.core.CandleRepository
import com.hanto.kcandlekit.core.CandleUnit
import com.hanto.kcandlekit.core.MarketSpec

class UpbitCandleRepository(private val api: UpbitApiService) : CandleRepository {

    override suspend fun getCandles(market: MarketSpec, interval: CandleIntervalSpec): List<Candle> =
        when (interval.unit) {
            CandleUnit.MINUTE -> api.getMinuteCandles(interval.minuteValue, market.id, interval.count)
            CandleUnit.DAY    -> api.getCandles("days",   market.id, interval.count)
            CandleUnit.WEEK   -> api.getCandles("weeks",  market.id, interval.count)
            CandleUnit.MONTH  -> api.getCandles("months", market.id, interval.count)
        }.reversed().map { it.toCandle() }
}
