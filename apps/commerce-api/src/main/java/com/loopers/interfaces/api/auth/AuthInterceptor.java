package com.loopers.interfaces.api.auth;

import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
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
 * <p>인증이 필수가 아닌 경로({@link #addOptionalPattern})에서는
 * 인증 헤더가 없거나 유효하지 않아도 에러 없이 통과한다.</p>
 *
 * <p>인증 헤더:</p>
 * <ul>
 *   <li>{@code X-Loopers-LoginId}: 로그인 ID</li>
 *   <li>{@code X-Loopers-LoginPw}: 비밀번호</li>
 * </ul>
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
    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final List<String> optionalPatterns = new ArrayList<>();

    /**
     * 인증이 필수가 아닌 경로 패턴을 등록한다.
     *
     * <p>등록된 패턴에 매칭되는 요청은 인증 헤더가 없거나 인증에 실패해도
     * 예외 없이 통과한다. 인증 헤더가 유효하면 사용자 ID를 저장한다.</p>
     *
     * @param pattern Ant 스타일 경로 패턴 (예: {@code /api/v1/products/*})
     */
    public void addOptionalPattern(String pattern) {
        optionalPatterns.add(pattern);
    }

    /**
     * 요청 처리 전 인증을 수행한다.
     *
     * <p>인증 성공 시 request attribute에 {@code userId}(Long)를 저장한다.
     * 인증이 필수가 아닌 경로에서는 인증 실패 시 에러 없이 통과한다.</p>
     *
     * @param request  HTTP 요청
     * @param response HTTP 응답
     * @param handler  핸들러 객체
     * @return 항상 true (인증 필수 경로에서 인증 실패 시 예외가 발생한다)
     * @throws CoreException 인증 필수 경로에서 로그인 ID 또는 비밀번호가 유효하지 않은 경우
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        boolean optional = isOptionalPath(request.getRequestURI());
        authenticate(request, optional);
        return true;
    }

    private void authenticate(HttpServletRequest request, boolean optional) {
        if (optional) {
            authenticateIfPossible(request);
        } else {
            authenticateRequired(request);
        }
    }

    private void authenticateIfPossible(HttpServletRequest request) {
        String loginId = request.getHeader(HEADER_LOGIN_ID);
        String loginPw = request.getHeader(HEADER_LOGIN_PW);

        if (loginId == null || loginPw == null) {
            return;
        }

        try {
            Long loginUserId = userService.login(loginId, loginPw);
            request.setAttribute(USER_ID_ATTRIBUTE, loginUserId);
        } catch (CoreException e) {
            // 인증 실패 시 optional 경로에서는 에러 없이 통과한다.
        }
    }

    private void authenticateRequired(HttpServletRequest request) {
        Long loginUserId = userService.login(
                request.getHeader(HEADER_LOGIN_ID),
                request.getHeader(HEADER_LOGIN_PW)
        );
        request.setAttribute(USER_ID_ATTRIBUTE, loginUserId);
    }

    private boolean isOptionalPath(String requestUri) {
        return optionalPatterns.stream()
                .anyMatch(pattern -> pathMatcher.match(pattern, requestUri));
    }
}
