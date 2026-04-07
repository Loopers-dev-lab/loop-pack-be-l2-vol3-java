package com.loopers.domain.queue;

/**
 * 동적 폴링 힌트. 로드맵 §2.3 구간과 동일한 값을 반환한다.
 */
public final class QueuePollHintPolicy {

    private QueuePollHintPolicy() {
    }

    public static long suggestedPollIntervalMs(long position) {
        if (position <= 100) {
            return 1000L;
        }
        if (position <= 1000) {
            return 3000L;
        }
        if (position <= 10000) {
            return 5000L;
        }
        return 10000L;
    }

    public static long retryAfterSeconds(long position) {
        if (position <= 100) {
            return 1L;
        }
        if (position <= 1000) {
            return 3L;
        }
        if (position <= 10000) {
            return 5L;
        }
        return 10L;
    }
}
