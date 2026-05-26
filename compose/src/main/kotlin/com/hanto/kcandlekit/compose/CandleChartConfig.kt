package com.hanto.kcandlekit.compose

import androidx.compose.ui.graphics.Color

data class CandleChartConfig(
    val bullishColor: Color = Color(0xFF26A69A),   // 상승봉: 틸 그린 (국제 금융 차트 표준)
    val bearishColor: Color = Color(0xFFEF5350),   // 하락봉: 레드 (국제 금융 차트 표준)
    val showPatternMarkers: Boolean = true,
    val showVolume: Boolean = true,
    val candleWidthRatio: Float = 0.6f             // 전체 슬롯 너비 대비 바디 너비 비율
)
