package com.hanto.kcandlekit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hanto.kcandlekit.compose.DrawingTool
import com.hanto.kcandlekit.core.Candle
import com.hanto.kcandlekit.core.CandleIntervalSpec
import com.hanto.kcandlekit.core.CandleRepository
import com.hanto.kcandlekit.core.CandleUnit
import com.hanto.kcandlekit.core.DrawingLine
import com.hanto.kcandlekit.core.MarketSpec
import com.hanto.kcandlekit.core.PatternDetector
import com.hanto.kcandlekit.core.PatternResult
import com.hanto.kcandlekit.core.TickerRepository
import com.hanto.kcandlekit.core.TickerUpdate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

// ── 도메인 타입 ────────────────────────────────────────────────────────────────

sealed interface ChartUiState {
    data object Loading : ChartUiState
    data class Success(
        val candles: List<Candle>,
        val patterns: List<PatternResult>,
        val isLive: Boolean = false,
    ) : ChartUiState
    data class Error(val message: String) : ChartUiState
}

/** UI 레이블 + CandleIntervalSpec 매핑. Upbit 특화 정보(label)만 여기에 존재. */
enum class Interval(val label: String, val spec: CandleIntervalSpec) {
    MINUTE_1 ("1분",  CandleIntervalSpec(CandleUnit.MINUTE, 1,  200)),
    MINUTE_5 ("5분",  CandleIntervalSpec(CandleUnit.MINUTE, 5,  200)),
    MINUTE_10("10분", CandleIntervalSpec(CandleUnit.MINUTE, 10, 200)),
    MINUTE_30("30분", CandleIntervalSpec(CandleUnit.MINUTE, 30, 200)),
    MINUTE_60("60분", CandleIntervalSpec(CandleUnit.MINUTE, 60, 200)),
    DAY      ("일봉", CandleIntervalSpec(CandleUnit.DAY,    count = 200)),
    WEEK     ("주봉", CandleIntervalSpec(CandleUnit.WEEK,   count = 100)),
    MONTH    ("월봉", CandleIntervalSpec(CandleUnit.MONTH,  count = 60)),
}

