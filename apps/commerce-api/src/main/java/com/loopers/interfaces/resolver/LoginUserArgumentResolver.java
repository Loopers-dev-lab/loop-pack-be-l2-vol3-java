package com.loopers.interfaces.resolver;

import com.loopers.domain.member.Member;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.NonNull;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Component
public class LoginUserArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(LoginUser.class)
                && Member.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(@NonNull MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        HttpServletRequest request = (HttpServletRequest) webRequest.getNativeRequest();
        Object attribute = request.getAttribute("authenticatedMember");

        LoginUser loginUser = parameter.getParameterAnnotation(LoginUser.class);
        boolean required = loginUser == null || loginUser.required();

        if (!(attribute instanceof Member)) {
            if (required) {
                throw new CoreException(ErrorType.UNAUTHORIZED, "인증 정보가 유효하지 않습니다.");
            }
            return null;
        }

        return attribute;
    }
}
