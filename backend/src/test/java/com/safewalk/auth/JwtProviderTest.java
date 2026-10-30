package com.safewalk.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class JwtProviderTest {

    private static final String SECRET = "test-secret-key-for-jwt-provider-32-bytes-minimum";

    @Test
    void createdTokenCanBeParsedToOriginalUserId() {
        JwtProvider provider = new JwtProvider(SECRET, 60_000);

        String token = provider.createToken(42L, "user@example.com", "ADULT");

        assertThat(provider.parseUserId(token)).isEqualTo(42L);
    }

    @Test
    void tokenSignedWithDifferentSecretIsRejected() {
        JwtProvider issuer = new JwtProvider(SECRET, 60_000);
        JwtProvider verifier = new JwtProvider("different-test-secret-for-jwt-provider-32-bytes", 60_000);

        String token = issuer.createToken(42L, "user@example.com", "ADULT");

        assertThatThrownBy(() -> verifier.parseUserId(token))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void expiredTokenIsRejected() {
        JwtProvider provider = new JwtProvider(SECRET, -1_000);

        String token = provider.createToken(42L, "user@example.com", "ADULT");

        assertThatThrownBy(() -> provider.parseUserId(token))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void tamperedTokenIsRejected() {
        JwtProvider provider = new JwtProvider(SECRET, 60_000);
        String token = provider.createToken(42L, "user@example.com", "ADULT");
        String[] parts = token.split("\\.");
        char replacement = parts[2].charAt(0) == 'A' ? 'B' : 'A';
        parts[2] = replacement + parts[2].substring(1);
        String tamperedToken = String.join(".", parts);

        assertThatThrownBy(() -> provider.parseUserId(tamperedToken))
                .isInstanceOf(UnauthorizedException.class);
    }
}
