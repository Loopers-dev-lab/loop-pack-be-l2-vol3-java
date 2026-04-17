package com.loopers.batch.job.ranking.step.score;

import com.loopers.domain.ranking.weight.WeightConfig;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ScoreFormulaTest {

    private static final WeightConfig DEFAULT = new WeightConfig("control", 0.1, 0.2, 0.7, 100, true);

    @Nested
    class 점수_계산 {

        @Test
        void 가중합_공식대로_score_를_계산한다() {
            // w_view=0.1, w_like=0.2, w_order=0.7
            // view=100, like=50, sales=999
            // → 0.1*100 + 0.2*50 + 0.7*log10(1000) = 10 + 10 + 2.1 = 22.1
            double score = ScoreFormula.compute(100, 50, 999, DEFAULT);

            assertThat(score).isCloseTo(22.1, offset(1e-9));
        }

        @Test
        void sales_가_0이어도_log10_1_로_안전하게_계산된다() {
            double score = ScoreFormula.compute(0, 0, 0, DEFAULT);

            assertThat(score).isEqualTo(0.0);
        }

        @Test
        void sales_는_log_스케일이라_큰_금액도_다른_지표를_압도하지_않는다() {
            // sales 1_000_000 → log10(1_000_001) ≈ 6
            // 0.7 * 6 ≈ 4.2 (view 42 나 like 21 과 동급)
            double scoreHighSales = ScoreFormula.compute(0, 0, 1_000_000, DEFAULT);

            assertThat(scoreHighSales).isCloseTo(0.7 * 6, offset(0.001));
        }
    }

    @Nested
    class weight_분기 {

        @Test
        void weight_가_다른_두_config_는_같은_입력에_다른_score_를_만든다() {
            WeightConfig viewHeavy  = new WeightConfig("a", 0.8, 0.1, 0.1, 50, true);
            WeightConfig orderHeavy = new WeightConfig("b", 0.1, 0.1, 0.8, 50, true);

            double s1 = ScoreFormula.compute(100, 100, 100, viewHeavy);
            double s2 = ScoreFormula.compute(100, 100, 100, orderHeavy);

            assertThat(s1).isNotEqualTo(s2);
        }
    }
}
