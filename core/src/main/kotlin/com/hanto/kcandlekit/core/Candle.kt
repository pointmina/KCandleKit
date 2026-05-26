package com.hanto.kcandlekit.core

data class Candle(
    val timestamp: Long,
    val open: Float,
    val high: Float,
    val low: Float,
    val close: Float,
    val volume: Long = 0L
)

// --- 계산용 확장 프로퍼티 (패턴 인식에서 반복 사용) ---

val Candle.isBullish: Boolean get() = close >= open

val Candle.bodySize: Float get() = kotlin.math.abs(close - open)

// 위꼬리: 고가 ~ 몸통 상단
val Candle.upperWick: Float get() = high - maxOf(open, close)

// 아래꼬리: 몸통 하단 ~ 저가
val Candle.lowerWick: Float get() = minOf(open, close) - low

// 전체 범위: 고가 ~ 저가
val Candle.totalRange: Float get() = high - low
