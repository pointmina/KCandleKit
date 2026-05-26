package com.hanto.kcandlekit.core

data class PatternResult(
    val index: Int,        // 패턴이 감지된 마지막 캔들의 인덱스
    val pattern: CandlePattern,
    val signal: Signal
)

enum class CandlePattern {
    // 단일 캔들 패턴 (Phase 2)
    HAMMER,
    INVERTED_HAMMER,
    SHOOTING_STAR,
    HANGING_MAN,
    DOJI,

    // 복합 캔들 패턴 (Phase 3)
    BULLISH_ENGULFING,
    BEARISH_ENGULFING,
    MORNING_STAR,
    EVENING_STAR,
    THREE_WHITE_SOLDIERS
}

// 패턴이 암시하는 방향성 — 단순 매수/매도 신호가 아닌 보조 지표임에 유의
enum class Signal { BULLISH, BEARISH, NEUTRAL }
