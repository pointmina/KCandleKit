package com.hanto.kcandlekit.compose

import android.annotation.SuppressLint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
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
import com.hanto.kcandlekit.core.Indicators
import com.hanto.kcandlekit.core.PatternResult
import com.hanto.kcandlekit.core.Signal
import com.hanto.kcandlekit.core.isBullish
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ── 레이아웃 상수 ──────────────────────────────────────────────────────────────

private const val MIN_CANDLE_WIDTH     = 6f
private const val MAX_CANDLE_WIDTH     = 120f
private const val DEFAULT_CANDLE_WIDTH = 20f
private const val PRICE_PADDING_RATIO  = 0.05f
private const val VOLUME_AREA_RATIO    = 0.2f
private val PRICE_LABEL_WIDTH = 60.dp
private val TIME_AXIS_HEIGHT  = 20.dp

// ── 날짜 포맷터 (UI 스레드 단일 사용 — 스레드 안전 불요) ──────────────────────────
private val DATE_FMT       = SimpleDateFormat("yyyy.MM.dd",       Locale.getDefault())
private val DATETIME_FMT   = SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault())
private val SHORT_DATE_FMT = SimpleDateFormat("MM/dd",             Locale.getDefault())
private val TIME_FMT       = SimpleDateFormat("HH:mm",             Locale.getDefault())

// ── 삼각형 마커 경로 ──────────────────────────────────────────────────────────

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

// ── 레이블 포맷터 ─────────────────────────────────────────────────────────────

private fun Float.toLabel(): String = when {
    this >= 1_000_000_000f -> "${"%.2f".format(this / 1_000_000_000f)}B"
    this >= 1_000_000f     -> "${"%.2f".format(this / 1_000_000f)}M"
    this >= 1_000f         -> "${"%.1f".format(this / 1_000f)}K"
    this >= 100f           -> "%.1f".format(this)
    else                   -> "%.2f".format(this)
}

private fun Long.toVolumeLabel(): String = when {
    this >= 1_000_000L -> "${"%.2f".format(this / 1_000_000.0)}M"
    this >= 1_000L     -> "${"%.1f".format(this / 1_000.0)}K"
    else               -> this.toString()
}

private fun Long.toDateLabel()      = DATE_FMT.format(Date(this))
private fun Long.toDateTimeLabel()  = DATETIME_FMT.format(Date(this))
private fun Long.toShortDateLabel() = SHORT_DATE_FMT.format(Date(this))
private fun Long.toTimeLabel()      = TIME_FMT.format(Date(this))

// ── 패턴 레이블 ───────────────────────────────────────────────────────────────

private fun CandlePattern.shortLabel() = when (this) {
    CandlePattern.HAMMER               -> "HAM"
    CandlePattern.INVERTED_HAMMER      -> "INV"
    CandlePattern.SHOOTING_STAR        -> "STAR"
    CandlePattern.HANGING_MAN          -> "HANG"
    CandlePattern.DOJI                 -> "DOJI"
    CandlePattern.BULLISH_ENGULFING    -> "B.ENG"
    CandlePattern.BEARISH_ENGULFING    -> "S.ENG"
    CandlePattern.MORNING_STAR         -> "M.STAR"
    CandlePattern.EVENING_STAR         -> "E.STAR"
    CandlePattern.THREE_WHITE_SOLDIERS -> "3WS"
}

private fun CandlePattern.fullLabel() = when (this) {
    CandlePattern.HAMMER               -> "Hammer"
    CandlePattern.INVERTED_HAMMER      -> "Inverted Hammer"
    CandlePattern.SHOOTING_STAR        -> "Shooting Star"
    CandlePattern.HANGING_MAN          -> "Hanging Man"
    CandlePattern.DOJI                 -> "Doji"
    CandlePattern.BULLISH_ENGULFING    -> "Bullish Engulfing"
    CandlePattern.BEARISH_ENGULFING    -> "Bearish Engulfing"
    CandlePattern.MORNING_STAR         -> "Morning Star"
    CandlePattern.EVENING_STAR         -> "Evening Star"
    CandlePattern.THREE_WHITE_SOLDIERS -> "Three White Soldiers"
}

