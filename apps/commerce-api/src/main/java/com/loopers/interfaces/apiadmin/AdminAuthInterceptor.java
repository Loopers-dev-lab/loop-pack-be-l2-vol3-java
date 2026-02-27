package com.loopers.interfaces.apiadmin;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 관리자 API 인증 처리를 담당하는 인터셉터.
 *
 * <p>{@code X-Loopers-Ldap} 헤더 값을 검증하여 관리자 인증을 수행한다.
 * 인증에 실패하면 {@link CoreException}을 발생시킨다.</p>
 */
@Component
public class AdminAuthInterceptor implements HandlerInterceptor {

    private static final String ADMIN_HEADER = "X-Loopers-Ldap";
    private static final String ADMIN_VALUE = "loopers.admin";

    /**
     * 요청을 처리하기 전에 관리자 인증을 수행한다.
     *
     * @param request  HTTP 요청 객체
     * @param response HTTP 응답 객체
     * @param handler  요청을 처리할 핸들러 객체
     * @return 인증 성공 시 {@code true}
     * @throws CoreException 인증 실패 시 {@link ErrorType#ADMIN_UNAUTHORIZED} 예외 발생
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String ldap = request.getHeader(ADMIN_HEADER);
        if (!ADMIN_VALUE.equals(ldap)) {
            throw new CoreException(ErrorType.ADMIN_UNAUTHORIZED);
        }
        return true;
    }
}