val MARKETS = listOf(
    MarketSpec("KRW-BTC",  "BTC"),
    MarketSpec("KRW-ETH",  "ETH"),
    MarketSpec("KRW-XRP",  "XRP"),
    MarketSpec("KRW-SOL",  "SOL"),
    MarketSpec("KRW-DOGE", "DOGE"),
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

class ChartViewModel(
    private val candleRepo: CandleRepository,
    private val tickerRepo: TickerRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ChartUiState>(ChartUiState.Loading)
    val uiState: StateFlow<ChartUiState> = _uiState.asStateFlow()

    private val _selectedMarket   = MutableStateFlow(MARKETS.first())
    val selectedMarket: StateFlow<MarketSpec> = _selectedMarket.asStateFlow()

    private val _selectedInterval = MutableStateFlow(Interval.DAY)
    val selectedInterval: StateFlow<Interval> = _selectedInterval.asStateFlow()

    private val _drawingLines = MutableStateFlow<List<DrawingLine>>(emptyList())
    val drawingLines: StateFlow<List<DrawingLine>> = _drawingLines.asStateFlow()

    private val _activeTool = MutableStateFlow(DrawingTool.NONE)
    val activeTool: StateFlow<DrawingTool> = _activeTool.asStateFlow()

    private var wsJob: Job? = null
    private var liveHigh: Float = 0f
    private var liveLow: Float = Float.MAX_VALUE
    private var currentPeriodStart: Long = 0L
    private var lastTickUpdateMs: Long = 0L

    init { load() }

    fun selectMarket(market: MarketSpec) {
        if (_selectedMarket.value == market) return
        _selectedMarket.value = market
        load()
    }

    fun selectInterval(interval: Interval) {
        if (_selectedInterval.value == interval) return
        _selectedInterval.value = interval
        load()
    }

    fun reload() = load()

    fun selectDrawingTool(tool: DrawingTool) {
        _activeTool.value = if (_activeTool.value == tool) DrawingTool.NONE else tool
    }

    fun addDrawingLine(line: DrawingLine) {
        _drawingLines.value = _drawingLines.value + line
        if (line is DrawingLine.Trend) _activeTool.value = DrawingTool.NONE
    }

    fun removeDrawingLine(line: DrawingLine) {
        _drawingLines.value = _drawingLines.value.filter { it.id != line.id }
    }

    fun clearAllDrawingLines() {
        _drawingLines.value = emptyList()
    }

    private fun load(seamless: Boolean = false) {
        val market   = _selectedMarket.value
        val interval = _selectedInterval.value

        wsJob?.cancel()
        wsJob = null
        // 봉 경계 갱신(seamless)이면 그리기 선 유지; 마켓/인터벌 변경 시엔 초기화
        if (!seamless) {
            _drawingLines.value = emptyList()
            _activeTool.value = DrawingTool.NONE
        }

        viewModelScope.launch {
            if (!seamless) {
                _uiState.value = ChartUiState.Loading
            } else {
                val cur = _uiState.value
                if (cur is ChartUiState.Success) _uiState.value = cur.copy(isLive = false)
            }
            runCatching {
                candleRepo.getCandles(market, interval.spec)
            }.onSuccess { candles ->
                val patterns = PatternDetector.detect(candles)
                _uiState.value = ChartUiState.Success(candles, patterns)

                // 분봉에만 WebSocket 연결 (일/주/월봉은 실시간 틱 의미 없음)
                if (interval.spec.unit == CandleUnit.MINUTE) {
                    val last = candles.lastOrNull()
                    if (last != null) {
                        liveHigh = last.high
                        liveLow  = last.low
                        currentPeriodStart = periodStartOf(last.timestamp, interval.spec)
                        startWebSocket(market, interval)
                    }
                }
            }.onFailure { e ->
                _uiState.value = ChartUiState.Error(e.message ?: "알 수 없는 오류")
            }
        }
    }

    private fun startWebSocket(market: MarketSpec, interval: Interval) {
        wsJob = viewModelScope.launch {
            var backoffMs = 1_000L
            while (isActive) {
                try {
                    var hitBoundary = false
                    tickerRepo.priceFlow(market)
                        .takeWhile { tick ->
                            val isBoundary = periodStartOf(tick.timestamp, interval.spec) > currentPeriodStart
                            if (isBoundary) hitBoundary = true
                            !isBoundary
                        }
                        .collect { tick -> processTick(tick) }
                    backoffMs = 1_000L
                    // 봉 경계 감지 시 collect 종료 후 load() 호출 — 이중 구독 방지
                    if (hitBoundary) { load(seamless = true); return@launch }
                } catch (e: CancellationException) {
                    throw e  // 구조적 동시성 유지
                } catch (e: Exception) {
                    val current = _uiState.value
                    if (current is ChartUiState.Success) {
                        _uiState.value = current.copy(isLive = false)
                    }
                    if (isActive) {
                        delay(backoffMs)
                        backoffMs = (backoffMs * 2).coerceAtMost(30_000L)
                    }
                }
            }
        }
    }

    private fun processTick(tick: TickerUpdate) {
        val newClose = tick.price.toFloat()

        // 고저는 매 틱 추적 (UI 업데이트 throttle과 무관하게 누락 없이 갱신)
        if (newClose > liveHigh) liveHigh = newClose
        if (newClose < liveLow)  liveLow  = newClose

        // UI 상태는 100ms에 1회로 제한 — 초당 수십 회 틱에 의한 리컴포지션 방지
        val now = System.currentTimeMillis()
        if (now - lastTickUpdateMs < 100L) return
        lastTickUpdateMs = now

        val current = _uiState.value as? ChartUiState.Success ?: return
        val last    = current.candles.lastOrNull() ?: return
        val updatedLast = last.copy(
            close = newClose,
            high  = liveHigh,
            low   = liveLow,
        )
        val updatedCandles = current.candles.toMutableList()
            .also { it[it.lastIndex] = updatedLast }
        _uiState.value = current.copy(candles = updatedCandles, isLive = true)
    }

    // tradeTimestamp가 속한 봉 기간의 시작 시각(Unix ms) 반환
    private fun periodStartOf(timestamp: Long, spec: CandleIntervalSpec): Long {
        if (spec.unit != CandleUnit.MINUTE) return Long.MIN_VALUE
        val periodMs = spec.minuteValue * 60 * 1_000L
        return (timestamp / periodMs) * periodMs
    }

    override fun onCleared() {
        super.onCleared()
        wsJob?.cancel()
    }
}
