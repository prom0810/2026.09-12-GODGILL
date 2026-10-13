package com.safewalk.global;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 컨트롤러 파라미터에 현재 로그인한 사용자의 ID(users.user_id)를 주입한다.
 *
 * <pre>
 * &#64;GetMapping("/me")
 * public ApiResponse&lt;UserResponse&gt; getMyInfo(&#64;LoginUser Long userId) { ... }
 * </pre>
 *
 * 값은 {@link AuthInterceptor}가 JWT에서 꺼내 둔 것이며, 요청 바디나 URL로 사용자 ID를 받지 않는다.
 * 타입은 반드시 Long이어야 한다.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface LoginUser {
}
