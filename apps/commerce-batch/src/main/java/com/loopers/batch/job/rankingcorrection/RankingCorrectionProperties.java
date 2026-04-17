package com.loopers.batch.job.rankingcorrection;

import com.loopers.domain.ranking.ScoreFormula;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

@ConfigurationProperties(prefix = "ranking")
public record RankingCorrectionProperties(
    ScoreFormula.Weights weights,
    Map<Long, Integer> categoryPriority,
    int defaultCategoryPriority
) {
    public RankingCorrectionProperties {
        if (categoryPriority == null) categoryPriority = Map.of();
    }
}
