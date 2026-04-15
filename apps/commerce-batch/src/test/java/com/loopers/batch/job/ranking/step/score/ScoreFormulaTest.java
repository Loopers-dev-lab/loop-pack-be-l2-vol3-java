package com.loopers.batch.job.ranking.step.score;

import com.loopers.domain.ranking.weight.WeightConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

class ScoreFormulaTest {

    private static final WeightConfig DEFAULT = new WeightConfig("control", 0.1, 0.2, 0.7, 100, true);

    @DisplayName("score = w_view × view + w_like × like + w_order × log10(sales + 1)")
    @Test
    void computesWeightedSum() {
        // w_view=0.1, w_like=0.2, w_order=0.7
        // view=100, like=50, sales=999
        // → 0.1*100 + 0.2*50 + 0.7*log10(1000) = 10 + 10 + 2.1 = 22.1
        double score = ScoreFormula.compute(100, 50, 999, DEFAULT);

        assertThat(score).isCloseTo(22.1, offset(1e-9));
    }

    @DisplayName("sales=0 이어도 log10(1)=0 으로 안전하게 계산된다 (log10(0) 회피).")
    @Test
    void handlesZeroSalesSafely() {
        double score = ScoreFormula.compute(0, 0, 0, DEFAULT);

        assertThat(score).isEqualTo(0.0);
    }

    @DisplayName("view/like 는 선형, sales 는 log 스케일이라 큰 금액도 다른 지표를 압도하지 않는다.")
    @Test
    void salesIsLogNormalized() {
        // sales 1_000_000 → log10(1_000_001) ≈ 6
        // 0.7 * 6 ≈ 4.2 (view 42 나 like 21 과 동급)
        double scoreHighSales = ScoreFormula.compute(0, 0, 1_000_000, DEFAULT);

        assertThat(scoreHighSales).isCloseTo(0.7 * 6, offset(0.001));
    }

    @DisplayName("weight 가 다른 두 config 는 같은 입력에 다른 score 를 만든다.")
    @Test
    void weightDrivesDivergentScores() {
        WeightConfig viewHeavy  = new WeightConfig("a", 0.8, 0.1, 0.1, 50, true);
        WeightConfig orderHeavy = new WeightConfig("b", 0.1, 0.1, 0.8, 50, true);

        double s1 = ScoreFormula.compute(100, 100, 100, viewHeavy);
        double s2 = ScoreFormula.compute(100, 100, 100, orderHeavy);

        assertThat(s1).isNotEqualTo(s2);
    }
}
