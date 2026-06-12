# KCandleKit

A candlestick chart library for Android, built with Jetpack Compose.  
Plug in any exchange or securities API — the chart and pattern engine are fully decoupled from data sources.

> **Portfolio project** — demonstrates clean architecture (multimodule, repository pattern, Kotlin Coroutines/Flow, Compose Canvas).

---

<img width="324" height="675" alt="image" src="https://github.com/user-attachments/assets/accc48b2-6c47-4dac-abcc-3fad75155a12" />

## Features

- **Candlestick chart** rendered on Compose Canvas with pinch-zoom and horizontal scroll
- **Real-time updates** via WebSocket with smooth live-tick rendering
- **11 candlestick patterns** detected automatically (Hammer, Engulfing, Morning Star, Three White Soldiers …)
- **Moving averages** — MA5 / MA20 / MA60 (fully configurable periods and colors)
- **Drawing tools** — horizontal line & trend line
- **Crosshair** on long-press drag with price/time label
- **Volume bars** scaled to the visible range
- **Multi-exchange ready** — implement two interfaces and connect any API

---

## Modules

| Module | Type | Description |
|--------|------|-------------|
| `kcandlekit-core` | Kotlin JVM | `Candle`, `PatternDetector`, `Indicators` (SMA/ATR/RSI), repository interfaces |
| `kcandlekit-compose` | Android Library | `CandleChart` composable, `CandleChartConfig`, `DrawingTool` |

---

## Installation

### JitPack

[![](https://jitpack.io/v/pointmina/KCandleKit.svg)](https://jitpack.io/#pointmina/KCandleKit)

**`settings.gradle.kts`**
```kotlin
dependencyResolutionManagement {
    repositories {
        maven { url = uri("https://jitpack.io") }
        google()
        mavenCentral()
    }
}
```

**`build.gradle.kts`**
```kotlin
dependencies {
    implementation("com.github.pointmina.KCandleKit:core:v0.1.0")
    implementation("com.github.pointmina.KCandleKit:compose:v0.1.0")
}
```

---

## Quick Start

### 1. Display a chart

```kotlin
@Composable
fun MyScreen(candles: List<Candle>) {
    val patterns = remember(candles) { PatternDetector.detect(candles) }

    CandleChart(
        candles  = candles,
        patterns = patterns,
        modifier = Modifier.fillMaxSize(),
    )
}
```

### 2. Customize appearance

```kotlin
CandleChart(
    candles = candles,
    config  = CandleChartConfig(
        bullishColor        = Color(0xFF26A69A),
        bearishColor        = Color(0xFFEF5350),
        showVolume          = true,
        showPatternMarkers  = true,
        showStrongSignalBadge = true,       // BUY / SELL badge on strong patterns
        movingAverages      = listOf(
            MovingAverageConfig(5,  Color(0xFFF48FB1)),
            MovingAverageConfig(20, Color(0xFF90CAF9)),
        ),
    ),
)
```

### 3. Drawing tools

```kotlin
var activeTool   by remember { mutableStateOf(DrawingTool.NONE) }
var drawingLines by remember { mutableStateOf(emptyList<DrawingLine>()) }

CandleChart(
    candles              = candles,
    activeTool           = activeTool,
    drawingLines         = drawingLines,
    onDrawingLineAdded   = { drawingLines = drawingLines + it },
    onDrawingLineRemoved = { drawingLines = drawingLines - it },
)
```

---

## Connecting Your Exchange API

Implement two interfaces from `core` and pass them to your ViewModel.

```kotlin
// 1. Implement CandleRepository
class BinanceCandleRepository(private val api: BinanceApi) : CandleRepository {
    override suspend fun getCandles(
        market: MarketSpec,
        interval: CandleIntervalSpec,
    ): List<Candle> {
        // map MarketSpec / CandleIntervalSpec to your API params
        return api.getKlines(symbol = market.id, interval = "1m", limit = interval.count)
            .map { it.toCandle() }
    }
}

// 2. Implement TickerRepository (optional — for real-time tick updates)
class BinanceTickerRepository(private val client: OkHttpClient) : TickerRepository {
    override fun priceFlow(market: MarketSpec): Flow<TickerUpdate> =
        binanceTickerFlow(client, market.id)
            .map { TickerUpdate(price = it.price, timestamp = it.time) }
}

// 3. Inject into your ViewModel
val viewModel = MyChartViewModel(
    candleRepo = BinanceCandleRepository(BinanceApi.service),
    tickerRepo = BinanceTickerRepository(okHttpClient),
)
```

### `CandleIntervalSpec`

| Property | Type | Description |
|----------|------|-------------|
| `unit` | `CandleUnit` | `MINUTE`, `DAY`, `WEEK`, `MONTH` |
| `minuteValue` | `Int` | Minute count when `unit == MINUTE` (e.g. 1, 5, 15, 60) |
| `count` | `Int` | Number of candles to fetch |

---

## Pattern Detection

`PatternDetector.detect(candles)` returns a list of `PatternResult`.

| Pattern | Signal | Strength |
|---------|--------|----------|
| Three White Soldiers | BULLISH | **STRONG** |
| Morning Star | BULLISH | **STRONG** |
| Three Black Crows | BEARISH | **STRONG** |
| Evening Star | BEARISH | **STRONG** |
| Bullish Engulfing | BULLISH | NORMAL |
| Bearish Engulfing | BEARISH | NORMAL |
| Hammer | BULLISH | NORMAL |
| Shooting Star | BEARISH | NORMAL |
| Inverted Hammer | BULLISH | NORMAL |
| Hanging Man | BEARISH | NORMAL |
| Doji | NEUTRAL | NORMAL |

`STRONG` patterns display a **BUY / SELL** badge on the chart. `NORMAL` patterns display a triangle marker.

---

## Technical Indicators

```kotlin
val closes = candles.map { it.close.toDouble() }

val sma20 = Indicators.sma(closes, period = 20)   // List<Double?>
val atr14 = Indicators.atr(candles, period = 14)   // List<Double?>
val rsi14  = Indicators.rsi(closes, period = 14)   // List<Double?>
```

---

## Architecture

```
core/          ← Pure Kotlin, no Android dependency
  Candle
  PatternDetector
  Indicators
  CandleRepository  (interface)
  TickerRepository  (interface)
  MarketSpec / CandleIntervalSpec / TickerUpdate

compose/       ← Android library, depends on core
  CandleChart  (Composable)
  CandleChartConfig
  DrawingTool

app/           ← Demo app (not published)
  UpbitCandleRepository   ← Upbit implementation
  UpbitTickerRepository   ← Upbit WebSocket
  ChartViewModel
```

---

## License

```
Copyright 2025 hanto (pointmina)

Licensed under the Apache License, Version 2.0
```
