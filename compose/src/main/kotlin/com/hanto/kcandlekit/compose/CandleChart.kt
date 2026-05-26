package com.hanto.kcandlekit.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import com.hanto.kcandlekit.core.Candle
import com.hanto.kcandlekit.core.PatternResult
import com.hanto.kcandlekit.core.isBullish

// 줌 범위: 6px(많은 캔들) ~ 120px(소수 캔들)
private const val MIN_CANDLE_WIDTH = 6f
private const val MAX_CANDLE_WIDTH = 120f
private const val DEFAULT_CANDLE_WIDTH = 20f

// 차트 상하 여백 비율 — 고/저가가 화면 끝에 딱 붙지 않도록
private const val PRICE_PADDING_RATIO = 0.05f

// 거래량 영역이 차트 전체 높이의 25%를 차지
private const val VOLUME_AREA_RATIO = 0.25f

@Composable
fun CandleChart(
    candles: List<Candle>,
    patterns: List<PatternResult> = emptyList(),
    config: CandleChartConfig = CandleChartConfig(),
    modifier: Modifier = Modifier
) {
    var candleWidthPx by remember { mutableFloatStateOf(DEFAULT_CANDLE_WIDTH) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var screenWidth by remember { mutableFloatStateOf(0f) }

    // candles가 바뀔 때마다 최신 캔들(오른쪽 끝)로 초기 스크롤
    var scrollInitialized by remember(candles) { mutableStateOf(false) }
    LaunchedEffect(screenWidth, candles.size) {
        if (screenWidth > 0f && !scrollInitialized) {
            scrollInitialized = true
            val totalWidth = candles.size * DEFAULT_CANDLE_WIDTH
            offsetX = if (totalWidth > screenWidth) screenWidth - totalWidth else 0f
        }
    }

    Canvas(
        modifier = modifier
            .onSizeChanged { screenWidth = it.width.toFloat() }
            .pointerInput(candles.size) {
                detectTransformGestures { _, pan, zoom, _ ->
                    candleWidthPx = (candleWidthPx * zoom).coerceIn(MIN_CANDLE_WIDTH, MAX_CANDLE_WIDTH)
                    val totalWidth = candles.size * candleWidthPx
                    // offsetX <= 0 이고 총 너비를 벗어나지 않도록 양쪽 경계 제한
                    val minOffset = minOf(0f, screenWidth - totalWidth)
                    offsetX = (offsetX + pan.x).coerceIn(minOffset, 0f)
                }
            }
    ) {
        if (candles.isEmpty()) return@Canvas

        val chartHeight = if (config.showVolume) size.height * (1f - VOLUME_AREA_RATIO) else size.height

        // 화면에 보이는 인덱스 범위만 계산 — O(visible) 렌더링
        val firstIdx = (-offsetX / candleWidthPx).toInt().coerceIn(0, candles.lastIndex)
        val lastIdx = (firstIdx + (size.width / candleWidthPx).toInt() + 2)
            .coerceIn(firstIdx, candles.lastIndex)

        // 보이는 캔들 기준으로 Y 스케일 결정 (줌인 시 자동 가격 조정)
        val visibleCandles = candles.subList(firstIdx, lastIdx + 1)
        val rawMin = visibleCandles.minOf { it.low }
        val rawMax = visibleCandles.maxOf { it.high }
        val priceRange = if (rawMax == rawMin) 1f else rawMax - rawMin
        val padding = priceRange * PRICE_PADDING_RATIO
        val minPrice = rawMin - padding
        val scaledRange = (rawMax + padding) - minPrice

        fun priceToY(price: Float) = chartHeight * (1f - (price - minPrice) / scaledRange)

        // 캔들 렌더링
        for (index in firstIdx..lastIdx) {
            val candle = candles[index]
            val centerX = offsetX + (index + 0.5f) * candleWidthPx
            val color = if (candle.isBullish) config.bullishColor else config.bearishColor
            val halfBodyWidth = candleWidthPx * config.candleWidthRatio / 2f

            val openY = priceToY(candle.open)
            val closeY = priceToY(candle.close)
            val bodyTop = minOf(openY, closeY)
            val bodyBottom = maxOf(openY, closeY)

            // 위 심지 (High → 몸통 상단)
            drawLine(
                color = color,
                start = Offset(centerX, priceToY(candle.high)),
                end = Offset(centerX, bodyTop),
                strokeWidth = 1.5f
            )
            // 아래 심지 (몸통 하단 → Low)
            drawLine(
                color = color,
                start = Offset(centerX, bodyBottom),
                end = Offset(centerX, priceToY(candle.low)),
                strokeWidth = 1.5f
            )
            // 몸통 — 도지처럼 open≈close여도 최소 1px 높이 보장
            drawRect(
                color = color,
                topLeft = Offset(centerX - halfBodyWidth, bodyTop),
                size = Size(halfBodyWidth * 2f, (bodyBottom - bodyTop).coerceAtLeast(1f))
            )
        }

        // 거래량 바
        if (config.showVolume) {
            val volumeAreaTop = chartHeight
            val volumeAreaHeight = size.height * VOLUME_AREA_RATIO
            val maxVolume = candles.maxOf { it.volume }.toFloat().coerceAtLeast(1f)

            for (index in firstIdx..lastIdx) {
                val candle = candles[index]
                val centerX = offsetX + (index + 0.5f) * candleWidthPx
                val barHeight = volumeAreaHeight * (candle.volume / maxVolume)
                val halfBarWidth = candleWidthPx * config.candleWidthRatio / 2f

                drawRect(
                    color = (if (candle.isBullish) config.bullishColor else config.bearishColor)
                        .copy(alpha = 0.5f),
                    topLeft = Offset(centerX - halfBarWidth, volumeAreaTop + volumeAreaHeight - barHeight),
                    size = Size(halfBarWidth * 2f, barHeight)
                )
            }
        }
    }
}
