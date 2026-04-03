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

    /** YAML 오설정 시 비현실적 대기·오버플로를 막기 위한 상한 (초당 입장 목표 TPS). */
    public static final double MAX_THROUGHPUT_TPS = 1_000_000.0;

    public QueuePositionProperties {
        if (rateLimit == null) {
            rateLimit = new RateLimit(false, 5, 1);
        }
        if (!(throughputTps > 0)
                || throughputTps > MAX_THROUGHPUT_TPS
                || !Double.isFinite(throughputTps)) {
            throw new IllegalArgumentException(
                    "queue.position.throughput-tps must be finite and in (0, "
                            + (long) MAX_THROUGHPUT_TPS
                            + "], got: "
                            + throughputTps);
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
