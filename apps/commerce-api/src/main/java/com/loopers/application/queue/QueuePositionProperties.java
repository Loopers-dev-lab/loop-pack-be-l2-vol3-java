package com.loopers.application.queue;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code queue.position.*} — 순번 조회 시 예상 대기 시간(TPS) 및 선택적 요청 속도 제한.
 */
@ConfigurationProperties(prefix = "queue.position")
public record QueuePositionProperties(
        double throughputTps,
        RateLimit rateLimit
) {
    public QueuePositionProperties {
        if (rateLimit == null) {
            rateLimit = new RateLimit(false, 5, 1);
        }
    }

    public record RateLimit(boolean enabled, int maxRequestsPerSecond, int windowSeconds) {
        public RateLimit {
            if (maxRequestsPerSecond <= 0) {
                maxRequestsPerSecond = 5;
            }
            if (windowSeconds <= 0) {
                windowSeconds = 1;
            }
        }
    }
}
