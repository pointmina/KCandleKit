package com.hanto.kcandlekit.core

/**
 * Identifies a tradeable market/symbol.
 *
 * [id] is an opaque identifier — its format and meaning are defined entirely by whichever
 * [CandleRepository]/[TickerRepository] implementation consumes it (e.g. Upbit's `"KRW-BTC"`,
 * Binance's `"BTCUSDT"`). `core` never parses or validates it.
 */
data class MarketSpec(val id: String, val displayName: String)
