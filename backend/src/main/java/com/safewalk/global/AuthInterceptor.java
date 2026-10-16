package com.safewalk.global;

import com.safewalk.auth.JwtProvider;
import com.safewalk.auth.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * JWT 인증 인터셉터.
 *
 * {@link WebConfig}에서 /api/** 전체에 적용하고 /api/auth/**(회원가입·로그인)만 제외한다.
 * 요청 헤더 "Authorization: Bearer {토큰}"을 검증한 뒤, 토큰의 사용자 ID를 요청 속성에 저장한다.
 * 컨트롤러에서는 {@link LoginUser @LoginUser Long userId} 파라미터로 그 값을 받는다.
 *
 * 검증에 실패하면 {@link UnauthorizedException}을 던지고, {@link GlobalExceptionHandler}가
 * 401 + 공통 응답 형식({success:false, data:null, message})으로 변환한다.
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    /** 인증된 사용자 ID를 담아두는 요청 속성 이름 */
    public static final String USER_ID_ATTRIBUTE = "safewalk.authenticatedUserId";

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtProvider jwtProvider;

    public AuthInterceptor(JwtProvider jwtProvider) {
        this.jwtProvider = jwtProvider;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // CORS preflight 요청은 토큰 없이 통과시킨다.
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            throw new UnauthorizedException("로그인이 필요합니다. Authorization: Bearer {토큰} 헤더를 포함해 주세요.");
        }

        String token = header.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            throw new UnauthorizedException("로그인이 필요합니다. Authorization: Bearer {토큰} 헤더를 포함해 주세요.");
        }

        Long userId = jwtProvider.parseUserId(token);
        request.setAttribute(USER_ID_ATTRIBUTE, userId);
        return true;
    }
}
