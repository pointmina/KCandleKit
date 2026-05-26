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
import com.hanto.kcandlekit.ui.theme.KCandleKitTheme
import kotlin.random.Random

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KCandleKitTheme {
                val sampleCandles = remember { generateSampleCandles(count = 120) }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    CandleChart(
                        candles = sampleCandles,
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxWidth()
                            .height(400.dp)
                    )
                }
            }
        }
    }
}

private fun generateSampleCandles(count: Int): List<Candle> {
    val random = Random(seed = 42) // 재현 가능한 시드
    val candles = mutableListOf<Candle>()
    var price = 50_000f                // 시작가 (원화 기준 5만원 주식 가정)
    val dayMs = 24 * 60 * 60 * 1_000L
    val baseTime = System.currentTimeMillis() - count * dayMs

    repeat(count) { i ->
        val change = (random.nextFloat() - 0.48f) * price * 0.03f // ±3% 변동, 약간 상승 편향
        val open = price
        val close = (price + change).coerceAtLeast(1f)
        val high = maxOf(open, close) + random.nextFloat() * price * 0.01f
        val low = minOf(open, close) - random.nextFloat() * price * 0.01f
        val volume = (500_000 + random.nextLong(until = 4_500_000L)).coerceAtLeast(1L)

        candles += Candle(
            timestamp = baseTime + i * dayMs,
            open = open,
            high = high,
            low = low.coerceAtLeast(0.1f),
            close = close,
            volume = volume
        )
        price = close
    }
    return candles
}
