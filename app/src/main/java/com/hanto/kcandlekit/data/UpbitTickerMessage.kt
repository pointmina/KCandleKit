package com.hanto.kcandlekit.data

import com.google.gson.annotations.SerializedName

// Upbit WebSocket ticker 응답.
// high_price / low_price 는 일별 집계값이므로 제외.
// per-candle 고저는 ViewModel에서 trade_price 누적으로 추적.
data class UpbitTickerMessage(
    @SerializedName("type")            val type: String,
    @SerializedName("code")            val code: String,
    @SerializedName("trade_price")     val tradePrice: Double,
    @SerializedName("trade_timestamp") val tradeTimestamp: Long,
)
