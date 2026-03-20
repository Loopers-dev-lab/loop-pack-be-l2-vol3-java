package com.loopers.interfaces.api;

import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserService;
import com.loopers.support.error.CoreException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 고객 API 인증 처리를 담당하는 인터셉터.
 *
 * <p>{@code X-Loopers-LoginId}와 {@code X-Loopers-LoginPw} 헤더 값을 검증하여 고객 인증을 수행한다.
 * 인증 성공 시 {@link UserModel}을 request attribute에 저장한다.
 * 인증에 실패하면 {@link CoreException}을 발생시킨다.</p>
 */
@Component
@RequiredArgsConstructor
public class CustomerAuthInterceptor implements HandlerInterceptor {

    private static final String LOGIN_ID_HEADER = "X-Loopers-LoginId";
    private static final String LOGIN_PW_HEADER = "X-Loopers-LoginPw";

    /**
     * Request attribute key for the authenticated user.
     */
    public static final String AUTH_USER_ATTR = "authenticatedUser";

    private final UserService userService;

    /**
     * 요청을 처리하기 전에 고객 인증을 수행한다.
     *
     * <p>인증 헤더가 없으면 통과시키고, 헤더가 있으면 인증을 수행한다.
     * 인증 성공 시 UserModel을 request attribute에 저장한다.</p>
     *
     * @param request  HTTP 요청 객체
     * @param response HTTP 응답 객체
     * @param handler  요청을 처리할 핸들러 객체
     * @return 항상 {@code true} (인증 실패 시 예외 발생)
     * @throws CoreException 인증 실패 시 예외 발생
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String loginId = request.getHeader(LOGIN_ID_HEADER);
        String loginPw = request.getHeader(LOGIN_PW_HEADER);

        if (loginId == null || loginPw == null) {
            return true;  // 헤더 없으면 통과 (인증 불필요 API용)
        }

        UserModel user = userService.authenticate(loginId, loginPw);
        request.setAttribute(AUTH_USER_ATTR, user);
        return true;
    }
}
