package com.loopers.interfaces.api.auth;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * {@link LoginUser} 어노테이션이 붙은 파라미터에 인증된 사용자 ID를 주입하는 ArgumentResolver.
 *
 * <p>AuthInterceptor에서 인증 후 저장한 userId를 request attribute에서 가져와 컨트롤러 파라미터로 전달한다.</p>
 *
 * @see LoginUser
 * @see AuthInterceptor
 */
@Component
public class LoginUserArgumentResolver implements HandlerMethodArgumentResolver {

    private static final String USER_ID_ATTRIBUTE = "userId";

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(LoginUser.class)
                && parameter.getParameterType().equals(Long.class);
    }

    @Override
    public Object resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory
    ) {
        HttpServletRequest request = (HttpServletRequest) webRequest.getNativeRequest();
        return request.getAttribute(USER_ID_ATTRIBUTE);
    }
}
