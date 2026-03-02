package com.loopers.support.auth;

import com.loopers.support.error.AdminErrorType;
import com.loopers.support.error.CoreException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * 어드민 인증 Argument Resolver
 *
 * @AuthAdmin이 붙은 Controller 파라미터에 인증된 LDAP 값을 주입한다.
 * X-Loopers-Ldap 헤더가 "loopers.admin"인지 검증한다.
 * 실제 LDAP 서버 연동 없이 고정값 비교로 간소화 구현.
 */
@Component
public class AdminAuthResolver implements HandlerMethodArgumentResolver {

    private static final String HEADER_LDAP = "X-Loopers-Ldap";
    private static final String VALID_LDAP_VALUE = "loopers.admin";

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(AuthAdmin.class);
    }

    @Override
    public Object resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory
    ) {
        HttpServletRequest request = (HttpServletRequest) webRequest.getNativeRequest();

        String ldap = request.getHeader(HEADER_LDAP);

        // LDAP 헤더 미전송 또는 빈 값
        if (ldap == null || ldap.isBlank()) {
            throw new CoreException(AdminErrorType.UNAUTHORIZED_ADMIN, "어드민 인증 헤더가 필요합니다.");
        }

        // LDAP 값 불일치
        if (!VALID_LDAP_VALUE.equals(ldap)) {
            throw new CoreException(AdminErrorType.UNAUTHORIZED_ADMIN, "유효하지 않은 어드민 인증입니다.");
        }

        return ldap;
    }
}
