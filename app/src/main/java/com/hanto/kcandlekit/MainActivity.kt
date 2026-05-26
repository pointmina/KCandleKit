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
        val rawChange = (random.nextFloat() - 0.48f) * price * 0.03f

        // 캔들 종류 결정
        val isDoji      = random.nextFloat() < 0.05f   //  5%: 도지 (open ≈ close, 심지 길게)
        val hasLongWick = !isDoji && random.nextFloat() < 0.15f  // 15%: 긴꼬리
        // 나머지 70%: 일반 캔들

        val change = when {
            isDoji -> rawChange * 0.04f  // 몸통 거의 0 → body/range 가 10% 미만 → DOJI 검출
            kotlin.math.abs(rawChange) < price * 0.005f ->
                price * 0.005f * if (rawChange >= 0f) 1f else -1f  // 일반/긴꼬리 최솟값 보장
            else -> rawChange
        }

        val open = price
        val close = (price + change).coerceAtLeast(1f)
        val body = kotlin.math.abs(close - open)
        val longWickIsUpper = hasLongWick && random.nextBoolean()

        val upperWick = when {
            isDoji      -> price * (random.nextFloat() * 0.008f + 0.004f)  // 가격 기준 심지
            longWickIsUpper -> body * (random.nextFloat() * 1.5f + 2.5f)
            else        -> body * random.nextFloat() * 0.08f
        }
        val lowerWick = when {
            isDoji               -> price * (random.nextFloat() * 0.008f + 0.004f)
            hasLongWick && !longWickIsUpper -> body * (random.nextFloat() * 1.5f + 2.5f)
            else                 -> body * random.nextFloat() * 0.08f
        }

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
