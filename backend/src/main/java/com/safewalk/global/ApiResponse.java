package com.safewalk.global;

/**
 * 모든 API 응답을 감싸는 공통 응답 포맷.
 * 도메인 컨트롤러(auth, user, route, ...)는 이 타입을 반환값으로 사용한다.
 * JSON 형태: { "success": true, "data": ..., "message": null }
 */
public record ApiResponse<T>(boolean success, T data, String message) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static <T> ApiResponse<T> ok() {
        return new ApiResponse<>(true, null, null);
    }

    public static <T> ApiResponse<T> fail(String message) {
        return new ApiResponse<>(false, null, message);
    }
}
