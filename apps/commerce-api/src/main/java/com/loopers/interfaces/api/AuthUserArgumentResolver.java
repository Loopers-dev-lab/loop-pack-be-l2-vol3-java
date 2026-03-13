package com.loopers.interfaces.api;

import com.loopers.domain.user.UserModel;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * {@link AuthUser} 어노테이션이 붙은 파라미터에 인증된 사용자 정보를 주입하는 ArgumentResolver.
 *
 * <p>{@link CustomerAuthInterceptor}에서 request attribute에 저장된 {@link UserModel}을
 * 컨트롤러 메서드 파라미터로 주입한다.</p>
 */
@Component
public class AuthUserArgumentResolver implements HandlerMethodArgumentResolver {

    /**
     * 해당 파라미터를 지원하는지 확인한다.
     *
     * @param parameter 메서드 파라미터
     * @return {@link AuthUser} 어노테이션이 있고 타입이 {@link UserModel}이면 {@code true}
     */
    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(AuthUser.class)
                && UserModel.class.isAssignableFrom(parameter.getParameterType());
    }

    /**
     * 인증된 사용자 정보를 반환한다.
     *
     * @param parameter     메서드 파라미터
     * @param mavContainer  ModelAndView 컨테이너
     * @param webRequest    웹 요청
     * @param binderFactory 바인더 팩토리
     * @return 인증된 {@link UserModel}
     * @throws CoreException 인증된 사용자가 없을 때 (UNAUTHORIZED)
     */
    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        Object user = webRequest.getAttribute(CustomerAuthInterceptor.AUTH_USER_ATTR,
                RequestAttributes.SCOPE_REQUEST);
        if (user == null) {
            throw new CoreException(ErrorType.UNAUTHORIZED);
        }
        return user;
    }
}
