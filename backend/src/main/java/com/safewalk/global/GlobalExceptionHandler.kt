package com.safewalk.global

import com.safewalk.auth.DuplicateEmailException
import com.safewalk.auth.InvalidCredentialsException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * 전역 예외 처리기.
 * 보안 필터, DB 설정 등 다른 전역 인프라 코드도 이 패키지(global)에 함께 위치시킨다.
 * TODO: 도메인별 커스텀 예외가 추가되면 이곳에 개별 핸들러를 등록한다.
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(e: IllegalArgumentException): ResponseEntity<ApiResponse<Nothing>> {
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.fail(e.message ?: "잘못된 요청입니다."))
    }

    @ExceptionHandler(DuplicateEmailException::class)
    fun handleDuplicateEmail(e: DuplicateEmailException): ResponseEntity<ApiResponse<Nothing>> {
        return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(ApiResponse.fail(e.message ?: "이미 가입된 이메일입니다."))
    }

    @ExceptionHandler(InvalidCredentialsException::class)
    fun handleInvalidCredentials(e: InvalidCredentialsException): ResponseEntity<ApiResponse<Nothing>> {
        return ResponseEntity
            .status(HttpStatus.UNAUTHORIZED)
            .body(ApiResponse.fail(e.message ?: "인증에 실패했습니다."))
    }

    @ExceptionHandler(Exception::class)
    fun handleException(e: Exception): ResponseEntity<ApiResponse<Nothing>> {
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.fail(e.message ?: "서버 내부 오류가 발생했습니다."))
    }
}
