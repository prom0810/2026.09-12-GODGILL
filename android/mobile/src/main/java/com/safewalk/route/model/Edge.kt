package com.safewalk.route.model

/**
 * 두 노드를 잇는 도로 구간.
 * safetyScore: 0.0(매우 위험) ~ 1.0(매우 안전). MVP에서는 목업 데이터로 생성되며,
 * 추후 범죄주의구간/고령보행자 사고다발지역/도로위험도지수 등 공공데이터로 대체 가능.
 */
data class Edge(
    val toNodeId: String,
    val distanceMeters: Double,
    val safetyScore: Double
)
