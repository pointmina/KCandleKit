package com.hanto.kcandlekit.core

/** 가이드 기준 신호 신뢰도: STRONG = ★★★ 이상, NORMAL = ★★ 이하 */
enum class SignalStrength { STRONG, NORMAL }

data class PatternResult(
    val index: Int,        // 패턴이 감지된 마지막 캔들의 인덱스
    val pattern: CandlePattern,
    val signal: Signal,
    val span: Int = 1,     // 패턴이 걸치는 캔들 수 (단일=1, 엔걸핑=2, 스타/쓰리솔져스=3)
    val strength: SignalStrength = SignalStrength.NORMAL,
)

enum class CandlePattern {
    // 단일 캔들 패턴
    HAMMER,
    INVERTED_HAMMER,
    SHOOTING_STAR,
    HANGING_MAN,
    DOJI,

    // 복합 캔들 패턴
    BULLISH_ENGULFING,
    BEARISH_ENGULFING,
    MORNING_STAR,
    EVENING_STAR,
    THREE_WHITE_SOLDIERS,
    THREE_BLACK_CROWS,
}

// 패턴이 암시하는 방향성 — 단순 매수/매도 신호가 아닌 보조 지표임에 유의
enum class Signal { BULLISH, BEARISH, NEUTRAL }
