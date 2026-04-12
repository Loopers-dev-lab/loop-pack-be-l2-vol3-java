package com.loopers.domain.ranking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("RankingKey 포맷 테스트 (api)")
class RankingKeyTest {

    @Nested
    @DisplayName("daily(date)")
    class Daily {

        @Test
        @DisplayName("yyyyMMdd 포맷으로 prefix 와 결합된다 (streamer/api 간 회귀 방지)")
        void format() {
            // given
            LocalDate date = LocalDate.of(2026, 4, 9);

            // when
            String key = RankingKey.daily(date);

            // then
            assertThat(key).isEqualTo("ranking:all:20260409");
        }

        @Test
        @DisplayName("월/일이 한 자리여도 2자리로 zero-padding 된다")
        void zeroPadding() {
            // given
            LocalDate date = LocalDate.of(2026, 1, 3);

            // when
            String key = RankingKey.daily(date);

            // then
            assertThat(key).isEqualTo("ranking:all:20260103");
        }

        @Test
        @DisplayName("null 입력은 IllegalArgumentException 을 던진다")
        void nullDate() {
            // expect
            assertThatThrownBy(() -> RankingKey.daily(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("상수 prefix 는 'ranking:all:' 이다 (streamer/api 간 회귀 방지)")
        void prefixContract() {
            assertThat(RankingKey.DAILY_PREFIX).isEqualTo("ranking:all:");
        }
    }
}
