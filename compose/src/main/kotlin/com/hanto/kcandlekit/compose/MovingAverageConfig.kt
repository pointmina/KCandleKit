package com.hanto.kcandlekit.compose

import androidx.compose.ui.graphics.Color

/**
 * 이동 평균선 하나의 시각 설정.
 *
 * @param period      이동 평균 기간 (ex: 5, 20, 60)
 * @param color       선 색상
 * @param strokeWidth 선 굵기 (px)
 */
data class MovingAverageConfig(
    val period: Int,
    val color: Color,
    val strokeWidth: Float = 1.5f,
)
