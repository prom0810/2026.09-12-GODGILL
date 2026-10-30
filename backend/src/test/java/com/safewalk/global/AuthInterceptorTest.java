package com.safewalk.global;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.safewalk.auth.JwtProvider;
import com.safewalk.auth.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;

@ExtendWith(MockitoExtension.class)
class AuthInterceptorTest {

    private static final String SECRET = "test-secret-key-for-auth-interceptor-32-bytes-minimum";

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    private JwtProvider jwtProvider;
    private AuthInterceptor interceptor;

    @BeforeEach
    void setUp() {
        jwtProvider = new JwtProvider(SECRET, 60_000);
        interceptor = new AuthInterceptor(jwtProvider);
    }

    @Test
    void requestWithoutAuthorizationHeaderIsRejected() {
        when(request.getMethod()).thenReturn("GET");

        assertThatThrownBy(() -> interceptor.preHandle(request, response, new Object()))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void requestWithEmptyBearerTokenIsRejected() {
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer   ");

        assertThatThrownBy(() -> interceptor.preHandle(request, response, new Object()))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void validBearerTokenStoresAuthenticatedUserId() {
        String token = jwtProvider.createToken(42L, "user@example.com", "ADULT");
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer " + token);

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertThat(allowed).isTrue();
        verify(request).setAttribute(AuthInterceptor.USER_ID_ATTRIBUTE, 42L);
    }

    @Test
    void optionsRequestPassesWithoutToken() {
        when(request.getMethod()).thenReturn("OPTIONS");

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
    }
}
