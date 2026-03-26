package com.loopers.infrastructure.resilience;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Sliding Window Counter 기반 Rate Limiter.
 *
 * <p>Fixed Window의 경계 돌파(Boundary Burst) 문제 해결:
 * Fixed Window는 윈도우 경계에서 최대 2배(100건) burst 가능.
 * Sliding Window Counter는 어떤 1초 구간에서도 정확히 limit 이하 보장.</p>
 *
 * <p>계산식: prevWeight × prevCount + currCount < limit</p>
 * <p>prevWeight = max(0, 1 - (now - currentWindowStart) / windowSizeMs)</p>
 *
 * @see <a href="05-payment-resilience.md §7.4">Rate Limiter 설계</a>
 */
public class SlidingWindowRateLimiter {

    private final int limit;
    private final long windowSizeMs;

    private final AtomicLong prevWindowStart = new AtomicLong(0);
    private final AtomicInteger prevWindowCount = new AtomicInteger(0);
    private final AtomicLong currWindowStart = new AtomicLong(0);
    private final AtomicInteger currWindowCount = new AtomicInteger(0);

    public SlidingWindowRateLimiter(int limit, long windowSizeMs) {
        this.limit = limit;
        this.windowSizeMs = windowSizeMs;
    }

    /**
     * 요청 허용 여부를 판단한다.
     *
     * @return true: 허용, false: 거부 (429 Too Many Requests)
     */
    public synchronized boolean tryAcquire() {
        long now = System.currentTimeMillis();
        long currentWindow = now / windowSizeMs * windowSizeMs;

        if (currentWindow != currWindowStart.get()) {
            prevWindowCount.set(currWindowCount.get());
            prevWindowStart.set(currWindowStart.get());
            currWindowCount.set(0);
            currWindowStart.set(currentWindow);
        }

        double elapsed = (double) (now - currentWindow) / windowSizeMs;
        double prevWeight = Math.max(0, 1.0 - elapsed);
        double weightedCount = prevWeight * prevWindowCount.get() + currWindowCount.get();

        if (weightedCount < limit) {
            currWindowCount.incrementAndGet();
            return true;
        }
        return false;
    }

    /**
     * 테스트용 — 현재 가중 카운트를 반환한다.
     */
    double getWeightedCount() {
        long now = System.currentTimeMillis();
        long currentWindow = now / windowSizeMs * windowSizeMs;
        double elapsed = (double) (now - currentWindow) / windowSizeMs;
        double prevWeight = Math.max(0, 1.0 - elapsed);
        return prevWeight * prevWindowCount.get() + currWindowCount.get();
    }
}
