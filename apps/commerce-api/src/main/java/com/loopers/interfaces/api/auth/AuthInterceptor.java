package com.loopers.interfaces.api.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.loopers.domain.user.UserService;
import com.loopers.interfaces.api.config.WebMvcConfig;
import com.loopers.support.error.CoreException;

import lombok.RequiredArgsConstructor;

/**
 * 인증 헤더 기반 사용자 인증 인터셉터.
 *
 * <p>요청 헤더에서 로그인 ID와 비밀번호를 추출하여 사용자를 인증한다.
 * 인증 성공 시 사용자 ID를 request attribute에 저장하여 컨트롤러에서 사용할 수 있도록 한다.</p>
 *
 * <p>인증 헤더:</p>
 * <ul>
 *   <li>{@code X-Loopers-LoginId}: 로그인 ID</li>
 *   <li>{@code X-Loopers-LoginPw}: 비밀번호</li>
 * </ul>
 *
 * <p>인증 실패 시 401 Unauthorized를 반환한다.</p>
 *
 * @see WebMvcConfig 인터셉터 등록 설정
 */
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private static final String HEADER_LOGIN_ID = "X-Loopers-LoginId";
    private static final String HEADER_LOGIN_PW = "X-Loopers-LoginPw";
    private static final String USER_ID_ATTRIBUTE = "userId";

    private final UserService userService;

    /**
     * 요청 처리 전 인증을 수행한다.
     *
     * <p>인증 성공 시 request attribute에 {@code userId}(Long)를 저장한다.</p>
     *
     * @param request  HTTP 요청
     * @param response HTTP 응답
     * @param handler  핸들러 객체
     * @return 항상 true (인증 실패 시 예외가 발생한다)
     * @throws CoreException 로그인 ID 또는 비밀번호가 유효하지 않은 경우
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String loginId = request.getHeader(HEADER_LOGIN_ID);
        String loginPw = request.getHeader(HEADER_LOGIN_PW);
        Long loginUserId = userService.login(loginId, loginPw);
        request.setAttribute(USER_ID_ATTRIBUTE, loginUserId);
        return true;
    }
}
