package com.loopers.domain.ranking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class RankingKeyTest {

    @Test
    @DisplayName("LocalDate로 ranking:all:{yyyyMMdd} 키를 만든다.")
    void dailyAll_shouldFormatKey() {
        assertThat(RankingKey.dailyAll(LocalDate.of(2026, 3, 26))).isEqualTo("ranking:all:20260326");
    }
}
