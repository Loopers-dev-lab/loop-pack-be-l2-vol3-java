package com.loopers.domain.ranking;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@DisplayName("RankingScoreCalculator 단위 테스트")
class RankingScoreCalculatorTest {

    private RankingWeights weights;
    private RankingScoreCalculator calculator;

    @BeforeEach
    void setUp() {
        // 기본 가중치: view=0.1, like=0.2, order=0.7 (합 1.0)
        weights = new RankingWeights(0.1, 0.2, 0.7);
        calculator = new RankingScoreCalculator(weights);
    }

    @Nested
    @DisplayName("기본 공식")
    class Formula {

        @Test
        @DisplayName("모든 지표가 0 이면 점수도 0")
        void zeroAggregate() {
            // given
            ProductDailyAggregate agg = ProductDailyAggregate.empty(1L);

            // when
            double score = calculator.calculate(agg);

            // then
            assertThat(score).isEqualTo(0.0);
        }

        @Test
        @DisplayName("공식: 0.1*log1p(view) + 0.2*log1p(like) + 0.7*log1p(amount)")
        void weightedLogFormula() {
            // given
            ProductDailyAggregate agg = new ProductDailyAggregate(
                    1L, 10L, 5L, 0L, BigDecimal.valueOf(10000));

            // when
            double score = calculator.calculate(agg);

            // then
            double expected = 0.1 * Math.log1p(10)
                    + 0.2 * Math.log1p(5)
                    + 0.7 * Math.log1p(10000);
            assertThat(score).isCloseTo(expected, within(1e-9));
        }

        @Test
        @DisplayName("null aggregate 는 0 을 반환 (null-safety)")
        void nullAggregate() {
            assertThat(calculator.calculate(null)).isEqualTo(0.0);
        }

        @Test
        @DisplayName("totalOrderAmount 가 null 이면 0 으로 clamp")
        void nullOrderAmount() {
            // given
            ProductDailyAggregate agg = new ProductDailyAggregate(1L, 5L, 2L, 0L, null);

            // when
            double score = calculator.calculate(agg);

            // then
            double expected = 0.1 * Math.log1p(5) + 0.2 * Math.log1p(2) + 0.7 * Math.log1p(0);
            assertThat(score).isCloseTo(expected, within(1e-9));
        }

        @Test
        @DisplayName("음수 입력은 0 으로 clamp 된다")
        void negativeInput() {
            // given
            ProductDailyAggregate agg = new ProductDailyAggregate(
                    1L, -5L, -3L, 0L, BigDecimal.valueOf(-100));

            // when
            double score = calculator.calculate(agg);

            // then
            assertThat(score).isEqualTo(0.0);
        }
    }

    @Nested
    @DisplayName("가중치 적용 검증 (과제 체크리스트)")
    class WeightAssertions {

        @Test
        @DisplayName("주문 1건(10000원) 이 좋아요 3건 보다 높은 점수를 가진다")
        void orderBeatsLikes() {
            // given
            ProductDailyAggregate orderOnly = new ProductDailyAggregate(
                    1L, 0L, 0L, 1L, BigDecimal.valueOf(10000));
            ProductDailyAggregate likeOnly = new ProductDailyAggregate(
                    2L, 0L, 3L, 0L, BigDecimal.ZERO);

            // when
            double orderScore = calculator.calculate(orderOnly);
            double likeScore = calculator.calculate(likeOnly);

            // then
            assertThat(orderScore).isGreaterThan(likeScore);
        }

        @Test
        @DisplayName("조회가 많아도 주문이 있는 상품을 이기지 못한다 (전체 log 정규화)")
        void orderBeatsManyViews() {
            // given
            ProductDailyAggregate manyViews = new ProductDailyAggregate(
                    1L, 5000L, 100L, 0L, BigDecimal.valueOf(10000));
            ProductDailyAggregate manyOrders = new ProductDailyAggregate(
                    2L, 100L, 5L, 0L, BigDecimal.valueOf(1_000_000));

            // when
            double viewsScore = calculator.calculate(manyViews);
            double ordersScore = calculator.calculate(manyOrders);

            // then — 매출 100배 이상 차이면 order 쪽이 승리해야 함
            assertThat(ordersScore).isGreaterThan(viewsScore);
        }

        @Test
        @DisplayName("가중치 튜닝(order 상향) 시 주문 기여도가 증가")
        void weightTuning() {
            // given — order 가중치를 극단적으로 높임
            RankingWeights tuned = new RankingWeights(0.05, 0.05, 0.9);
            RankingScoreCalculator tunedCalc = new RankingScoreCalculator(tuned);
            ProductDailyAggregate agg = new ProductDailyAggregate(
                    1L, 0L, 0L, 1L, BigDecimal.valueOf(10000));

            // when
            double defaultScore = calculator.calculate(agg);
            double tunedScore = tunedCalc.calculate(agg);

            // then — 동일 주문에 대해 가중치 높은 쪽이 더 큰 점수
            assertThat(tunedScore).isGreaterThan(defaultScore);
        }
    }

    @Nested
    @DisplayName("calculateRaw 편의 메서드")
    class CalculateRaw {

        @Test
        @DisplayName("view/like/amount 직접 입력 시 동일 공식 적용")
        void directInput() {
            // when
            double score = calculator.calculateRaw(10L, 5L, BigDecimal.valueOf(10000));

            // then
            double expected = 0.1 * Math.log1p(10)
                    + 0.2 * Math.log1p(5)
                    + 0.7 * Math.log1p(10000);
            assertThat(score).isCloseTo(expected, within(1e-9));
        }

        @Test
        @DisplayName("null orderAmount 는 0 으로 clamp")
        void nullAmount() {
            double score = calculator.calculateRaw(5L, 0L, null);
            assertThat(score).isCloseTo(0.1 * Math.log1p(5), within(1e-9));
        }
    }
}
