package com.loopers.interfaces.auth;

import com.loopers.application.member.MemberAuthenticationService;
import com.loopers.application.member.command.AuthenticateCommand;
import com.loopers.domain.member.vo.MemberId;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class MemberAuthenticationFilter extends OncePerRequestFilter {

    static final String HEADER_LOGIN_ID = "X-Loopers-LoginId";
    static final String HEADER_LOGIN_PW = "X-Loopers-LoginPw";

    private final MemberAuthenticationService memberAuthenticationService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        final String loginId = request.getHeader(HEADER_LOGIN_ID);
        final String password = request.getHeader(HEADER_LOGIN_PW);

        if (loginId != null && !loginId.isBlank() && password != null && !password.isBlank()) {
            final var member = memberAuthenticationService.authenticate(
                    new AuthenticateCommand(new MemberId(loginId), password)
            );
            final var authentication = new UsernamePasswordAuthenticationToken(
                    new AuthenticatedMemberPrincipal(member),
                    null,
                    List.of()
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
