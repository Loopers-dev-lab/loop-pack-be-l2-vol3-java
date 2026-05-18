package com.loopers.domain.ranking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ProductMetricsAggregationTest {

    @DisplayName("saturation 기반 점수를 계산한다: (x/(x+k)) × 가중치의 합")
    @Test
    void calculateScore_saturation() {
        var aggregation = new ProductMetricsAggregation(1L, 100, 50, 10);

        // k=100 가정:
        // view : 100/(100+100) × 0.1 = 0.05
        // like : 50/(50+100)   × 0.2 ≈ 0.0667
        // sales: 10/(10+100)   × 0.7 ≈ 0.0636
        // 합: ≈ 0.1803
        assertThat(aggregation.calculateScore()).isCloseTo(0.180303, within(0.0001));
    }

    @DisplayName("모든 카운트가 0이면 점수도 0이다")
    @Test
    void calculateScore_allZero() {
        var aggregation = new ProductMetricsAggregation(1L, 0, 0, 0);

        assertThat(aggregation.calculateScore()).isEqualTo(0.0);
    }

    @DisplayName("값이 매우 커져도 점수는 가중치 합(0.1+0.2+0.7=1.0)을 넘지 않는다 (포화 특성)")
    @Test
    void calculateScore_saturates() {
        var aggregation = new ProductMetricsAggregation(1L, 1_000_000, 1_000_000, 1_000_000);

        // 각 항이 거의 1에 수렴 → 총합은 거의 1.0
        assertThat(aggregation.calculateScore()).isLessThan(1.0).isGreaterThan(0.99);
    }

    @DisplayName("큰 값 차이가 점수에서는 크게 벌어지지 않는다 (롱테일 변별력)")
    @Test
    void calculateScore_longTailDiscrimination() {
        // 선형이라면 10배 차이지만 saturation에서는 거의 같아야 함
        var popular = new ProductMetricsAggregation(1L, 10_000, 0, 0);
        var veryPopular = new ProductMetricsAggregation(2L, 100_000, 0, 0);

        double popularScore = popular.calculateScore();
        double veryPopularScore = veryPopular.calculateScore();

        // 두 점수 모두 가중치(0.1)에 근접하지만 아주 미세하게 다름
        assertThat(veryPopularScore - popularScore).isLessThan(0.001);
    }

    @DisplayName("음수 카운트는 0으로 간주하여 점수에 영향을 주지 않는다")
    @Test
    void calculateScore_negativeCountTreatedAsZero() {
        var aggregation = new ProductMetricsAggregation(1L, 0, -5, 0);

        // 음수는 0으로 처리 → 점수 0
        assertThat(aggregation.calculateScore()).isEqualTo(0.0);
    }
}
