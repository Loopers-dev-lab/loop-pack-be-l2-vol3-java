package com.loopers.interfaces.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.user.AuthenticationService;
import com.loopers.domain.user.UserModel;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class AuthFilter extends OncePerRequestFilter {

    private static final String HEADER_LOGIN_ID = "X-Loopers-LoginId";
    private static final String HEADER_LOGIN_PW = "X-Loopers-LoginPw";
    private static final Set<String> AUTH_REQUIRED_URLS = Set.of(
        "/api/v1/users/me",
        "/api/v1/users/password",
        "/api/v1/users/me/likes",
        "/api/v1/users/me/coupons"
    );
    private static final String AUTH_REQUIRED_SUFFIX = "/likes";
    private static final String AUTH_REQUIRED_PREFIX_ORDERS = "/api/v1/orders";
    private static final String AUTH_REQUIRED_PREFIX_COUPONS = "/api/v1/coupons/";

    private final AuthenticationService authenticationService;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!requiresAuth(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }

        String loginId = request.getHeader(HEADER_LOGIN_ID);
        String password = request.getHeader(HEADER_LOGIN_PW);

        if (loginId == null || loginId.isBlank() || password == null || password.isBlank()) {
            writeUnauthorizedResponse(response, ErrorType.UNAUTHORIZED.getMessage());
            return;
        }

        try {
            UserModel user = authenticationService.authenticate(loginId, password);
            request.setAttribute("loginUser",
                    new LoginUser(user.getId(), user.getLoginId(), user.getName()));
            filterChain.doFilter(request, response);
        } catch (CoreException e) {
            writeUnauthorizedResponse(response,
                e.getCustomMessage() != null ? e.getCustomMessage() : e.getErrorCode().getMessage());
        }
    }

    private boolean requiresAuth(String uri) {
        return AUTH_REQUIRED_URLS.contains(uri)
            || (uri.startsWith("/api/v1/products/") && uri.endsWith(AUTH_REQUIRED_SUFFIX))
            || uri.startsWith(AUTH_REQUIRED_PREFIX_ORDERS)
            || uri.startsWith(AUTH_REQUIRED_PREFIX_COUPONS);
    }

    private void writeUnauthorizedResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        ApiResponse<Object> apiResponse = ApiResponse.fail(ErrorType.UNAUTHORIZED.getCode(), message);
        response.getWriter().write(objectMapper.writeValueAsString(apiResponse));
    }
}
