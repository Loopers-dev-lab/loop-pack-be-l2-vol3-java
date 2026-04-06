package com.loopers.interfaces.interceptor;

import com.loopers.domain.member.model.Member;
import com.loopers.domain.member.service.MemberService;
import com.loopers.domain.queue.service.EntryTokenService;
import com.loopers.domain.queue.service.QueueFeatureFlag;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@RequiredArgsConstructor
@Component
public class EntryTokenInterceptor implements HandlerInterceptor {

    private final EntryTokenService entryTokenService;
    private final MemberService memberService;
    private final QueueFeatureFlag queueFeatureFlag;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!queueFeatureFlag.isEnabled()) return true;

        String loginId = request.getHeader("X-Loopers-LoginId");
        if (loginId == null) return true;

        String password = request.getHeader("X-Loopers-LoginPw");
        Member member = memberService.findMember(loginId, password);

        if (!entryTokenService.validateToken(member.getId())) {
            throw new CoreException(ErrorType.FORBIDDEN, "입장 토큰이 없습니다. 대기열에 먼저 진입해주세요.");
        }

        long remainingMs = entryTokenService.getRemainingDelayMs(member.getId());
        if (remainingMs > 0) {
            try {
                Thread.sleep(remainingMs);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }

        entryTokenService.refreshTtl(member.getId());
        return true;
    }
}
