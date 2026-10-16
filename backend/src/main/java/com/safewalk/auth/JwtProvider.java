package com.safewalk.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * JWT 발급·검증기.
 *  - 발급: 로그인/회원가입 성공 시 {@link #createToken}
 *  - 검증: {@link com.safewalk.global.AuthInterceptor}가 매 요청마다 {@link #parseUserId}로 토큰을 확인한다.
 * 토큰의 subject(sub)에는 users.user_id를 담는다.
 */
@Component
public class JwtProvider {

    private final SecretKey key;
    private final long expirationMs;

    public JwtProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration-ms:86400000}") long expirationMs
    ) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    public String createToken(Long userId, String email, String userType) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("email", email)
                .claim("userType", userType)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    /**
     * 토큰의 서명과 만료 시각을 검증하고, subject에 담긴 사용자 ID를 반환한다.
     *
     * @throws UnauthorizedException 서명 불일치, 만료, 형식 오류 등 검증에 실패한 경우
     */
    public Long parseUserId(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Long.valueOf(claims.getSubject());
        } catch (JwtException | IllegalArgumentException e) {
            // IllegalArgumentException: 빈 토큰, subject가 숫자가 아닌 경우(NumberFormatException) 포함
            throw new UnauthorizedException("유효하지 않거나 만료된 토큰입니다. 다시 로그인해 주세요.");
        }
    }
}
