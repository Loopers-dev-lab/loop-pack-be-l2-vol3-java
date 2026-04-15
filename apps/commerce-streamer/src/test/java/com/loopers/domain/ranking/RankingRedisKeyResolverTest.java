package com.loopers.domain.ranking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class RankingRedisKeyResolverTest {

    @Test
    @DisplayName("occurredAt이 한국 날짜 20260408이면 ranking:all:20260408 키를 반환한다.")
    void resolveDailyAllKey_whenOccurredAtInSeoulDate_shouldReturnExpectedKey() {
        // given
        RankingRedisKeyResolver resolver = new RankingRedisKeyResolver();
        Instant occurredAt = Instant.parse("2026-04-07T15:30:00Z"); // Asia/Seoul: 2026-04-08 00:30

        // when
        String key = resolver.resolveDailyAllKey(occurredAt);

        // then
        assertThat(key).isEqualTo("ranking:all:20260408");
    }

    @Test
    @DisplayName("자정 경계 시각은 occurredAt 기준으로 각각 다른 날짜 키를 반환한다.")
    void resolveDailyAllKey_whenCrossMidnightBoundary_shouldSplitDateKeys() {
        // given
        RankingRedisKeyResolver resolver = new RankingRedisKeyResolver();
        Instant beforeMidnight = Instant.parse("2026-04-07T14:59:59.999Z"); // KST 23:59:59.999
        Instant afterMidnight = Instant.parse("2026-04-07T15:00:00Z"); // KST 00:00:00

        // when
        String beforeKey = resolver.resolveDailyAllKey(beforeMidnight);
        String afterKey = resolver.resolveDailyAllKey(afterMidnight);

        // then
        assertThat(beforeKey).isEqualTo("ranking:all:20260407");
        assertThat(afterKey).isEqualTo("ranking:all:20260408");
    }
}
