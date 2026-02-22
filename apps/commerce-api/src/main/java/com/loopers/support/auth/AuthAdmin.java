package com.loopers.support.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 어드민 인증 어노테이션
 *
 * Controller 메서드 파라미터에 선언하면 {@link AdminAuthResolver}가
 * 요청 헤더의 X-Loopers-Ldap 값을 검증하고, 인증된 LDAP 값을 주입한다.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuthAdmin {
}
