package com.hanto.kcandlekit.compose

import androidx.compose.ui.graphics.Color

data class CandleChartConfig(
    // 캔들 색상
    val bullishColor: Color = Color(0xFF26A69A),
    val bearishColor: Color = Color(0xFFEF5350),

    // 배경 / 그리드
    val backgroundColor: Color = Color(0xFF131722),  // TradingView 다크 스타일
    val gridColor: Color = Color(0xFF1E2130),
    val priceTextColor: Color = Color(0xFF787B86),
    val showGrid: Boolean = true,
    val gridLineCount: Int = 4,

    // 패턴
    val showPatternMarkers: Boolean = true,
    val showPatternLabels: Boolean = true,

    // 거래량
    val showVolume: Boolean = true,

    val candleWidthRatio: Float = 0.6f,
)
