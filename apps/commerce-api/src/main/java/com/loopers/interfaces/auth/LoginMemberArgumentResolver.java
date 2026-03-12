package com.loopers.interfaces.auth;

import com.loopers.domain.member.MemberAuthService;
import com.loopers.domain.member.MemberModel;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@RequiredArgsConstructor
@Component
public class LoginMemberArgumentResolver implements HandlerMethodArgumentResolver {

    private final MemberAuthService memberAuthService;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(LoginMember.class)
            && MemberModel.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        String loginId = webRequest.getHeader("X-Loopers-LoginId");
        String loginPw = webRequest.getHeader("X-Loopers-LoginPw");

        if (loginId == null || loginPw == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "인증 헤더가 누락되었습니다.");
        }

        return memberAuthService.authenticate(loginId, loginPw);
    }
}
