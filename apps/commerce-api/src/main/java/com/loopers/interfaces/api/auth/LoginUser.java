package com.loopers.interfaces.api.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 인증된 사용자의 ID를 컨트롤러 파라미터로 주입받기 위한 어노테이션.
 *
 * <p>AuthInterceptor에서 인증된 사용자 ID를 컨트롤러 메서드의 파라미터로 주입한다.</p>
 *
 * <pre>{@code
 * @GetMapping("/me")
 * public ApiResponse<MeResponse> getMyInfo(@LoginUser Long userId) {
 *     // userId는 인증된 사용자의 ID
 * }
 * }</pre>
 *
 * @see LoginUserArgumentResolver
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface LoginUser {
}