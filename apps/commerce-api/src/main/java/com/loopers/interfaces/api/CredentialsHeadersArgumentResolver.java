package com.loopers.interfaces.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Component
@RequiredArgsConstructor
public class CredentialsHeadersArgumentResolver implements HandlerMethodArgumentResolver {

    private final Validator validator;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType() == CredentialsHeaders.class;
    }

    @Override
    public Object resolveArgument(
        MethodParameter parameter,
        ModelAndViewContainer mavContainer,
        NativeWebRequest webRequest,
        WebDataBinderFactory binderFactory
    ) {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        if (request == null) {
            throw new IllegalStateException("HttpServletRequest not available");
        }

        String loginId = request.getHeader(LoopersHeaders.X_LOOPERS_LOGIN_ID);
        String loginPw = request.getHeader(LoopersHeaders.X_LOOPERS_LOGIN_PW);

        CredentialsHeaders headers = new CredentialsHeaders(loginId, loginPw);
        Set<ConstraintViolation<CredentialsHeaders>> violations = validator.validate(headers);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
        return headers;
    }
}
