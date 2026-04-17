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

            // salesAmount=50000 → log10(50001) ≈ 4.699
            double score = scorer.score(500, 5, 50000, config);

            // 0.1*500 + 0.2*5 + 0.7*log10(50001) = 50 + 1 + 3.289 ≈ 54.289
            double expected = 0.1 * 500 + 0.2 * 5 + 0.7 * Math.log10(50001);
            assertThat(score).isCloseTo(expected, within(0.001));
        }

        @Test
        void 조회_중심_가중치로_점수를_계산한다() {
            WeightConfig config = WeightConfig.create("experiment", 0.5, 0.3, 0.2, 50);

            double score = scorer.score(500, 5, 50000, config);

            double expected = 0.5 * 500 + 0.3 * 5 + 0.2 * Math.log10(50001);
            assertThat(score).isCloseTo(expected, within(0.001));
        }

        @Test
        void 모든_메트릭이_0이면_점수는_0이다() {
            WeightConfig config = WeightConfig.defaultConfig();

            double score = scorer.score(0, 0, 0, config);

            // log10(0+1) = 0
            assertThat(score).isEqualTo(0.0);
        }

        @Test
        void log10_정규화로_고가_상품_독식을_방지한다() {
            WeightConfig config = WeightConfig.defaultConfig();

            // 1만원 주문 vs 100만원 주문: 금액 100배 차이
            double scoreLow = scorer.score(0, 0, 10000, config);
            double scoreHigh = scorer.score(0, 0, 1000000, config);

            // log10 덕분에 점수 차이는 100배가 아니라 약 1.5배
            assertThat(scoreHigh).isLessThan(scoreLow * 3);
            assertThat(scoreHigh).isGreaterThan(scoreLow);
        }

        @Test
        void 주문_1건이_조회_좋아요보다_적절한_비중을_가진다() {
            WeightConfig config = WeightConfig.defaultConfig();

            // 5만원 주문 1건의 order 기여: 0.7 * log10(50001) ≈ 3.29
            double orderOnly = scorer.score(0, 0, 50000, config);
            // 좋아요 17건의 like 기여: 0.2 * 17 = 3.4
            double likeOnly = scorer.score(0, 17, 0, config);

            // 5만원 주문 1건 ≈ 좋아요 17건 수준
            assertThat(orderOnly).isCloseTo(likeOnly, within(0.2));
        }

        @Test
        void 가중치_변경으로_순위가_뒤바뀔_수_있다() {
            // 상품A: 매출 1000만원, view/like 적음
            // 상품B: 매출 1000원, view 200회, like 30건
            // 주문 중심(0.7): 매출 높은 A가 이김
            WeightConfig orderHeavy = WeightConfig.defaultConfig(); // 0.1, 0.2, 0.7
            double scoreA_orderHeavy = scorer.score(10, 2, 10000000, orderHeavy);
            double scoreB_orderHeavy = scorer.score(10, 2, 1000, orderHeavy);
            assertThat(scoreA_orderHeavy).isGreaterThan(scoreB_orderHeavy);

            // 조회 중심(0.5): view/like 높은 B가 이김
            WeightConfig viewHeavy = WeightConfig.create("exp", 0.5, 0.3, 0.2, 50);
            double scoreA_viewHeavy = scorer.score(10, 2, 10000000, viewHeavy);
            double scoreB_viewHeavy = scorer.score(200, 30, 1000, viewHeavy);
            assertThat(scoreB_viewHeavy).isGreaterThan(scoreA_viewHeavy);
        }
    }
}
