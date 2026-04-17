package com.loopers.application.ranking;

import com.loopers.domain.ranking.ScoreFormula;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * 랭킹 시스템 설정.
 *
 * <p>Additionals "실시간 Weight 조절"을 위해 {@code @ConfigurationProperties}로 외부화.
 * Spring Cloud Config 또는 yml 변경 + actuator refresh로 런타임 가중치 조정이 가능하다.</p>
 *
 * <pre>
 * ranking:
 *   weights:
 *     view: 0.1
 *     like: 0.2
 *     order: 0.7
 *   carry-over-rate: 0.1
 *   monthly-decay-rate: 0.97
 *   category-priority: {}
 *   default-category-priority: 0
 *   experiment:
 *     enabled: false
 * </pre>
 */
@ConfigurationProperties(prefix = "ranking")
public record RankingProperties(
    ScoreFormula.Weights weights,
    double carryOverRate,
    double monthlyDecayRate,
    int carryOverCap,
    Map<Long, Integer> categoryPriority,
    int defaultCategoryPriority,
    Experiment experiment
) {
    public RankingProperties {
        if (monthlyDecayRate == 0) monthlyDecayRate = 0.97;
        if (carryOverCap == 0) carryOverCap = 10_000;
        if (categoryPriority == null) categoryPriority = Map.of();
        if (experiment == null) experiment = new Experiment(false, Map.of());
    }

    public record Experiment(boolean enabled, Map<String, Variant> variants) {
        public Experiment {
            if (variants == null) variants = Map.of();
        }
    }

    public record Variant(ScoreFormula.Weights weights, String zsetPrefix) {}
}