// ── DrawScope 유틸 ────────────────────────────────────────────────────────────

private fun DrawScope.drawLabelBadge(
    x: Float, y: Float, textWidth: Float, textHeight: Float, color: Color,
) = drawRect(
    color = color.copy(alpha = 0.85f),
    topLeft = Offset(x - 3f, y - 1f),
    size = Size(textWidth + 6f, textHeight + 2f),
)

// ── CandleChart ───────────────────────────────────────────────────────────────

@Composable
fun CandleChart(
    candles: List<Candle>,
    patterns: List<PatternResult> = emptyList(),
    config: CandleChartConfig = CandleChartConfig(),
    modifier: Modifier = Modifier,
) {
    val textMeasurer      = rememberTextMeasurer()
    val density           = LocalDensity.current
    val priceLabelWidthPx = with(density) { PRICE_LABEL_WIDTH.toPx() }
    val timeAxisHeightPx  = with(density) { if (config.showTimeAxis) TIME_AXIS_HEIGHT.toPx() else 0f }

    // ── TextStyle 캐시 ────────────────────────────────────────────────────────
    val priceStyle = remember(config.priceTextColor) {
        TextStyle(fontSize = 10.sp, color = config.priceTextColor)
    }
    val markerLabelStyle = remember {
        TextStyle(fontSize = 8.sp, color = Color.White, fontWeight = FontWeight.Bold)
    }
    val tapBadgeStyle = remember {
        TextStyle(fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
    }
    val timeAxisStyle = remember(config.timeAxisTextColor) {
        TextStyle(fontSize = 9.sp, color = config.timeAxisTextColor)
    }
    val crosshairInfoStyle = remember {
        // OHLCV 인포바 — TradingView 스타일 밝은 회색
        TextStyle(fontSize = 10.sp, color = Color(0xFFD1D4DC))
    }
    val crosshairPriceStyle = remember {
        // 크로스헤어 가격 배지 — 어두운 배경 위 짙은 텍스트
        TextStyle(fontSize = 10.sp, color = Color(0xFF131722), fontWeight = FontWeight.Bold)
    }

    // ── 상태 ─────────────────────────────────────────────────────────────────
    var candleWidthPx      by remember { mutableFloatStateOf(DEFAULT_CANDLE_WIDTH) }
    var offsetX            by remember { mutableFloatStateOf(0f) }
    var screenWidth        by remember { mutableFloatStateOf(0f) }
    var scrollInitialized  by remember(candles) { mutableStateOf(false) }
    var tappedPatternIndex by remember { mutableStateOf<Int?>(null) }
    // 크로스헤어가 가리키는 캔들 인덱스 — null이면 비활성
    var crosshairIndex     by remember { mutableStateOf<Int?>(null) }

    // ── MA 값 사전 계산 (candles가 바뀔 때만 재계산) ──────────────────────────
    val maValues = remember(candles, config.movingAverages) {
        config.movingAverages.map { Indicators.movingAverage(candles, it.period) }
    }

    // ── 최신 캔들이 우측에 오도록 초기 스크롤 ─────────────────────────────────
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
                coroutineScope {
                    // ① 스크롤 / 핀치줌
                    launch {
                        detectTransformGestures { _, pan, zoom, _ ->
                            candleWidthPx = (candleWidthPx * zoom).coerceIn(MIN_CANDLE_WIDTH, MAX_CANDLE_WIDTH)
                            val totalWidth = candles.size * candleWidthPx
                            val minOffset  = minOf(0f, screenWidth - totalWidth)
                            offsetX = (offsetX + pan.x).coerceIn(minOffset, 0f)
                        }
                    }
                    // ② 탭 — 패턴 뱃지 토글 / 크로스헤어 닫기
                    launch {
                        detectTapGestures(onTap = { tapOffset ->
                            if (crosshairIndex != null) {
                                crosshairIndex = null
                                return@detectTapGestures
                            }
                            val idx = ((tapOffset.x - offsetX) / candleWidthPx)
                                .toInt().coerceIn(0, candles.lastIndex)
                            val hasPattern = patterns.any { it.index == idx }
                            tappedPatternIndex =
                                if (hasPattern && tappedPatternIndex != idx) idx else null
                        })
                    }
                    // ③ 크로스헤어 — 길게 누른 채 드래그
                    launch {
                        if (!config.showCrosshair) return@launch
                        detectDragGesturesAfterLongPress(
                            onDragStart = { offset ->
                                tappedPatternIndex = null
                                crosshairIndex = ((offset.x - offsetX) / candleWidthPx)
                                    .toInt().coerceIn(0, candles.lastIndex)
                            },
                            onDrag = { change, _ ->
                                crosshairIndex = ((change.position.x - offsetX) / candleWidthPx)
                                    .toInt().coerceIn(0, candles.lastIndex)
                            },
                            onDragEnd    = { crosshairIndex = null },
                            onDragCancel = { crosshairIndex = null },
                        )
                    }
                }
            },
    ) {
        val chartWidth = size.width - priceLabelWidthPx

        // ── ① 배경 ──────────────────────────────────────────────────────────
        drawRect(config.backgroundColor)
        if (candles.isEmpty()) return@Canvas

        // 레이아웃 영역 분할
        //  size.height
        //  ├── chartAreaHeight (= 캔들 + 거래량)
        //  │     ├── chartHeight (캔들 영역)
        //  │     └── volumeAreaHeight (거래량, showVolume=true 시)
        //  └── timeAxisHeightPx (시간 축, showTimeAxis=true 시)
        val chartAreaHeight  = size.height - timeAxisHeightPx
        val chartHeight      = if (config.showVolume) chartAreaHeight * (1f - VOLUME_AREA_RATIO)
                               else chartAreaHeight

        // ── ② 보이는 캔들 인덱스 범위 ────────────────────────────────────────
        val firstIdx       = (-offsetX / candleWidthPx).toInt().coerceIn(0, candles.lastIndex)
        val lastIdx        = (firstIdx + (chartWidth / candleWidthPx).toInt() + 2)
            .coerceIn(firstIdx, candles.lastIndex)
        val visibleCandles = candles.subList(firstIdx, lastIdx + 1)

        // 캔들 간격으로 분봉/일봉 자동 판별 — 시간축 포맷 & OHLCV 날짜 포맷 결정
        val avgCandleMs = if (lastIdx > firstIdx)
            (candles[lastIdx].timestamp - candles[firstIdx].timestamp) / (lastIdx - firstIdx).toLong()
        else Long.MAX_VALUE
        val isIntraday = avgCandleMs < 24 * 60 * 60 * 1_000L  // 1일 미만 간격 = 분봉/시간봉

        // ── ③ 가격 스케일 ────────────────────────────────────────────────────
        val rawMin      = visibleCandles.minOf { it.low }
        val rawMax      = visibleCandles.maxOf { it.high }
        val priceRange  = if (rawMax == rawMin) 1f else rawMax - rawMin
        val padding     = priceRange * PRICE_PADDING_RATIO
        val minPrice    = rawMin - padding
        val scaledRange = (rawMax + padding) - minPrice

        fun priceToY(price: Float) = chartHeight * (1f - (price - minPrice) / scaledRange)

        // ── ④ 그리드 + 우측 가격 레이블 ──────────────────────────────────────
        if (config.showGrid) {
            val step = scaledRange / (config.gridLineCount + 1)
            for (i in 1..config.gridLineCount) {
                val price = minPrice + step * i
                val y     = priceToY(price)
                drawLine(
                    color = config.gridColor,
                    start = Offset(0f, y), end = Offset(chartWidth, y),
                    strokeWidth = 0.5f,
                )
                val label    = price.toLabel()
                val measured = textMeasurer.measure(label, priceStyle)
                drawText(textMeasurer, label, Offset(chartWidth + 4f, y - measured.size.height / 2f), priceStyle)
            }
        }

        // ── ⑤ 우측 경계선 ────────────────────────────────────────────────────
        drawLine(
            color = config.gridColor,
            start = Offset(chartWidth, 0f), end = Offset(chartWidth, size.height),
            strokeWidth = 0.5f,
        )

        // ── clipRect: 캔들 영역 — 가격 레이블 영역으로 넘치지 않도록 ──────────
        clipRect(0f, 0f, chartWidth, size.height) {

            // ── ⑥ 패턴 구간 배경 (span 기반) ─────────────────────────────────
            if (config.showPatternMarkers) {
                for (result in patterns) {
                    val idx = result.index
                    if (idx < firstIdx || idx > lastIdx) continue
                    val startIdx    = (idx - result.span + 1).coerceAtLeast(0)
                    val spanCandles = candles.subList(startIdx, idx + 1)
                    val zoneHigh    = spanCandles.maxOf { it.high }
                    val zoneLow     = spanCandles.minOf { it.low }
                    val zoneLeft    = offsetX + startIdx * candleWidthPx
                    val zoneRight   = offsetX + (idx + 1) * candleWidthPx
                    val zoneColor   = when (result.signal) {
                        Signal.BULLISH -> config.bullishColor
                        Signal.BEARISH -> config.bearishColor
                        Signal.NEUTRAL -> Color.Gray
                    }
                    val alpha = if (tappedPatternIndex == idx) 0.30f else 0.15f
                    drawRect(
                        color = zoneColor.copy(alpha = alpha),
                        topLeft = Offset(zoneLeft, priceToY(zoneHigh)),
                        size = Size(zoneRight - zoneLeft, priceToY(zoneLow) - priceToY(zoneHigh)),
                    )
                }
            }

            // ── ⑦ 캔들 렌더링 ────────────────────────────────────────────────
            for (index in firstIdx..lastIdx) {
                val candle        = candles[index]
                val centerX       = offsetX + (index + 0.5f) * candleWidthPx
                val color         = if (candle.isBullish) config.bullishColor else config.bearishColor
                val halfBodyWidth = candleWidthPx * config.candleWidthRatio / 2f
                val openY         = priceToY(candle.open)
                val closeY        = priceToY(candle.close)
                val bodyTop       = minOf(openY, closeY)
                val bodyBottom    = maxOf(openY, closeY)
                drawLine(color = color, start = Offset(centerX, priceToY(candle.high)), end = Offset(centerX, bodyTop), strokeWidth = 1.5f)
                drawLine(color = color, start = Offset(centerX, bodyBottom), end = Offset(centerX, priceToY(candle.low)), strokeWidth = 1.5f)
                drawRect(color = color, topLeft = Offset(centerX - halfBodyWidth, bodyTop), size = Size(halfBodyWidth * 2f, (bodyBottom - bodyTop).coerceAtLeast(1f)))
            }

            // ── ⑧ 이동 평균선 ────────────────────────────────────────────────
            // firstIdx-1 까지 포함해 좌측 경계에서 선이 끊기지 않도록
            config.movingAverages.forEachIndexed { maIdx, maConfig ->
                val values    = maValues.getOrNull(maIdx) ?: return@forEachIndexed
                val path      = Path()
                var started   = false
                val startFrom = (firstIdx - 1).coerceAtLeast(0)
                for (index in startFrom..lastIdx) {
                    val ma = values.getOrNull(index) ?: run { started = false; continue }
                    val x  = offsetX + (index + 0.5f) * candleWidthPx
                    val y  = priceToY(ma)
                    if (!started) { path.moveTo(x, y); started = true } else path.lineTo(x, y)
                }
                if (started) drawPath(
                    path, color = maConfig.color,
                    style = Stroke(width = maConfig.strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }

            // ── ⑨ 패턴 마커 (▲ ▼ ●) ─────────────────────────────────────────
            if (config.showPatternMarkers && patterns.isNotEmpty()) {
                val markerSize = (candleWidthPx * 0.45f).coerceIn(7f, 18f)
                val markerGap  = markerSize + 4f

                for (result in patterns) {
                    val idx = result.index
                    if (idx < firstIdx || idx > lastIdx) continue
                    val candle  = candles[idx]
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

                // ── ⑩ 탭 뱃지 — 선택된 패턴 전체 이름 + 시그널 ───────────────
                tappedPatternIndex?.let { idx ->
                    if (idx !in firstIdx..lastIdx) return@let
                    val result  = patterns.find { it.index == idx } ?: return@let
                    val candle  = candles[idx]
                    val centerX = offsetX + (idx + 0.5f) * candleWidthPx
                    val mSize   = (candleWidthPx * 0.45f).coerceIn(7f, 18f)
                    val mGap    = mSize + 4f
                    val signalText = when (result.signal) {
                        Signal.BULLISH -> "↑ Bullish"
                        Signal.BEARISH -> "↓ Bearish"
                        Signal.NEUTRAL -> "— Neutral"
                    }
                    val m1 = textMeasurer.measure(result.pattern.fullLabel(), tapBadgeStyle)
                    val m2 = textMeasurer.measure(signalText, tapBadgeStyle)
                    val badgeW     = maxOf(m1.size.width, m2.size.width).toFloat()
                    val badgeH     = (m1.size.height + m2.size.height + 6).toFloat()
                    val badgeColor = when (result.signal) {
                        Signal.BULLISH -> config.bullishColor
                        Signal.BEARISH -> config.bearishColor
                        Signal.NEUTRAL -> Color(0xFF616161)
                    }
                    val badgeY = when (result.signal) {
                        Signal.BEARISH -> (priceToY(candle.high) - mGap - mSize * 1.2f - badgeH - 6f).coerceAtLeast(4f)
                        else           -> (priceToY(candle.low)  + mGap + mSize * 1.2f + 6f).coerceAtMost(chartHeight - badgeH - 4f)
                    }
                    val badgeX = (centerX - badgeW / 2f - 6f).coerceIn(4f, chartWidth - badgeW - 16f)
                    drawRect(color = badgeColor, topLeft = Offset(badgeX, badgeY), size = Size(badgeW + 12f, badgeH))
                    drawText(textMeasurer, result.pattern.fullLabel(), Offset(badgeX + 6f, badgeY + 3f), tapBadgeStyle)
                    drawText(textMeasurer, signalText, Offset(badgeX + 6f, badgeY + 3f + m1.size.height), tapBadgeStyle)
                }
            }

            // ── ⑪ 크로스헤어 — 수직 점선 + OHLCV 인포바 ─────────────────────
            crosshairIndex?.let { idx ->
                val candle  = candles.getOrNull(idx) ?: return@let
                val centerX = offsetX + (idx + 0.5f) * candleWidthPx
                if (centerX < 0f || centerX > chartWidth) return@let

                val dash = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))

                // 수직 점선 (캔들 + 거래량 영역 전체)
                drawLine(
                    color = config.crosshairColor.copy(alpha = 0.8f),
                    start = Offset(centerX, 0f), end = Offset(centerX, chartAreaHeight),
                    strokeWidth = 1f, pathEffect = dash,
                )

                // OHLCV 인포바 (상단 좌측 고정)
                val info = buildString {
                    // 분봉이면 날짜+시간, 일봉 이상이면 날짜만
                    append(if (isIntraday) candle.timestamp.toDateTimeLabel() else candle.timestamp.toDateLabel())
                    append("  O ${candle.open.toLabel()}")
                    append("  H ${candle.high.toLabel()}")
                    append("  L ${candle.low.toLabel()}")
                    append("  C ${candle.close.toLabel()}")
                    append("  V ${candle.volume.toVolumeLabel()}")
                }
                val infoMeasured = textMeasurer.measure(info, crosshairInfoStyle)
                val panelW = (infoMeasured.size.width.toFloat() + 12f).coerceAtMost(chartWidth - 4f)
                val panelH = infoMeasured.size.height.toFloat() + 8f
                drawRect(
                    color = Color(0xFF1E2130).copy(alpha = 0.95f),
                    topLeft = Offset(2f, 2f),
                    size = Size(panelW, panelH),
                )
                drawText(textMeasurer, info, Offset(6f, 6f), crosshairInfoStyle)
            }

            // ── ⑫ 거래량 바 ──────────────────────────────────────────────────
            if (config.showVolume) {
                drawLine(color = config.gridColor, start = Offset(0f, chartHeight), end = Offset(chartWidth, chartHeight), strokeWidth = 1f)
                val volumeAreaHeight = chartAreaHeight * VOLUME_AREA_RATIO
                val maxVolume        = candles.maxOf { it.volume }.toFloat().coerceAtLeast(1f)
                for (index in firstIdx..lastIdx) {
                    val candle       = candles[index]
                    val centerX      = offsetX + (index + 0.5f) * candleWidthPx
                    val barHeight    = volumeAreaHeight * (candle.volume / maxVolume)
                    val halfBarWidth = candleWidthPx * config.candleWidthRatio / 2f
                    drawRect(
                        color = (if (candle.isBullish) config.bullishColor else config.bearishColor).copy(alpha = 0.5f),
                        topLeft = Offset(centerX - halfBarWidth, chartHeight + volumeAreaHeight - barHeight),
                        size = Size(halfBarWidth * 2f, barHeight),
                    )
                }
            }
        }
        // ── clipRect 종료 ────────────────────────────────────────────────────

        // ── ⑬ 크로스헤어 — 수평 점선 + 우측 가격 배지 ──────────────────────
        // clipRect 바깥에서 그려 가격 레이블 영역(오른쪽 60dp)까지 선이 연장됨
        crosshairIndex?.let { idx ->
            val candle  = candles.getOrNull(idx) ?: return@let
            val centerX = offsetX + (idx + 0.5f) * candleWidthPx
            if (centerX < 0f || centerX > chartWidth) return@let

            val closeY = priceToY(candle.close)
            val dash   = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))

            // 수평 점선 (차트 전체 폭)
            drawLine(
                color = config.crosshairColor.copy(alpha = 0.8f),
                start = Offset(0f, closeY), end = Offset(size.width, closeY),
                strokeWidth = 1f, pathEffect = dash,
            )
            // 우측 가격 배지 — 현재 가격 하이라이트
            val priceLabel   = candle.close.toLabel()
            val priceMeasured = textMeasurer.measure(priceLabel, priceStyle)
            val badgeTop     = closeY - priceMeasured.size.height / 2f - 2f
            drawRect(
                color = config.crosshairColor,
                topLeft = Offset(chartWidth + 1f, badgeTop),
                size = Size(priceLabelWidthPx - 2f, priceMeasured.size.height.toFloat() + 4f),
            )
            drawText(
                textMeasurer, priceLabel,
                Offset(chartWidth + 4f, closeY - priceMeasured.size.height / 2f),
                crosshairPriceStyle,
            )
        }

        // ── ⑭ 시간 축 (X축 날짜 레이블) ─────────────────────────────────────
        if (config.showTimeAxis) {
            val axisTop = chartAreaHeight
            drawLine(
                color = config.gridColor,
                start = Offset(0f, axisTop), end = Offset(chartWidth, axisTop),
                strokeWidth = 0.5f,
            )
            val visibleCount  = lastIdx - firstIdx + 1
            val labelInterval = maxOf(1, visibleCount / 5)
            var prevLabelRight = -Float.MAX_VALUE
            for (index in firstIdx..lastIdx step labelInterval) {
                val x = offsetX + (index + 0.5f) * candleWidthPx
                if (x < 0f || x > chartWidth) continue
                // 분봉이면 HH:mm, 일봉 이상이면 MM/dd
                val label    = if (isIntraday) candles[index].timestamp.toTimeLabel()
                               else candles[index].timestamp.toShortDateLabel()
                val measured = textMeasurer.measure(label, timeAxisStyle)
                val labelX   = (x - measured.size.width / 2f)
                    .coerceIn(0f, chartWidth - measured.size.width.toFloat())
                // 겹치는 레이블 건너뜀
                if (labelX > prevLabelRight + 4f) {
                    drawText(textMeasurer, label, Offset(labelX, axisTop + 3f), timeAxisStyle)
                    prevLabelRight = labelX + measured.size.width
                }
            }
        }
    }
}
