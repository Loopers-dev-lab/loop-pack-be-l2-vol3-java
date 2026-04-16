package com.loopers.domain.ranking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RankingScoreCalculatorTest {

    @DisplayName("가중치 검증 — 단일 카운트")
    @Nested
    class SingleWeight {

        @DisplayName("view 1건만 있으면 VIEW_WEIGHT(1) 점수가 나온다")
        @Test
        void viewOnly() {
            double score = RankingScoreCalculator.calculate(1, 0, 0);

            assertThat(score).isEqualTo(RankingScoreCalculator.VIEW_WEIGHT);
        }

        @DisplayName("like 1건만 있으면 LIKE_WEIGHT(2) 점수가 나온다")
        @Test
        void likeOnly() {
            double score = RankingScoreCalculator.calculate(0, 1, 0);

            assertThat(score).isEqualTo(RankingScoreCalculator.LIKE_WEIGHT);
        }

        @DisplayName("order 1건만 있으면 ORDER_WEIGHT(7) 점수가 나온다")
        @Test
        void orderOnly() {
            double score = RankingScoreCalculator.calculate(0, 0, 1);

            assertThat(score).isEqualTo(RankingScoreCalculator.ORDER_WEIGHT);
        }
    }

    @DisplayName("엣지 케이스")
    @Nested
    class EdgeCases {

        @DisplayName("모든 카운트가 0이면 점수는 0이다")
        @Test
        void allZero() {
            double score = RankingScoreCalculator.calculate(0, 0, 0);

            assertThat(score).isEqualTo(0.0);
        }
    }

    @DisplayName("drift 검증")
    @Nested
    class Drift {

        @DisplayName("SQL 점수와 Calculator 공식이 일치하면 통과한다")
        @Test
        void consistent() {
            double sqlScore = 565.0;

            RankingScoreCalculator.assertConsistent(sqlScore, 300, 80, 15);
            // 예외 없이 통과하면 OK
        }

        @DisplayName("SQL 점수가 Calculator 기대값과 다르면 IllegalStateException이 발생한다")
        @Test
        void drift() {
            double driftedSqlScore = 999.0; // 실제 기대값은 565

            assertThatThrownBy(() ->
                RankingScoreCalculator.assertConsistent(driftedSqlScore, 300, 80, 15)
            )
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("drift");
        }
    }

    @DisplayName("복합 시나리오")
    @Nested
    class Composite {

        @DisplayName("view 300 + like 80 + order 15 → 300*1 + 80*2 + 15*7 = 565 (E2E 샘플과 동일)")
        @Test
        void weeklyScenarioSample() {
            double score = RankingScoreCalculator.calculate(300, 80, 15);

            assertThat(score).isEqualTo(565.0);
        }

        @DisplayName("view 50 + like 10 + order 20 → 50*1 + 10*2 + 20*7 = 210")
        @Test
        void weeklyScenarioSecondPlace() {
            double score = RankingScoreCalculator.calculate(50, 10, 20);

            assertThat(score).isEqualTo(210.0);
        }
    }
}
