package com.safewalk.global;

import com.safewalk.auth.DuplicateEmailException;
import com.safewalk.auth.DuplicatePhoneException;
import com.safewalk.auth.InvalidCredentialsException;
import com.safewalk.auth.UnauthorizedException;
import com.safewalk.guardian.DuplicateGuardianException;
import com.safewalk.guardian.GuardianNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 전역 예외 처리기.
 * 보안 필터, DB 설정 등 다른 전역 인프라 코드도 이 패키지(global)에 함께 위치시킨다.
 * 인터셉터({@link AuthInterceptor})에서 던진 예외도 이곳에서 처리된다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException e) {
        return fail(HttpStatus.BAD_REQUEST, e, "잘못된 요청입니다.");
    }

    /** JSON 형식 오류, 타입 불일치(예: guardians에 배열이 아닌 값) 등 요청 바디를 읽지 못한 경우 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotReadable(HttpMessageNotReadableException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail("요청 본문(JSON) 형식이 올바르지 않습니다."));
    }

    /** 경로 변수 타입 불일치. 예: DELETE /api/guardians/abc */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail("요청 값의 형식이 올바르지 않습니다: " + e.getName()));
    }

    @ExceptionHandler(GuardianNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleGuardianNotFound(GuardianNotFoundException e) {
        return fail(HttpStatus.NOT_FOUND, e, "보호자를 찾을 수 없습니다.");
    }

    /** 존재하지 않는 경로 */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.fail("존재하지 않는 API입니다."));
    }

    /** 경로는 있지만 지원하지 않는 HTTP 메서드. 예: DELETE /api/guardians (ID 없음) */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiResponse.fail("지원하지 않는 요청 방식입니다: " + e.getMethod()));
    }

    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicateEmail(DuplicateEmailException e) {
        return fail(HttpStatus.CONFLICT, e, "이미 가입된 이메일입니다.");
    }

    @ExceptionHandler(DuplicatePhoneException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicatePhone(DuplicatePhoneException e) {
        return fail(HttpStatus.CONFLICT, e, "이미 가입된 전화번호입니다.");
    }

    @ExceptionHandler(DuplicateGuardianException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicateGuardian(DuplicateGuardianException e) {
        return fail(HttpStatus.CONFLICT, e, "이미 등록된 보호자입니다.");
    }

    /** 코드 검사를 통과했지만 DB 제약(UNIQUE 등)에 걸린 경우. 예: 같은 전화번호로 동시에 가입 */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.fail("이미 등록된 정보와 중복됩니다."));
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidCredentials(InvalidCredentialsException e) {
        return fail(HttpStatus.UNAUTHORIZED, e, "인증에 실패했습니다.");
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnauthorized(UnauthorizedException e) {
        return fail(HttpStatus.UNAUTHORIZED, e, "로그인이 필요합니다.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        return fail(HttpStatus.INTERNAL_SERVER_ERROR, e, "서버 내부 오류가 발생했습니다.");
    }

    private ResponseEntity<ApiResponse<Void>> fail(HttpStatus status, Exception e, String defaultMessage) {
        String message = e.getMessage() != null ? e.getMessage() : defaultMessage;
        return ResponseEntity.status(status).body(ApiResponse.fail(message));
    }
}
