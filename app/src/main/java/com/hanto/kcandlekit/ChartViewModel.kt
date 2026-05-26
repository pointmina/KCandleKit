package com.hanto.kcandlekit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hanto.kcandlekit.core.Candle
import com.hanto.kcandlekit.core.PatternDetector
import com.hanto.kcandlekit.core.PatternResult
import com.hanto.kcandlekit.data.UpbitApi
import com.hanto.kcandlekit.data.toCandle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ── 도메인 타입 ────────────────────────────────────────────────────────────────

sealed interface ChartUiState {
    data object Loading : ChartUiState
    data class Success(
        val candles: List<Candle>,
        val patterns: List<PatternResult>,
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
        viewModelScope.launch {
            _uiState.value = ChartUiState.Loading
            runCatching {
                // 분봉은 /candles/minutes/{unit}, 나머지는 /candles/{days|weeks|months}
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
            }.onFailure { e ->
                _uiState.value = ChartUiState.Error(e.message ?: "알 수 없는 오류")
            }
        }
    }
}
