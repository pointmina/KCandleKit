package com.hanto.kcandlekit.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hanto.kcandlekit.core.Candle
import com.hanto.kcandlekit.core.CandlePattern
import com.hanto.kcandlekit.core.PatternResult
import com.hanto.kcandlekit.core.Signal
import com.hanto.kcandlekit.core.isBullish
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private const val MIN_CANDLE_WIDTH = 6f
private const val MAX_CANDLE_WIDTH = 120f
private const val DEFAULT_CANDLE_WIDTH = 20f
private const val PRICE_PADDING_RATIO = 0.05f
private const val VOLUME_AREA_RATIO = 0.2f
private val PRICE_LABEL_WIDTH = 60.dp

private fun upTriangle(cx: Float, tipY: Float, size: Float) = Path().apply {
    moveTo(cx, tipY)
    lineTo(cx - size, tipY + size * 1.2f)
    lineTo(cx + size, tipY + size * 1.2f)
    close()
}

private fun downTriangle(cx: Float, tipY: Float, size: Float) = Path().apply {
    moveTo(cx, tipY)
    lineTo(cx - size, tipY - size * 1.2f)
    lineTo(cx + size, tipY - size * 1.2f)
    close()
}

private fun Float.toLabel(): String = when {
    this >= 10_000f -> "%.0f".format(this)
    this >= 100f -> "%.1f".format(this)
    else -> "%.2f".format(this)
}

// 마커 위 약어 (평상시)
private fun CandlePattern.shortLabel() = when (this) {
    CandlePattern.HAMMER -> "HAM"
    CandlePattern.INVERTED_HAMMER -> "INV"
    CandlePattern.SHOOTING_STAR -> "STAR"
    CandlePattern.HANGING_MAN -> "HANG"
    CandlePattern.DOJI -> "DOJI"
    CandlePattern.BULLISH_ENGULFING -> "B.ENG"
    CandlePattern.BEARISH_ENGULFING -> "S.ENG"
    CandlePattern.MORNING_STAR -> "M.STAR"
    CandlePattern.EVENING_STAR -> "E.STAR"
    CandlePattern.THREE_WHITE_SOLDIERS -> "3WS"
}

// 탭했을 때 뱃지에 표시할 전체 이름
private fun CandlePattern.fullLabel() = when (this) {
    CandlePattern.HAMMER -> "Hammer"
    CandlePattern.INVERTED_HAMMER -> "Inverted Hammer"
    CandlePattern.SHOOTING_STAR -> "Shooting Star"
    CandlePattern.HANGING_MAN -> "Hanging Man"
    CandlePattern.DOJI -> "Doji"
    CandlePattern.BULLISH_ENGULFING -> "Bullish Engulfing"
    CandlePattern.BEARISH_ENGULFING -> "Bearish Engulfing"
    CandlePattern.MORNING_STAR -> "Morning Star"
    CandlePattern.EVENING_STAR -> "Evening Star"
    CandlePattern.THREE_WHITE_SOLDIERS -> "Three White Soldiers"
}

