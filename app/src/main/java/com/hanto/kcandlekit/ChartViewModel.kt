package com.hanto.kcandlekit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hanto.kcandlekit.core.Candle
import com.hanto.kcandlekit.core.PatternDetector
import com.hanto.kcandlekit.core.PatternResult
import com.hanto.kcandlekit.data.UpbitApi
import com.hanto.kcandlekit.data.UpbitTickerMessage
import com.hanto.kcandlekit.data.toCandle
import com.hanto.kcandlekit.data.upbitTickerFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

/**
 * @param minuteUnit null이면 일/주/월봉, 숫자면 분봉 단위 (5, 10, 30, 60)
 */
enum class Interval(val label: String, val path: String, val minuteUnit: Int?, val count: Int) {
    MINUTE_5 ("5분",  "minutes",  5,  200),
    MINUTE_10("10분", "minutes", 10,  200),
    MINUTE_30("30분", "minutes", 30,  200),
    MINUTE_60("60분", "minutes", 60,  200),
    DAY      ("일봉", "days",    null, 200),
    WEEK     ("주봉", "weeks",   null, 100),
    MONTH    ("월봉", "months",  null,  60),
}

data class Market(val code: String, val label: String)

val MARKETS = listOf(
    Market("KRW-BTC",  "BTC"),
    Market("KRW-ETH",  "ETH"),
    Market("KRW-XRP",  "XRP"),
    Market("KRW-SOL",  "SOL"),
    Market("KRW-DOGE", "DOGE"),
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

class ChartViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<ChartUiState>(ChartUiState.Loading)
    val uiState: StateFlow<ChartUiState> = _uiState.asStateFlow()

    private val _selectedMarket   = MutableStateFlow(MARKETS.first())
    val selectedMarket: StateFlow<Market> = _selectedMarket.asStateFlow()

    private val _selectedInterval = MutableStateFlow(Interval.DAY)
    val selectedInterval: StateFlow<Interval> = _selectedInterval.asStateFlow()

    private var wsJob: Job? = null
    private var liveHigh: Float = 0f
    private var liveLow: Float = Float.MAX_VALUE
    private var currentPeriodStart: Long = 0L

    init { load() }

    fun selectMarket(market: Market) {
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

    private fun load() {
        val market   = _selectedMarket.value
        val interval = _selectedInterval.value

        wsJob?.cancel()
        wsJob = null

        viewModelScope.launch {
            _uiState.value = ChartUiState.Loading
            runCatching {
                if (interval.minuteUnit != null) {
                    UpbitApi.service.getMinuteCandles(
                        unit   = interval.minuteUnit,
                        market = market.code,
                        count  = interval.count,
                    )
                } else {
                    UpbitApi.service.getCandles(
                        interval = interval.path,
                        market   = market.code,
                        count    = interval.count,
                    )
                }
            }.onSuccess { response ->
                // 업비트는 최신→과거 순 반환 — 차트는 과거→최신 순 필요
                val candles  = response.reversed().map { it.toCandle() }
                val patterns = PatternDetector.detect(candles)
                _uiState.value = ChartUiState.Success(candles, patterns)

                // 분봉에만 WebSocket 연결 (일/주/월봉은 실시간 틱 의미 없음)
                if (interval.minuteUnit != null) {
                    val last = candles.lastOrNull()
                    if (last != null) {
                        liveHigh = last.high
                        liveLow  = last.low
                        currentPeriodStart = periodStartOf(last.timestamp, interval)
                        startWebSocket(market, interval)
                    }
                }
            }.onFailure { e ->
                _uiState.value = ChartUiState.Error(e.message ?: "알 수 없는 오류")
            }
        }
    }

    private fun startWebSocket(market: Market, interval: Interval) {
        wsJob = viewModelScope.launch {
            var backoffMs = 1_000L
            while (isActive) {
                try {
                    upbitTickerFlow(UpbitApi.client, market.code)
                        .collect { processTick(it, interval) }
                    backoffMs = 1_000L  // 정상 종료 시 백오프 초기화
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

    private fun processTick(tick: UpbitTickerMessage, interval: Interval) {
        // 봉 경계를 넘으면 REST 재조회로 깔끔한 데이터 확보
        if (periodStartOf(tick.tradeTimestamp, interval) > currentPeriodStart) {
            viewModelScope.launch(Dispatchers.Main.immediate) { load() }
            return
        }

        val current = _uiState.value as? ChartUiState.Success ?: return
        val last    = current.candles.lastOrNull() ?: return
        val newClose = tick.tradePrice.toFloat()

        // 현재 봉의 고저를 틱 스트림에서 직접 추적
        if (newClose > liveHigh) liveHigh = newClose
        if (newClose < liveLow)  liveLow  = newClose

        val updatedLast = last.copy(
            close = newClose,
            high  = maxOf(last.high, liveHigh),
            low   = minOf(last.low,  liveLow),
        )
        val updatedCandles = current.candles.toMutableList()
            .also { it[it.lastIndex] = updatedLast }

        // 패턴은 봉이 확정될 때만 재계산 (REST 재조회 시) — 매 틱마다 실행 안 함
        _uiState.value = current.copy(candles = updatedCandles, isLive = true)
    }

    // tradeTimestamp가 속한 봉 기간의 시작 시각(Unix ms) 반환
    private fun periodStartOf(tradeTimestamp: Long, interval: Interval): Long {
        val periodMs = (interval.minuteUnit ?: return Long.MIN_VALUE) * 60 * 1_000L
        return (tradeTimestamp / periodMs) * periodMs
    }

    override fun onCleared() {
        super.onCleared()
        wsJob?.cancel()
    }
}
