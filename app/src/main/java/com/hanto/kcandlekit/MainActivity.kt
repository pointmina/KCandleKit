package com.hanto.kcandlekit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hanto.kcandlekit.compose.CandleChart
import com.hanto.kcandlekit.core.DrawingLine
import com.hanto.kcandlekit.ui.theme.KCandleKitTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KCandleKitTheme {
                ChartScreen()
            }
        }
    }
}

private val BG = Color(0xFF131722)       // TradingView 다크 배경
private val SURFACE = Color(0xFF1E2130)  // 컨트롤 영역

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartScreen(vm: ChartViewModel = viewModel(factory = ChartViewModelFactory())) {
    val uiState          by vm.uiState.collectAsState()
    val selectedMarket   by vm.selectedMarket.collectAsState()
    val selectedInterval by vm.selectedInterval.collectAsState()
    val drawingLines     by vm.drawingLines.collectAsState()
    val activeTool       by vm.activeTool.collectAsState()
    var lineToDelete     by remember { mutableStateOf<DrawingLine?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "${selectedMarket.displayName} / KRW",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color.White,
                        )
                        if ((uiState as? ChartUiState.Success)?.isLive == true) {
                            LiveDot()
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SURFACE),
            )
        },
        containerColor = BG,
    ) { innerPadding ->
        // 선 삭제 확인 다이얼로그
        lineToDelete?.let { line ->
            AlertDialog(
                onDismissRequest = { lineToDelete = null },
                title   = { Text("선 삭제") },
                text    = { Text("이 선을 삭제할까요?") },
                confirmButton = {
                    TextButton(onClick = {
                        vm.removeDrawingLine(line)
                        lineToDelete = null
                    }) { Text("삭제", color = Color(0xFFEF5350)) }
                },
                dismissButton = {
                    TextButton(onClick = { lineToDelete = null }) { Text("취소") }
                },
            )
        }
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        ) {
            // 코인 선택 칩 행
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SURFACE),
            ) {
                items(MARKETS) { market ->
                    MarketChip(
                        label    = market.displayName,
                        selected = selectedMarket == market,
                        onClick  = { vm.selectMarket(market) },
                    )
                }
            }

            // 봉 종류 선택 칩 행 (5분~월봉, 가로 스크롤)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F1217)),
            ) {
                items(Interval.entries) { interval ->
                    IntervalChip(
                        label    = interval.label,
                        selected = selectedInterval == interval,
                        onClick  = { vm.selectInterval(interval) },
                    )
                }
            }

            // 차트 / 로딩 / 에러
            Box(modifier = Modifier.fillMaxSize()) {
                when (val state = uiState) {
                    is ChartUiState.Loading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center),
                            color = Color(0xFF26A69A),
                        )
                    }

                    is ChartUiState.Error -> {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text  = "데이터를 불러오지 못했어요",
                                color = Color(0xFFEF5350),
                                fontSize = 14.sp,
                            )
                            Text(
                                text     = state.message,
                                color    = Color(0xFF787B86),
                                fontSize = 11.sp,
                                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
                            )
                            Button(onClick = { vm.reload() }) {
                                Text("다시 시도")
                            }
                        }
                    }

                    is ChartUiState.Success -> {
                        Box(modifier = Modifier.fillMaxSize()) {
                            CandleChart(
                                candles              = state.candles,
                                scrollResetKey       = "${selectedMarket.id}_${selectedInterval.name}",
                                patterns             = state.patterns,
                                drawingLines         = drawingLines,
                                activeTool           = activeTool,
                                onDrawingLineAdded   = { vm.addDrawingLine(it) },
                                onDrawingLineRemoved = { lineToDelete = it },
                                modifier             = Modifier.fillMaxSize(),
                            )
                            DrawingFab(
                                activeTool     = activeTool,
                                onToolSelected = { vm.selectDrawingTool(it) },
                                modifier       = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── 컴포넌트 ──────────────────────────────────────────────────────────────────

@Composable
private fun LiveDot() {
    val infiniteTransition = rememberInfiniteTransition(label = "live")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue  = 1.3f,
        animationSpec = infiniteRepeatable(
            animation  = tween(durationMillis = 800),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "live_scale",
    )
    Box(
        modifier = Modifier
            .size(8.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(Color(0xFF26A69A)),
    )
}


@Composable
private fun MarketChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick  = onClick,
        label    = { Text(label, fontSize = 12.sp) },
        colors   = FilterChipDefaults.filterChipColors(
            selectedContainerColor    = Color(0xFF26A69A),
            selectedLabelColor        = Color.White,
            containerColor            = Color(0xFF2A2E39),
            labelColor                = Color(0xFFB2B5BE),
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled              = true,
            selected             = selected,
            selectedBorderColor  = Color.Transparent,
            borderColor          = Color(0xFF363A45),
        ),
    )
}

@Composable
private fun IntervalChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick  = onClick,
        label    = { Text(label, fontSize = 11.sp) },
        colors   = FilterChipDefaults.filterChipColors(
            selectedContainerColor    = Color(0xFF2962FF),
            selectedLabelColor        = Color.White,
            containerColor            = Color.Transparent,
            labelColor                = Color(0xFFB2B5BE),
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled              = true,
            selected             = selected,
            selectedBorderColor  = Color.Transparent,
            borderColor          = Color(0xFF363A45),
        ),
    )
}
