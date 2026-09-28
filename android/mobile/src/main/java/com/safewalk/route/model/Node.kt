package com.safewalk.route.model

/**
 * 경로 그래프의 정점(교차점)을 나타냄.
 * id는 격자 좌표(row_col) 형태의 문자열 키로 사용.
 */
data class Node(
    val id: String,
    val lat: Double,
    val lon: Double
)
