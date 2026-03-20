package com.loopers.interfaces.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 컨트롤러 메서드 파라미터에 인증된 사용자 정보를 주입하기 위한 어노테이션.
 *
 * <p>{@link CustomerAuthInterceptor}에서 인증된 {@link com.loopers.domain.user.UserModel}을
 * 컨트롤러 메서드 파라미터로 주입받을 때 사용한다.</p>
 *
 * <pre>{@code
 * @GetMapping("/me")
 * public ResponseEntity<...> getMyInfo(@AuthUser UserModel user) {
 *     // user is the authenticated user
 * }
 * }</pre>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuthUser {
}
