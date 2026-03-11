package com.loopers.interfaces.api;

import com.loopers.application.user.AuthUserPrincipal;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorMessage;
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
public class AuthUserArgumentResolver implements HandlerMethodArgumentResolver {

    private final UserService userService;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(AuthUser.class)
            && parameter.getParameterType().equals(AuthUserPrincipal.class);
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

        if (loginId == null || loginId.isBlank() || loginPw == null || loginPw.isBlank()) {
            throw new CoreException(ErrorType.UNAUTHORIZED, ErrorMessage.Auth.AUTH_HEADER_MISSING);
        }

        UserModel user = userService.authenticate(loginId, loginPw);
        return AuthUserPrincipal.from(user);
    }
}
