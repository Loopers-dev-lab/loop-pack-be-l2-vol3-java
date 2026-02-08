package com.loopers.support.auth;

import com.loopers.domain.user.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.UserErrorType;

/**
 * 인증 Argument Resolver
 *
 * @AuthUser가 붙은 Controller 파라미터에 인증된 User를 주입한다.
 * 헤더(X-Loopers-LoginId/Pw)를 추출하고, 인증은 UserService에 위임한다.
 */
@Component
public class AuthUserResolver implements HandlerMethodArgumentResolver {

    private static final String HEADER_LOGIN_ID = "X-Loopers-LoginId";
    private static final String HEADER_LOGIN_PW = "X-Loopers-LoginPw";

    private final UserService userService;

    public AuthUserResolver(UserService userService) {
        this.userService = userService;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(AuthUser.class);
    }

    @Override
    public Object resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory
    ) {
        HttpServletRequest request = (HttpServletRequest) webRequest.getNativeRequest();

        String loginId = request.getHeader(HEADER_LOGIN_ID);
        String password = request.getHeader(HEADER_LOGIN_PW);

        if (loginId == null || loginId.isBlank() || password == null || password.isBlank()) {
            throw new CoreException(UserErrorType.UNAUTHORIZED, "인증 헤더가 필요합니다.");
        }

        return this.userService.authenticateUser(loginId, password);
    }
}
