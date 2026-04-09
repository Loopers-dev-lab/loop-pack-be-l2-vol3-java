package com.loopers.domain.ranking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RankingKeyGenerator 단위 테스트")
class RankingKeyGeneratorTest {

    @Nested
    @DisplayName("dailyKey()")
    class DailyKey {

        @Test
        @DisplayName("주어진 날짜로 'rank:all:{yyyyMMdd}' 형식의 키를 생성한다")
        void generatesKeyWithExpectedFormat() {
            LocalDate date = LocalDate.of(2026, 4, 5);

            String key = RankingKeyGenerator.dailyKey(date);

            assertThat(key).isEqualTo("rank:all:20260405");
        }

        @Test
        @DisplayName("월/일이 한 자리일 때 0으로 패딩된다")
        void padsSingleDigitMonthAndDay() {
            LocalDate date = LocalDate.of(2026, 1, 7);

            String key = RankingKeyGenerator.dailyKey(date);

            assertThat(key).isEqualTo("rank:all:20260107");
        }
    }
}
