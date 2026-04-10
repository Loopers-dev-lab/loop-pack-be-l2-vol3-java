package com.loopers.interfaces.auth;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Component
@RequiredArgsConstructor
public class LoginUserArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        if (!LoginUser.class.isAssignableFrom(parameter.getParameterType())) {
            return false;
        }
        return parameter.hasParameterAnnotation(Login.class)
                || parameter.hasParameterAnnotation(OptionalLogin.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) {
        HttpServletRequest request = (HttpServletRequest) webRequest.getNativeRequest();
        LoginUser loginUser = (LoginUser) request.getAttribute("loginUser");

        if (loginUser == null && parameter.hasParameterAnnotation(Login.class)) {
            throw new CoreException(ErrorType.UNAUTHORIZED);
        }

        return loginUser;
    }
}
