package com.loopers.interfaces.auth;

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

    private static final String VALID_LDAP = "loopers.admin";

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(AdminUser.class)
            && AdminInfo.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        String ldap = webRequest.getHeader("X-Loopers-Ldap");

        if (ldap == null || ldap.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "어드민 인증 헤더가 누락되었습니다.");
        }

        if (!VALID_LDAP.equals(ldap)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "유효하지 않은 어드민 인증입니다.");
        }

        return new AdminInfo(ldap);
    }
}
