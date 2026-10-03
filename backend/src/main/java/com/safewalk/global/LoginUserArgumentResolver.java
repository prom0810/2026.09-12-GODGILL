package com.safewalk.global;

import com.safewalk.auth.UnauthorizedException;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * {@link LoginUser @LoginUser Long userId} 파라미터에 {@link AuthInterceptor}가 저장한 사용자 ID를 넣어준다.
 */
public class LoginUserArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(LoginUser.class)
                && Long.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory
    ) {
        Object userId = webRequest.getAttribute(AuthInterceptor.USER_ID_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        if (userId instanceof Long id) {
            return id;
        }
        // 인터셉터 적용 범위 밖(/api/auth/** 등)에서 @LoginUser를 쓴 경우
        throw new UnauthorizedException("로그인이 필요합니다.");
    }
}
