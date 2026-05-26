package com.hanto.kcandlekit.core

object PatternDetector {

    fun detect(candles: List<Candle>): List<PatternResult> {
        if (candles.size < 2) return emptyList()
        // Phase 2에서 단일 캔들 패턴 5종 구현 예정
        // Phase 3에서 복합 패턴 5종 구현 예정
        return emptyList()
    }
}