@Composable
fun CandleChart(
    candles: List<Candle>,
    patterns: List<PatternResult> = emptyList(),
    config: CandleChartConfig = CandleChartConfig(),
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val priceLabelWidthPx = with(density) { PRICE_LABEL_WIDTH.toPx() }

    val priceStyle = remember(config.priceTextColor) {
        TextStyle(fontSize = 10.sp, color = config.priceTextColor)
    }
    val markerLabelStyle = remember {
        TextStyle(fontSize = 8.sp, color = Color.White, fontWeight = FontWeight.Bold)
    }
    val tapBadgeStyle = remember {
        TextStyle(fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
    }

    var candleWidthPx by remember { mutableFloatStateOf(DEFAULT_CANDLE_WIDTH) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var screenWidth by remember { mutableFloatStateOf(0f) }
    var scrollInitialized by remember(candles) { mutableStateOf(false) }

    // 탭된 패턴 인덱스 — null이면 아무것도 선택 안 됨
    var tappedPatternIndex by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(screenWidth, candles.size) {
        if (screenWidth > 0f && !scrollInitialized) {
            scrollInitialized = true
            val totalWidth = candles.size * DEFAULT_CANDLE_WIDTH
            offsetX = if (totalWidth > screenWidth) screenWidth - totalWidth else 0f
        }
    }

    Canvas(
        modifier = modifier
            .onSizeChanged { screenWidth = it.width.toFloat() - priceLabelWidthPx }
            .pointerInput(candles.size) {
                // 스크롤/줌과 탭을 하나의 pointerInput 안에서 병행
                // coroutineScope 안에서 launch로 각각 독립 코루틴으로 실행
                coroutineScope {
                    launch {
                        detectTransformGestures { _, pan, zoom, _ ->
                            candleWidthPx = (candleWidthPx * zoom).coerceIn(MIN_CANDLE_WIDTH, MAX_CANDLE_WIDTH)
                            val totalWidth = candles.size * candleWidthPx
                            val minOffset = minOf(0f, screenWidth - totalWidth)
                            offsetX = (offsetX + pan.x).coerceIn(minOffset, 0f)
                        }
                    }
                    launch {
                        detectTapGestures(onTap = { tapOffset ->
                            val tappedIdx = ((tapOffset.x - offsetX) / candleWidthPx)
                                .toInt().coerceIn(0, candles.lastIndex)
                            val hasPattern = patterns.any { it.index == tappedIdx }
                            // 같은 캔들 재탭 → 닫기 / 패턴 없는 곳 탭 → 닫기 / 새 패턴 탭 → 열기
                            tappedPatternIndex = if (hasPattern && tappedPatternIndex != tappedIdx) tappedIdx else null
                        })
                    }
                }
            }
    ) {
        val chartWidth = size.width - priceLabelWidthPx

        // 1. 배경
        drawRect(config.backgroundColor)
        if (candles.isEmpty()) return@Canvas

        val chartHeight = if (config.showVolume) size.height * (1f - VOLUME_AREA_RATIO) else size.height

        // 2. 보이는 캔들 범위
        val firstIdx = (-offsetX / candleWidthPx).toInt().coerceIn(0, candles.lastIndex)
        val lastIdx = (firstIdx + (chartWidth / candleWidthPx).toInt() + 2)
            .coerceIn(firstIdx, candles.lastIndex)
        val visibleCandles = candles.subList(firstIdx, lastIdx + 1)

        // 3. 가격 스케일
        val rawMin = visibleCandles.minOf { it.low }
        val rawMax = visibleCandles.maxOf { it.high }
        val priceRange = if (rawMax == rawMin) 1f else rawMax - rawMin
        val padding = priceRange * PRICE_PADDING_RATIO
        val minPrice = rawMin - padding
        val scaledRange = (rawMax + padding) - minPrice

        fun priceToY(price: Float) = chartHeight * (1f - (price - minPrice) / scaledRange)

        // 4. 그리드 + 가격 레이블
        if (config.showGrid) {
            val step = scaledRange / (config.gridLineCount + 1)
            for (i in 1..config.gridLineCount) {
                val price = minPrice + step * i
                val y = priceToY(price)
                drawLine(color = config.gridColor, start = Offset(0f, y), end = Offset(chartWidth, y), strokeWidth = 0.5f)
                val label = price.toLabel()
                val measured = textMeasurer.measure(label, priceStyle)
                drawText(textMeasurer, label, Offset(chartWidth + 4f, y - measured.size.height / 2f), priceStyle)
            }
        }

        // 5. 우측 경계선
        drawLine(color = config.gridColor, start = Offset(chartWidth, 0f), end = Offset(chartWidth, size.height), strokeWidth = 0.5f)

        clipRect(0f, 0f, chartWidth, size.height) {

            // 6. 패턴 구간 배경 (캔들보다 먼저)
            // span을 활용해 복합 패턴(엔걸핑=2, 스타/쓰리솔져스=3)은 전체 캔들 범위를 강조
            if (config.showPatternMarkers) {
                for (result in patterns) {
                    val idx = result.index
                    if (idx !in firstIdx..lastIdx) continue
                    val startIdx = (idx - result.span + 1).coerceAtLeast(0)
                    val spanCandles = candles.subList(startIdx, idx + 1)
                    val zoneHigh = spanCandles.maxOf { it.high }
                    val zoneLow  = spanCandles.minOf { it.low }
                    val zoneLeft = offsetX + startIdx * candleWidthPx
                    val zoneRight = offsetX + (idx + 1) * candleWidthPx
                    val zoneColor = when (result.signal) {
                        Signal.BULLISH -> config.bullishColor
                        Signal.BEARISH -> config.bearishColor
                        Signal.NEUTRAL -> Color.Gray
                    }
                    // 탭된 캔들은 배경을 더 진하게 — 선택 상태 표시
                    val alpha = if (tappedPatternIndex == idx) 0.30f else 0.15f
                    drawRect(
                        color = zoneColor.copy(alpha = alpha),
                        topLeft = Offset(zoneLeft, priceToY(zoneHigh)),
                        size = Size(zoneRight - zoneLeft, priceToY(zoneLow) - priceToY(zoneHigh))
                    )
                }
            }

            // 7. 캔들 렌더링
            for (index in firstIdx..lastIdx) {
                val candle = candles[index]
                val centerX = offsetX + (index + 0.5f) * candleWidthPx
                val color = if (candle.isBullish) config.bullishColor else config.bearishColor
                val halfBodyWidth = candleWidthPx * config.candleWidthRatio / 2f
                val openY = priceToY(candle.open)
                val closeY = priceToY(candle.close)
                val bodyTop = minOf(openY, closeY)
                val bodyBottom = maxOf(openY, closeY)
                drawLine(color = color, start = Offset(centerX, priceToY(candle.high)), end = Offset(centerX, bodyTop), strokeWidth = 1.5f)
                drawLine(color = color, start = Offset(centerX, bodyBottom), end = Offset(centerX, priceToY(candle.low)), strokeWidth = 1.5f)
                drawRect(color = color, topLeft = Offset(centerX - halfBodyWidth, bodyTop), size = Size(halfBodyWidth * 2f, (bodyBottom - bodyTop).coerceAtLeast(1f)))
            }

            // 8. 패턴 마커 (▲ ▼ ●) + 약어 레이블
            if (config.showPatternMarkers && patterns.isNotEmpty()) {
                val markerSize = (candleWidthPx * 0.45f).coerceIn(7f, 18f)
                val markerGap = markerSize + 4f

                for (result in patterns) {
                    val idx = result.index
                    if (idx < firstIdx || idx > lastIdx) continue
                    val candle = candles[idx]
                    val centerX = offsetX + (idx + 0.5f) * candleWidthPx
                    val shortLabel = when (result.signal) {
                        Signal.BULLISH -> "↑ ${result.pattern.shortLabel()}"
                        Signal.BEARISH -> "↓ ${result.pattern.shortLabel()}"
                        Signal.NEUTRAL -> "— ${result.pattern.shortLabel()}"
                    }
                    val measured = textMeasurer.measure(shortLabel, markerLabelStyle)

                    when (result.signal) {
                        Signal.BULLISH -> {
                            val tipY = priceToY(candle.low) + markerGap
                            val path = upTriangle(centerX, tipY, markerSize)
                            drawPath(path, color = config.bullishColor)
                            drawPath(path, color = Color.White.copy(alpha = 0.7f), style = Stroke(width = 1.2f))
                            if (config.showPatternLabels) {
                                val tx = centerX - measured.size.width / 2f
                                val ty = tipY + markerSize * 1.2f + 3f
                                drawLabelBadge(tx, ty, measured.size.width.toFloat(), measured.size.height.toFloat(), config.bullishColor)
                                drawText(textMeasurer, shortLabel, Offset(tx, ty), markerLabelStyle)
                            }
                        }
                        Signal.BEARISH -> {
                            val tipY = priceToY(candle.high) - markerGap
                            val path = downTriangle(centerX, tipY, markerSize)
                            drawPath(path, color = config.bearishColor)
                            drawPath(path, color = Color.White.copy(alpha = 0.7f), style = Stroke(width = 1.2f))
                            if (config.showPatternLabels) {
                                val tx = centerX - measured.size.width / 2f
                                val ty = tipY - markerSize * 1.2f - measured.size.height - 3f
                                drawLabelBadge(tx, ty, measured.size.width.toFloat(), measured.size.height.toFloat(), config.bearishColor)
                                drawText(textMeasurer, shortLabel, Offset(tx, ty), markerLabelStyle)
                            }
                        }
                        Signal.NEUTRAL -> {
                            val cy = priceToY(candle.low) + markerGap + markerSize * 0.5f
                            drawCircle(color = Color(0xFF9E9E9E), radius = markerSize * 0.6f, center = Offset(centerX, cy))
                            drawCircle(color = Color.White.copy(alpha = 0.6f), radius = markerSize * 0.6f, center = Offset(centerX, cy), style = Stroke(width = 1.2f))
                            if (config.showPatternLabels) {
                                val tx = centerX - measured.size.width / 2f
                                val ty = cy + markerSize * 0.6f + 3f
                                drawLabelBadge(tx, ty, measured.size.width.toFloat(), measured.size.height.toFloat(), Color(0xFF616161))
                                drawText(textMeasurer, shortLabel, Offset(tx, ty), markerLabelStyle)
                            }
                        }
                    }
                }

                // 9. 탭 뱃지 — 탭된 패턴 캔들에만 전체 이름 표시
                tappedPatternIndex?.let { idx ->
                    if (idx !in firstIdx..lastIdx) return@let
                    val result = patterns.find { it.index == idx } ?: return@let
                    val candle = candles[idx]
                    val centerX = offsetX + (idx + 0.5f) * candleWidthPx
                    val markerSize2 = (candleWidthPx * 0.45f).coerceIn(7f, 18f)
                    val markerGap2 = markerSize2 + 4f

                    val signalText = when (result.signal) {
                        Signal.BULLISH -> "↑ Bullish"
                        Signal.BEARISH -> "↓ Bearish"
                        Signal.NEUTRAL -> "— Neutral"
                    }
                    val line1 = result.pattern.fullLabel()
                    val measured1 = textMeasurer.measure(line1, tapBadgeStyle)
                    val measured2 = textMeasurer.measure(signalText, tapBadgeStyle)
                    val badgeW = maxOf(measured1.size.width, measured2.size.width).toFloat()
                    val badgeH = (measured1.size.height + measured2.size.height + 6).toFloat()
                    val badgeColor = when (result.signal) {
                        Signal.BULLISH -> config.bullishColor
                        Signal.BEARISH -> config.bearishColor
                        Signal.NEUTRAL -> Color(0xFF616161)
                    }

                    // 위치: BULLISH는 마커 아래, BEARISH는 마커 위
                    val badgeY = when (result.signal) {
                        Signal.BEARISH -> (priceToY(candle.high) - markerGap2 - markerSize2 * 1.2f - badgeH - 6f).coerceAtLeast(4f)
                        else -> (priceToY(candle.low) + markerGap2 + markerSize2 * 1.2f + 6f).coerceAtMost(chartHeight - badgeH - 4f)
                    }
                    val badgeX = (centerX - badgeW / 2f - 6f).coerceIn(4f, chartWidth - badgeW - 16f)

                    // 뱃지 배경
                    drawRect(color = badgeColor, topLeft = Offset(badgeX, badgeY), size = Size(badgeW + 12f, badgeH))
                    // 텍스트
                    drawText(textMeasurer, line1, Offset(badgeX + 6f, badgeY + 3f), tapBadgeStyle)
                    drawText(textMeasurer,
                        signalText, Offset(badgeX + 6f, badgeY + 3f + measured1.size.height), tapBadgeStyle)
                }
            }

            // 10. 거래량 구분선 + 바
            if (config.showVolume) {
                drawLine(color = config.gridColor, start = Offset(0f, chartHeight), end = Offset(chartWidth, chartHeight), strokeWidth = 1f)
                val volumeAreaHeight = size.height * VOLUME_AREA_RATIO
                val maxVolume = candles.maxOf { it.volume }.toFloat().coerceAtLeast(1f)
                for (index in firstIdx..lastIdx) {
                    val candle = candles[index]
                    val centerX = offsetX + (index + 0.5f) * candleWidthPx
                    val barHeight = volumeAreaHeight * (candle.volume / maxVolume)
                    val halfBarWidth = candleWidthPx * config.candleWidthRatio / 2f
                    drawRect(
                        color = (if (candle.isBullish) config.bullishColor else config.bearishColor).copy(alpha = 0.5f),
                        topLeft = Offset(centerX - halfBarWidth, chartHeight + volumeAreaHeight - barHeight),
                        size = Size(halfBarWidth * 2f, barHeight)
                    )
                }
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLabelBadge(
    x: Float, y: Float, textWidth: Float, textHeight: Float, color: Color
) {
    drawRect(
        color = color.copy(alpha = 0.85f),
        topLeft = Offset(x - 3f, y - 1f),
        size = Size(textWidth + 6f, textHeight + 2f)
    )
}
