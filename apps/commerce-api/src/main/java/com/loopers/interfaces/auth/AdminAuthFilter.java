package com.loopers.interfaces.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.error.ErrorType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
@Order(1)
public class AdminAuthFilter extends OncePerRequestFilter {

    private static final String HEADER_LDAP = "X-Loopers-Ldap";
    private static final String LDAP_VALUE = "loopers.admin";
    private static final String ADMIN_PATH_PREFIX = "/api-admin/";

    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!requiresAuth(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }

        String ldapHeader = request.getHeader(HEADER_LDAP);

        if (ldapHeader == null || !LDAP_VALUE.equals(ldapHeader)) {
            writeUnauthorizedResponse(response, ErrorType.UNAUTHORIZED.getMessage());
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean requiresAuth(String uri) {
        return uri.startsWith(ADMIN_PATH_PREFIX);
    }

    private void writeUnauthorizedResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        ApiResponse<Object> apiResponse = ApiResponse.fail(ErrorType.UNAUTHORIZED.getCode(), message);
        response.getWriter().write(objectMapper.writeValueAsString(apiResponse));
    }
}
