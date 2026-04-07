package com.loopers.domain.ranking;

import java.time.Duration;

/**
 * 랭킹 Redis 키 TTL 정책을 제공한다.
 * <p>
 * 일간 키 TTL은 2일을 사용한다.
 */
public class RankingTtlPolicy {

    private static final Duration DAILY_KEY_TTL = Duration.ofDays(2);

    /**
     * 일간 랭킹 키 TTL을 반환한다.
     *
     * @return 2일 TTL
     */
    public Duration dailyKeyTtl() {
        return DAILY_KEY_TTL;
    }
}
