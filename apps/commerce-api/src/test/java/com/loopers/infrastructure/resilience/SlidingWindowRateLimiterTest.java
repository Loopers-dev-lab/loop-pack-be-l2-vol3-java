package com.loopers.infrastructure.resilience;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SlidingWindowRateLimiterTest {

    @Nested
    @DisplayName("Sliding Window Rate Limiter")
    class RateLimiting {

        @DisplayName("U2-1: limit 이내 요청은 전부 허용된다")
        @Test
        void withinLimit_allAllowed() {
            var rateLimiter = new SlidingWindowRateLimiter(50, 1000);

            int accepted = 0;
            for (int i = 0; i < 50; i++) {
                if (rateLimiter.tryAcquire()) {
                    accepted++;
                }
            }

            assertThat(accepted).isEqualTo(50);
        }

        @DisplayName("U2-2: limit 초과 요청은 거부된다")
        @Test
        void exceedLimit_rejected() {
            var rateLimiter = new SlidingWindowRateLimiter(50, 1000);

            for (int i = 0; i < 50; i++) {
                rateLimiter.tryAcquire();
            }

            assertThat(rateLimiter.tryAcquire()).isFalse();
        }

        @DisplayName("U2-3: 윈도우 경계에서 이전 윈도우 가중치가 적용된다 (Boundary Burst 방지)")
        @Test
        void windowBoundary_prevWindowWeightApplied() throws InterruptedException {
            var rateLimiter = new SlidingWindowRateLimiter(10, 200);

            // 현재 윈도우에서 10건 소진
            for (int i = 0; i < 10; i++) {
                assertThat(rateLimiter.tryAcquire()).isTrue();
            }
            assertThat(rateLimiter.tryAcquire()).isFalse();

            // 윈도우 경계를 넘어감 (새 윈도우 시작 직후)
            Thread.sleep(220);

            // Sliding Window: 이전 윈도우 10건이 가중치로 반영되어
            // Fixed Window와 달리 10건 전부 허용되지 않는다 (Boundary Burst 방지)
            int acceptedInNewWindow = 0;
            for (int i = 0; i < 10; i++) {
                if (rateLimiter.tryAcquire()) {
                    acceptedInNewWindow++;
                }
            }

            // 핵심: 이전 윈도우 가중치 때문에 새 윈도우에서 10건 미만만 허용된다
            // (Fixed Window라면 10건 전부 허용되어 Boundary Burst 발생)
            assertThat(acceptedInNewWindow)
                .as("Sliding Window는 이전 윈도우 가중치로 Boundary Burst를 방지한다")
                .isGreaterThanOrEqualTo(1)
                .isLessThan(10);
        }
    }
}
