package com.loopers.interfaces.api.interceptor;

import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.loopers.domain.user.LoginId;
import com.loopers.domain.user.PasswordEncoder;
import com.loopers.domain.user.User;
import com.loopers.domain.user.UserRepository;
import com.loopers.interfaces.api.config.WebMvcConfig;

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
    public static final String USER_ID_ATTRIBUTE = "userId";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * 요청 처리 전 인증을 수행한다.
     *
     * <p>인증 성공 시 request attribute에 {@code userId}(Long)를 저장한다.</p>
     *
     * @param request  HTTP 요청
     * @param response HTTP 응답
     * @param handler  핸들러 객체
     * @return 인증 성공 시 true, 실패 시 false
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String loginId = request.getHeader(HEADER_LOGIN_ID);
        String loginPw = request.getHeader(HEADER_LOGIN_PW);

        if (loginId == null || loginPw == null) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            return false;
        }

        Optional<User> userOptional = userRepository.findByLoginId(new LoginId(loginId));
        if (userOptional.isEmpty()) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            return false;
        }

        User user = userOptional.get();
        if (!user.matchesPassword(loginPw, passwordEncoder)) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            return false;
        }

        request.setAttribute(USER_ID_ATTRIBUTE, user.getId());
        return true;
    }
}
