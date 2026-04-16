package com.loopers.batch.job.ranking.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RankingScoreCalculator")
class RankingScoreCalculatorTest {

    private RankingScoreCalculator calculator() {
        RankingWeights w = new RankingWeights();
        w.setLike(0.2);
        w.setOrder(0.7);
        w.setView(0.1);
        return new RankingScoreCalculator(w);
    }

    @Nested
    @DisplayName("calculate 메서드는")
    class Calculate {

        @Test
        @DisplayName("모두 0이면 0을 반환한다")
        void returnsZero_whenAllZero() {
            assertThat(calculator().calculate(0, 0, 0, 0)).isEqualByComparingTo("0.0000");
        }

        @Test
        @DisplayName("주문이 없으면 금액 log 보너스가 0이다 (avgAmount=0)")
        void noOrder_noAmountBonus() {
            // like=10*0.2 + order=0 + view=100*0.1 = 2 + 0 + 10 = 12
            BigDecimal score = calculator().calculate(10, 0, 100, 0);
            assertThat(score).isEqualByComparingTo("12.0000");
        }

        @Test
        @DisplayName("주문이 있으면 평균 금액의 log 보너스가 건당 가산된다")
        void order_withAmountBonus() {
            // orderCount=3, orderAmount=30000 → avg=10000 → log₁₀(10001) ≈ 4.0000
            // orderScore/건 = 0.7 + 0.01*4.0000 = 0.74
            // score = 0 + 3*0.74 + 0 = 2.22
            BigDecimal score = calculator().calculate(0, 3, 0, 30000);
            assertThat(score).isEqualByComparingTo("2.2200");
        }

        @Test
        @DisplayName("좋아요·주문·조회 모두 있으면 가중합이 소수 4자리로 반환된다")
        void weightedSum_allMetrics() {
            // like=10*0.2=2.0
            // order=3, amount=30000 → avg=10000 → log≈4 → 3*(0.7+0.04)=2.22
            // view=100*0.1=10.0
            // total ≈ 14.22
            BigDecimal score = calculator().calculate(10, 3, 100, 30000);
            assertThat(score).isEqualByComparingTo("14.2200");
        }

        @Test
        @DisplayName("고가 상품 소량 주문이 저가 상품 대량 주문을 역전할 수 있는 소프트 위계를 보장한다")
        void softHierarchy() {
            // 고가 소량: 1건 * 1,000,000원 → avg=1M → log≈6 → 1*(0.7+0.06)=0.76
            BigDecimal highPrice = calculator().calculate(0, 1, 0, 1_000_000);
            // 저가 대량: 10건 * 1,000원 → avg=1000 → log≈3 → 10*(0.7+0.03)=7.30
            BigDecimal lowPriceBulk = calculator().calculate(0, 10, 0, 10_000);
            // 건수가 많은 쪽이 기본적으로 이김 (soft hierarchy)
            assertThat(lowPriceBulk).isGreaterThan(highPrice);
        }
    }
}
