package com.loopers.application.ranking;

import com.loopers.domain.ranking.WeightConfig;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class RankingScorerTest {

    private final RankingScorer scorer = new RankingScorer();

    @Nested
    class 점수_계산 {

        @Test
        void 기본_가중치로_점수를_계산한다() {
            WeightConfig config = WeightConfig.defaultConfig(); // 0.1, 0.2, 0.7

            double score = scorer.score(500, 5, 2, config);

            // 0.1*500 + 0.2*5 + 0.7*2 = 50 + 1 + 1.4 = 52.4
            assertThat(score).isCloseTo(52.4, within(0.001));
        }

        @Test
        void 조회_중심_가중치로_점수를_계산한다() {
            WeightConfig config = WeightConfig.create("experiment", 0.5, 0.3, 0.2, 50);

            double score = scorer.score(500, 5, 2, config);

            // 0.5*500 + 0.3*5 + 0.2*2 = 250 + 1.5 + 0.4 = 251.9
            assertThat(score).isCloseTo(251.9, within(0.001));
        }

        @Test
        void 모든_메트릭이_0이면_점수는_0이다() {
            WeightConfig config = WeightConfig.defaultConfig();

            double score = scorer.score(0, 0, 0, config);

            assertThat(score).isEqualTo(0.0);
        }

        @Test
        void 가중치_변경으로_순위가_뒤바뀔_수_있다() {
            // 주문 중심: 상품D(주문 30건)가 높음
            WeightConfig orderHeavy = WeightConfig.defaultConfig(); // 0.1, 0.2, 0.7
            double scoreC_orderHeavy = scorer.score(200, 50, 3, orderHeavy);
            double scoreD_orderHeavy = scorer.score(100, 10, 30, orderHeavy);
            assertThat(scoreD_orderHeavy).isGreaterThan(scoreC_orderHeavy);

            // 조회 중심: 상품C(좋아요 50건)가 높음
            WeightConfig viewHeavy = WeightConfig.create("exp", 0.5, 0.3, 0.2, 50);
            double scoreC_viewHeavy = scorer.score(200, 50, 3, viewHeavy);
            double scoreD_viewHeavy = scorer.score(100, 10, 30, viewHeavy);
            assertThat(scoreC_viewHeavy).isGreaterThan(scoreD_viewHeavy);
        }
    }
}
