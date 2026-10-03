package com.safewalk.global;

import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * JWT 인증 적용 범위 설정.
 *
 * 기본값은 "모든 API 잠김"이다: /api/** 전체에 토큰 검사를 걸고, 토큰을 발급받는 /api/auth/**만 예외로 둔다.
 * 새 API를 추가하면 자동으로 토큰 검사 대상이 된다.
 * 토큰 없이 열어야 하는 API가 생기면(예: 보호자용 위치 공유 링크) 아래 excludePathPatterns에 추가한다.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    public WebConfig(AuthInterceptor authInterceptor) {
        this.authInterceptor = authInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/**");
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new LoginUserArgumentResolver());
    }
}
