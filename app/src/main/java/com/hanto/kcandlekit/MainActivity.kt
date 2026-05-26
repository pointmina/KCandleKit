package com.hanto.kcandlekit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hanto.kcandlekit.compose.CandleChart
import com.hanto.kcandlekit.core.Candle
import com.hanto.kcandlekit.core.PatternDetector
import com.hanto.kcandlekit.ui.theme.KCandleKitTheme
import kotlin.random.Random

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KCandleKitTheme {
                val sampleCandles = remember { generateSampleCandles(count = 120) }
                val patterns = remember(sampleCandles) { PatternDetector.detect(sampleCandles) }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    CandleChart(
                        candles = sampleCandles,
                        patterns = patterns,
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize()
                    )
                }
            }
        }
    }
}

private fun generateSampleCandles(count: Int): List<Candle> {
    val random = Random(seed = 42)
    val candles = mutableListOf<Candle>()
    var price = 50_000f
    val dayMs = 24 * 60 * 60 * 1_000L
    val baseTime = System.currentTimeMillis() - count * dayMs

    repeat(count) { i ->
        // 몸통: ±3% 변동 / 최솟값 0.5% 보장 → 심지 대비 몸통이 너무 작으면 모든 캔들이 DOJI로 검출됨
        val rawChange = (random.nextFloat() - 0.48f) * price * 0.03f
        val change = if (kotlin.math.abs(rawChange) < price * 0.005f)
            price * 0.005f * if (rawChange >= 0f) 1f else -1f
        else rawChange

        val open = price
        val close = (price + change).coerceAtLeast(1f)
        val body = kotlin.math.abs(close - open)

        // 75% 일반 캔들: 심지 ≤ 몸통의 8% (패턴 없음)
        // 25% 긴꼬리 캔들: 한쪽 심지가 몸통의 2.5~4배 (HAMMER / SHOOTING_STAR 등)
        val hasLongWick = random.nextFloat() < 0.25f
        val longWickIsUpper = hasLongWick && random.nextBoolean()

        val upperWick = if (longWickIsUpper) body * (random.nextFloat() * 1.5f + 2.5f)
                        else body * random.nextFloat() * 0.08f
        val lowerWick = if (hasLongWick && !longWickIsUpper) body * (random.nextFloat() * 1.5f + 2.5f)
                        else body * random.nextFloat() * 0.08f

        val high = maxOf(open, close) + upperWick
        val low = (minOf(open, close) - lowerWick).coerceAtLeast(0.1f)
        val volume = (500_000 + random.nextLong(until = 4_500_000L)).coerceAtLeast(1L)

        candles += Candle(
            timestamp = baseTime + i * dayMs,
            open = open, high = high, low = low, close = close, volume = volume
        )
        price = close
    }
    return candles
}
