package com.loopers.domain.ranking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("RankingWeightProperties 단위 테스트")
class RankingWeightPropertiesTest {

    @Nested
    @DisplayName("생성 시 검증")
    class Validation {

        @Test
        @DisplayName("합이 1.0인 가중치는 정상 생성된다")
        void createsWhenSumIsOne() {
            RankingWeightProperties properties = new RankingWeightProperties(0.1, 0.2, 0.7);

            assertThat(properties.view()).isEqualTo(0.1);
            assertThat(properties.like()).isEqualTo(0.2);
            assertThat(properties.order()).isEqualTo(0.7);
        }

        @Test
        @DisplayName("합이 1.0에서 허용 오차(0.0001) 이내면 정상 생성된다")
        void createsWhenSumWithinTolerance() {
            RankingWeightProperties properties = new RankingWeightProperties(0.10001, 0.19999, 0.7);

            assertThat(properties).isNotNull();
        }

        @Test
        @DisplayName("합이 1.0이 아니면 IllegalArgumentException을 던진다")
        void throwsWhenSumNotOne() {
            assertThatThrownBy(() -> new RankingWeightProperties(0.1, 0.2, 0.5))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("sum must be 1.0");
        }

        @Test
        @DisplayName("음수 가중치는 IllegalArgumentException을 던진다")
        void throwsWhenNegativeWeight() {
            assertThatThrownBy(() -> new RankingWeightProperties(-0.1, 0.3, 0.8))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("non-negative");
        }
    }
}
