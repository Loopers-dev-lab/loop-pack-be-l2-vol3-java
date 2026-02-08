package com.loopers.support.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 인증된 사용자 주입 어노테이션
 *
 * Controller 메서드 파라미터에 선언하면 {@link AuthUserResolver}가
 * 요청 헤더의 인증 정보를 기반으로 User 객체를 주입한다.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuthUser {
}
