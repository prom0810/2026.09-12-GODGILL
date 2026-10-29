package com.safewalk.global;

import com.safewalk.auth.DuplicateEmailException;
import com.safewalk.auth.DuplicatePhoneException;
import com.safewalk.auth.InvalidCredentialsException;
import com.safewalk.auth.UnauthorizedException;
import com.safewalk.guardian.DuplicateGuardianException;
import com.safewalk.guardian.GuardianNotFoundException;
import com.safewalk.guardian.GuardianStateException;
import com.safewalk.notification.NotificationNotFoundException;
import com.safewalk.report.RiskReportNotFoundException;
import com.safewalk.storage.ImageStorageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 전역 예외 처리기.
 * 보안 필터, DB 설정 등 다른 전역 인프라 코드도 이 패키지(global)에 함께 위치시킨다.
 * 인터셉터({@link AuthInterceptor})에서 던진 예외도 이곳에서 처리된다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

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

    @ExceptionHandler(NotificationNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotificationNotFound(NotificationNotFoundException e) {
        return fail(HttpStatus.NOT_FOUND, e, "알림을 찾을 수 없습니다.");
    }

    @ExceptionHandler(RiskReportNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleRiskReportNotFound(RiskReportNotFoundException e) {
        return fail(HttpStatus.NOT_FOUND, e, "제보를 찾을 수 없습니다.");
    }

    /** 필수 폼/쿼리 값 누락. 예: 제보 작성 시 lat 없음 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParameter(MissingServletRequestParameterException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail("필수 값이 없습니다: " + e.getParameterName()));
    }

    /** 필수 파일 누락. 예: 사진 추가 시 images 없음 */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingPart(MissingServletRequestPartException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail("필수 파일이 없습니다: " + e.getRequestPartName()));
    }

    /** 업로드 크기 초과 (application.yml의 spring.servlet.multipart 제한) */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSize(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ApiResponse.fail("파일이 너무 큽니다. 사진 1장은 10MB 이하로 올려 주세요."));
    }

    /** multipart 요청 형식 오류 */
    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiResponse<Void>> handleMultipart(MultipartException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail("파일 업로드 요청(multipart/form-data) 형식이 올바르지 않습니다."));
    }

    /** Content-Type 불일치. 예: 사진 업로드 API에 JSON으로 요청 */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMediaType(HttpMediaTypeNotSupportedException e) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(ApiResponse.fail("지원하지 않는 요청 형식입니다: " + e.getContentType()));
    }

    /** 이미지 저장소(Supabase Storage) 통신 실패. 원인은 서버 로그에만 남긴다. */
    @ExceptionHandler(ImageStorageException.class)
    public ResponseEntity<ApiResponse<Void>> handleImageStorage(ImageStorageException e) {
        log.error("이미지 저장소 오류", e);
        return fail(HttpStatus.BAD_GATEWAY, e, "이미지 저장소 처리 중 오류가 발생했습니다.");
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

    /** 보호자 연결 상태가 맞지 않음. 예: 이미 연결된 보호자에게 다시 초대 */
    @ExceptionHandler(GuardianStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleGuardianState(GuardianStateException e) {
        return fail(HttpStatus.CONFLICT, e, "현재 보호자 연결 상태에서는 처리할 수 없습니다.");
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
