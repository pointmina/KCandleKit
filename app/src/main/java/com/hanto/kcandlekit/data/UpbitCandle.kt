package com.hanto.kcandlekit.data

import com.google.gson.annotations.SerializedName
import com.hanto.kcandlekit.core.Candle

/**
 * 업비트 캔들 API 응답 모델.
 * 데이터는 최신 순(내림차순)으로 오므로 사용 측에서 reversed() 필요.
 */
data class UpbitCandle(
    /** 캔들 종료 시각 (Unix ms) */
    @SerializedName("timestamp")
    val timestamp: Long,

    @SerializedName("opening_price")
    val openingPrice: Double,

    @SerializedName("high_price")
    val highPrice: Double,

    @SerializedName("low_price")
    val lowPrice: Double,

    /** 종가 */
    @SerializedName("trade_price")
    val tradePrice: Double,

    /** 누적 거래대금 (KRW) — 볼륨 바 시각화에 사용 */
    @SerializedName("candle_acc_trade_price")
    val accTradePrice: Double,
)

fun UpbitCandle.toCandle() = Candle(
    timestamp   = timestamp,
    open        = openingPrice.toFloat(),
    high        = highPrice.toFloat(),
    low         = lowPrice.toFloat(),
    close       = tradePrice.toFloat(),
    volume      = accTradePrice.toLong().coerceAtLeast(1L),
)
