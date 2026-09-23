package com.safewalk.auth

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.util.Date
import javax.crypto.SecretKey

/**
 * 로그인/회원가입 성공 시 발급하는 JWT 생성기.
 * 지금 단계에서는 토큰 발급까지만 하고, 다른 API를 이 토큰으로 보호하는 필터는 아직 없다.
 */
@Component
class JwtProvider(
    @Value("\${jwt.secret}") secret: String,
    @Value("\${jwt.expiration-ms:86400000}") private val expirationMs: Long
) {
    private val key: SecretKey = Keys.hmacShaKeyFor(secret.toByteArray(StandardCharsets.UTF_8))

    fun createToken(userId: Long, email: String, userType: String): String {
        val now = Date()
        val expiry = Date(now.time + expirationMs)
        return Jwts.builder()
            .subject(userId.toString())
            .claim("email", email)
            .claim("userType", userType)
            .issuedAt(now)
            .expiration(expiry)
            .signWith(key)
            .compact()
    }
}
