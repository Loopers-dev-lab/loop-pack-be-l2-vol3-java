package com.loopers.interfaces.api.admin;

import com.loopers.application.admin.AdminPrincipal;
import com.loopers.interfaces.api.LoopersHeaders;
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
public class AdminUserArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(AdminUser.class)
            && parameter.getParameterType().equals(AdminPrincipal.class);
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

        String adminId = request.getHeader(LoopersHeaders.X_ADMIN_ID);
        String adminToken = request.getHeader(LoopersHeaders.X_ADMIN_TOKEN);

        if (adminId == null || adminId.isBlank() || adminToken == null || adminToken.isBlank()) {
            throw new CoreException(ErrorType.UNAUTHORIZED, ErrorMessage.Auth.ADMIN_AUTH_HEADER_MISSING);
        }

        // TODO: 실제 LDAP 서버 연동 시 이 부분을 LDAP 인증으로 교체
        // 현재는 간단한 토큰 검증 (예: adminId + "_token" 형태)
        if (!isValidAdminToken(adminId, adminToken)) {
            throw new CoreException(ErrorType.UNAUTHORIZED, ErrorMessage.Auth.ADMIN_AUTH_FAILED);
        }

        // TODO: LDAP에서 관리자 정보(이름, 부서 등) 조회
        return AdminPrincipal.of(adminId, "관리자", "운영팀");
    }

    /**
     * 관리자 토큰 검증
     * TODO: 실제 LDAP 연동 시 LDAP 서버에서 인증
     */
    private boolean isValidAdminToken(String adminId, String adminToken) {
        // 임시 검증 로직 (실제 구현 시 LDAP 또는 별도 인증 서버 연동)
        return adminToken.equals(adminId + "_token");
    }
}
