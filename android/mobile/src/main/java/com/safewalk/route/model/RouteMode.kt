package com.safewalk.route.model

/**
 * 이동자 유형별 안전 가중치(safetyWeight).
 * 0에 가까울수록 최단거리 위주, 1에 가까울수록 안전도 위주로 경로를 계산한다.
 * MVP 단계의 초기값이며, 추후 실사용 피드백/공공데이터로 보정 예정.
 */
enum class RouteMode(val label: String, val safetyWeight: Double) {
    STANDARD("일반", 0.4),
    STUDENT("학생(하교·야간 이동)", 0.55),
    WOMAN("여성", 0.6),
    ELDERLY("고령자", 0.65),
    MOBILITY_IMPAIRED("교통약자", 0.7);

    override fun toString(): String = label
}
