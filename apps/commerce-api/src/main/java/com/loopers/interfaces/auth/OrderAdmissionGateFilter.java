package com.loopers.interfaces.auth;

import com.loopers.application.order.queue.OrderAdmissionApplicationService;
import com.loopers.application.order.queue.OrderQueueProperties;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class OrderAdmissionGateFilter extends OncePerRequestFilter {

    private final OrderQueueProperties orderQueueProperties;
    private final OrderAdmissionApplicationService orderAdmissionApplicationService;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!isOrderCreateRequest(request) || !orderQueueProperties.enabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof UsernamePasswordAuthenticationToken token)
                || !(token.getPrincipal() instanceof AuthenticatedMemberPrincipal principal)) {
            writeFailure(response, ErrorType.UNAUTHORIZED, ErrorType.UNAUTHORIZED.getMessage());
            return;
        }

        try {
            orderAdmissionApplicationService.validateOrderEntry(principal.member().id().value());
            filterChain.doFilter(request, response);
            if (response.getStatus() == HttpServletResponse.SC_CREATED) {
                orderAdmissionApplicationService.completeAdmission(principal.member().id().value());
            }
        } catch (CoreException e) {
            writeFailure(response, e.getErrorType(), e.getCustomMessage() != null ? e.getCustomMessage() : e.getMessage());
        }
    }

    boolean isOrderCreateRequest(HttpServletRequest request) {
        return "POST".equalsIgnoreCase(request.getMethod()) && "/api/v1/orders".equals(request.getRequestURI());
    }

    void writeFailure(HttpServletResponse response, ErrorType errorType, String message) throws IOException {
        response.setStatus(errorType.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), ApiResponse.fail(errorType.getCode(), message));
    }
}
