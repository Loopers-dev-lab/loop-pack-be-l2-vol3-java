package com.loopers.interfaces.filter;

import com.loopers.application.queue.RateLimitModeEvaluator;
import com.loopers.config.RateLimitProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@RequiredArgsConstructor
public class DynamicRateLimitFilter extends OncePerRequestFilter {

    private final RateLimitModeEvaluator rateLimitModeEvaluator;
    private final RateLimitProperties rateLimitProperties;

    private final ConcurrentHashMap<String, WindowCounter> counters = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!rateLimitProperties.enabled() || !isQueueEndpoint(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!rateLimitModeEvaluator.isActive()) {
            filterChain.doFilter(request, response);
            return;
        }

        String userId = request.getHeader("X-USER-ID");
        if (userId == null || userId.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        WindowCounter counter = counters.computeIfAbsent(userId, k -> new WindowCounter());
        if (counter.incrementAndCheck(rateLimitProperties.perUserMaxRequests(), rateLimitProperties.perUserWindowSeconds())) {
            filterChain.doFilter(request, response);
        } else {
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write("{\"meta\":{\"result\":\"FAIL\",\"errorCode\":\"TOO_MANY_REQUESTS\",\"message\":\"요청이 너무 많습니다. 잠시 후 다시 시도해주세요.\"}}");
        }
    }

    private boolean isQueueEndpoint(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/api/v1/queue/");
    }

    static class WindowCounter {
        private final AtomicInteger count = new AtomicInteger(0);
        private volatile long windowStartMs = System.currentTimeMillis();

        boolean incrementAndCheck(int maxRequests, int windowSeconds) {
            long now = System.currentTimeMillis();
            if (now - windowStartMs > windowSeconds * 1000L) {
                synchronized (this) {
                    if (now - windowStartMs > windowSeconds * 1000L) {
                        count.set(0);
                        windowStartMs = now;
                    }
                }
            }
            return count.incrementAndGet() <= maxRequests;
        }
    }
}
