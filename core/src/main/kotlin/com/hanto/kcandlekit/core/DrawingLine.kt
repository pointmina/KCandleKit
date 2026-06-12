package com.hanto.kcandlekit.core

/**
 * 사용자가 차트에 그린 라인. Compose 의존성 없이 pure Kotlin으로 유지.
 * [id] 는 삭제 시 식별자로 사용.
 */
sealed class DrawingLine {
    abstract val id: Long

    /** 특정 가격에 고정된 수평선 */
    data class Horizontal(
        override val id: Long,
        val price: Float,
    ) : DrawingLine()

    /** 두 앵커 포인트를 연결하고 차트 양 끝까지 연장되는 추세선 */
    data class Trend(
        override val id: Long,
        val index1: Int, val price1: Float,
        val index2: Int, val price2: Float,
    ) : DrawingLine()
}
