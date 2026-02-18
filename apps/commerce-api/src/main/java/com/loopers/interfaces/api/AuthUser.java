package com.loopers.interfaces.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 인증된 사용자 인자를 주입받을 때 사용.
 * {@link AuthUserArgumentResolver}가 헤더(X-Loopers-LoginId, X-Loopers-LoginPw)로 인증 후
 * {@link com.loopers.application.AuthUserPrincipal}을 주입한다.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuthUser {
}
