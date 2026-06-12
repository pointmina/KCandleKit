package com.hanto.kcandlekit.core

data class CandleIntervalSpec(
    val unit: CandleUnit,
    val minuteValue: Int = 1,
    val count: Int = 200,
)
