package com.loopers.interfaces.api.auth;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Component
public class AdminUserArgumentResolver implements HandlerMethodArgumentResolver {

    private static final String HEADER_ADMIN_LDAP = "X-Loopers-AdminLdap";

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(AdminUser.class)
            && parameter.getParameterType().equals(String.class);
    }

    @Override
    public String resolveArgument(
        MethodParameter parameter,
        ModelAndViewContainer mavContainer,
        NativeWebRequest webRequest,
        WebDataBinderFactory binderFactory
    ) {
        String adminLdap = webRequest.getHeader(HEADER_ADMIN_LDAP);

        if (adminLdap == null || adminLdap.isBlank()) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "관리자 인증 헤더가 누락되었습니다.");
        }

        return adminLdap;
    }
}
