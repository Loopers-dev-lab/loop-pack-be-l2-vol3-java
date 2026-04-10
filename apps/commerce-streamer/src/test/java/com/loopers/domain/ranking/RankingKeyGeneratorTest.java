package com.loopers.domain.ranking;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class RankingKeyGeneratorTest {

    @Test
    void generates_daily_key() {
        String key = RankingKeyGenerator.dailyKey(LocalDate.of(2026, 4, 10));
        assertThat(key).isEqualTo("ranking:all:20260410");
    }

    @Test
    void generates_today_key() {
        String key = RankingKeyGenerator.todayKey();
        assertThat(key).startsWith("ranking:all:");
        assertThat(key).hasSize("ranking:all:20260410".length());
    }
}
