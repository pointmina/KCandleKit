package com.hanto.kcandlekit.compose

import androidx.compose.ui.graphics.Color

data class CandleChartConfig(
    // 캔들 색상
    val bullishColor: Color = Color(0xFF26A69A),
    val bearishColor: Color = Color(0xFFEF5350),

    // 배경 / 그리드
    val backgroundColor: Color = Color(0xFF131722),
    val gridColor: Color = Color(0xFF1E2130),
    val priceTextColor: Color = Color(0xFF787B86),
    val showGrid: Boolean = true,
    val gridLineCount: Int = 4,

    // 패턴 마커
    val showPatternMarkers: Boolean = true,
    val showPatternLabels: Boolean = false,
    val showStrongSignalBadge: Boolean = true,

    // 거래량
    val showVolume: Boolean = true,

    // 이동 평균선 (기본: MA5 핑크 / MA20 블루 / MA60 그린)
    val movingAverages: List<MovingAverageConfig> = listOf(
        MovingAverageConfig(5,  Color(0xFFF48FB1)),
        MovingAverageConfig(20, Color(0xFF90CAF9)),
        MovingAverageConfig(60, Color(0xFFA5D6A7)),
    ),

    // 크로스헤어 (길게 누른 채 드래그)
    val showCrosshair: Boolean = true,
    val crosshairColor: Color = Color(0xFFB2B5BE),

    // 시간 축 (X축 날짜 레이블)
    val showTimeAxis: Boolean = true,
    val timeAxisTextColor: Color = Color(0xFF787B86),

    val candleWidthRatio: Float = 0.6f,
)
