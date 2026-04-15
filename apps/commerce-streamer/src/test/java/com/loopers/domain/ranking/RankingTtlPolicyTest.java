package com.loopers.domain.ranking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class RankingTtlPolicyTest {

    @Test
    @DisplayName("랭킹 일간 키 TTL은 2일이다.")
    void dailyKeyTtl_whenCalled_shouldReturnTwoDays() {
        // given
        RankingTtlPolicy policy = new RankingTtlPolicy();

        // when
        Duration ttl = policy.dailyKeyTtl();

        // then
        assertThat(ttl).isEqualTo(Duration.ofDays(2));
    }
}
