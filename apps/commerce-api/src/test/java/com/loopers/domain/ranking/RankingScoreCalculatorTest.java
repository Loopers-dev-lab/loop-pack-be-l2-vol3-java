package com.loopers.domain.ranking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RankingScoreCalculatorTest {

    @DisplayName("이벤트별 점수를 계산할 때, ")
    @Nested
    class CalculateScore {

        @DisplayName("조회 이벤트이면, 0.1점을 반환한다.")
        @Test
        void calculateScore_whenViewEvent() {
            assertThat(RankingEventType.VIEW.calculateScore(0))
                    .isCloseTo(0.1, within(0.0001));
        }

        @DisplayName("좋아요 이벤트이면, 0.2점을 반환한다.")
        @Test
        void calculateScore_whenLikeEvent() {
            assertThat(RankingEventType.LIKE.calculateScore(0))
                    .isCloseTo(0.2, within(0.0001));
        }

        @DisplayName("주문 이벤트이면, 0.7 × totalPrice를 반환한다.")
        @Test
        void calculateScore_whenOrderEvent() {
            assertThat(RankingEventType.ORDER.calculateScore(10000))
                    .isCloseTo(7000.0, within(0.0001));
        }
    }
}
