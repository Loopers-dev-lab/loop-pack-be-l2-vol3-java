package com.loopers.interfaces.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.member.Member;
import com.loopers.domain.member.MemberRepository;
import com.loopers.domain.member.PasswordEncoder;
import com.loopers.interfaces.api.ApiResponse;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;

@Component
@Order(1)
@RequiredArgsConstructor
public class AuthenticationFilter implements Filter {

    private static final String LOGIN_ID_HEADER = "X-Loopers-LoginId";
    private static final String LOGIN_PW_HEADER = "X-Loopers-LoginPw";
    private static final String ADMIN_LDAP_HEADER = "X-Loopers-Ldap";

    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/api/v1/members/signup",
            "/api/v1/examples"
    );

    private static final Set<String> OPTIONAL_AUTH_PATHS = Set.of(
            "/api/v1/products"
    );

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String path = httpRequest.getRequestURI();

        if (isPublicPath(path)) {
            chain.doFilter(request, response);
            return;
        }

        if (!path.startsWith("/api/")) {
            chain.doFilter(request, response);
            return;
        }

        if (path.startsWith("/api/admin/")) {
            handleAdminAuthentication(httpRequest, httpResponse, chain);
            return;
        }

        if (isOptionalAuthPath(path)) {
            handleOptionalUserAuthentication(httpRequest, httpResponse, chain);
            return;
        }

        handleUserAuthentication(httpRequest, httpResponse, chain);
    }

    private boolean isOptionalAuthPath(String path) {
        return OPTIONAL_AUTH_PATHS.stream().anyMatch(optionalPath -> isPathMatch(path, optionalPath));
    }

    private void handleOptionalUserAuthentication(HttpServletRequest httpRequest, HttpServletResponse httpResponse,
                                                   FilterChain chain) throws IOException, ServletException {
        String loginId = httpRequest.getHeader(LOGIN_ID_HEADER);
        String loginPw = httpRequest.getHeader(LOGIN_PW_HEADER);

        if (loginId == null || loginPw == null) {
            chain.doFilter(httpRequest, httpResponse);
            return;
        }

        Optional<Member> memberOpt = memberRepository.findByMemberIdValue(loginId);
        if (memberOpt.isEmpty()) {
            chain.doFilter(httpRequest, httpResponse);
            return;
        }

        Member member = memberOpt.get();
        if (!member.getPassword().matches(loginPw, passwordEncoder)) {
            chain.doFilter(httpRequest, httpResponse);
            return;
        }

        httpRequest.setAttribute("authenticatedMember", member);
        chain.doFilter(httpRequest, httpResponse);
    }

    private void handleAdminAuthentication(HttpServletRequest httpRequest, HttpServletResponse httpResponse,
                                           FilterChain chain) throws IOException, ServletException {
        String ldap = httpRequest.getHeader(ADMIN_LDAP_HEADER);

        if (ldap == null || ldap.isBlank()) {
            sendUnauthorizedResponse(httpResponse, "관리자 인증 정보가 필요합니다.");
            return;
        }

        httpRequest.setAttribute("authenticatedAdmin", ldap);
        chain.doFilter(httpRequest, httpResponse);
    }

    private void handleUserAuthentication(HttpServletRequest httpRequest, HttpServletResponse httpResponse,
                                          FilterChain chain) throws IOException, ServletException {
        String loginId = httpRequest.getHeader(LOGIN_ID_HEADER);
        String loginPw = httpRequest.getHeader(LOGIN_PW_HEADER);

        if (loginId == null || loginPw == null) {
            sendUnauthorizedResponse(httpResponse, "인증 정보가 필요합니다.");
            return;
        }

        Optional<Member> memberOpt = memberRepository.findByMemberIdValue(loginId);
        if (memberOpt.isEmpty()) {
            sendUnauthorizedResponse(httpResponse, "인증에 실패했습니다.");
            return;
        }

        Member member = memberOpt.get();
        if (!member.getPassword().matches(loginPw, passwordEncoder)) {
            sendUnauthorizedResponse(httpResponse, "인증에 실패했습니다.");
            return;
        }

        httpRequest.setAttribute("authenticatedMember", member);
        chain.doFilter(httpRequest, httpResponse);
    }

    private boolean isPublicPath(String path) {
        return PUBLIC_PATHS.stream().anyMatch(publicPath -> isPathMatch(path, publicPath));
    }

    private boolean isPathMatch(String requestPath, String publicPath) {
        if (requestPath.equals(publicPath)) {
            return true;
        }
        return requestPath.startsWith(publicPath + "/");
    }

    private void sendUnauthorizedResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        ApiResponse<Object> apiResponse = ApiResponse.fail(HttpStatus.UNAUTHORIZED.getReasonPhrase(), message);
        response.getWriter().write(objectMapper.writeValueAsString(apiResponse));
    }
}
