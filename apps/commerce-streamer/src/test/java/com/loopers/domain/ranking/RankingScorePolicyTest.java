package com.loopers.domain.ranking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RankingScorePolicyTest {

    private final RankingScorePolicy policy = new RankingScorePolicy();

    @Nested
    @DisplayName("이벤트 타입별 점수 계산")
    class CalculateScore {

        @Test
        @DisplayName("VIEW 이벤트는 0.1점을 반환한다")
        void viewEvent() {
            double score = policy.calculate("VIEW", 0L, 0);
            assertThat(score).isEqualTo(0.1);
        }

        @Test
        @DisplayName("LIKE 이벤트는 0.2점을 반환한다")
        void likeEvent() {
            double score = policy.calculate("LIKE", 0L, 0);
            assertThat(score).isEqualTo(0.2);
        }

        @Test
        @DisplayName("ORDER 이벤트는 price * quantity * 0.6을 반환한다")
        void orderEvent() {
            double score = policy.calculate("ORDER", 10000L, 3);
            assertThat(score).isEqualTo(10000 * 3 * 0.6);
        }

        @Test
        @DisplayName("ORDER 이벤트 - 다른 price/quantity 조합")
        void orderEventDifferentValues() {
            double score = policy.calculate("ORDER", 5000L, 2);
            assertThat(score).isEqualTo(5000 * 2 * 0.6);
        }

        @Test
        @DisplayName("알 수 없는 이벤트 타입은 0점을 반환한다")
        void unknownEvent() {
            double score = policy.calculate("UNKNOWN", 0L, 0);
            assertThat(score).isEqualTo(0.0);
        }
    }
}
