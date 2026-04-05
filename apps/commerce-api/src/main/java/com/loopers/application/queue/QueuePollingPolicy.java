package com.loopers.application.queue;

/**
 * 대기열 순번 기반 클라이언트 폴링 주기 정책.
 *
 * <p>순번 구간별로 권장 폴링 주기(ms)를 반환한다.</p>
 * <ul>
 *   <li>1–60: 1,000ms (IMMINENT)</li>
 *   <li>61–300: 3,000ms (SOON)</li>
 *   <li>301–3,000: 10,000ms (MODERATE)</li>
 *   <li>3,001+: 20,000ms (FAR)</li>
 * </ul>
 */
public final class QueuePollingPolicy {

    static final long TIER_IMMINENT_MAX = 60;
    static final long TIER_SOON_MAX = 300;
    static final long TIER_MODERATE_MAX = 3_000;

    static final long POLLING_IMMINENT_MS = 1_000;
    static final long POLLING_SOON_MS = 3_000;
    static final long POLLING_MODERATE_MS = 10_000;
    static final long POLLING_FAR_MS = 20_000;

    private QueuePollingPolicy() {
    }

    /**
     * 대기 순번에 따른 권장 폴링 주기(ms)를 반환한다.
     *
     * @param position 1-based 대기 순번
     * @return 권장 폴링 주기(밀리초)
     */
    public static long calculateIntervalMs(long position) {
        if (position <= TIER_IMMINENT_MAX) {
            return POLLING_IMMINENT_MS;
        }
        if (position <= TIER_SOON_MAX) {
            return POLLING_SOON_MS;
        }
        if (position <= TIER_MODERATE_MAX) {
            return POLLING_MODERATE_MS;
        }
        return POLLING_FAR_MS;
    }
}
