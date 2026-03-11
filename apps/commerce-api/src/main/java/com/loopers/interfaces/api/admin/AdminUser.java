package com.loopers.interfaces.api.admin;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 인증된 관리자 인자를 주입받을 때 사용.
 * {@link AdminUserArgumentResolver}가 LDAP 헤더로 인증 후
 * {@link com.loopers.application.admin.AdminPrincipal}을 주입한다.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface AdminUser {
}
