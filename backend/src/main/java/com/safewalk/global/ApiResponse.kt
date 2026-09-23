package com.safewalk.global

/**
 * 모든 API 응답을 감싸는 공통 응답 포맷.
 * 도메인 컨트롤러(auth, user, route, ...)는 이 타입을 반환값으로 사용한다.
 */
data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val message: String? = null
) {
    companion object {
        fun <T> ok(data: T? = null): ApiResponse<T> = ApiResponse(success = true, data = data)
        fun fail(message: String): ApiResponse<Nothing> = ApiResponse(success = false, message = message)
    }
}
