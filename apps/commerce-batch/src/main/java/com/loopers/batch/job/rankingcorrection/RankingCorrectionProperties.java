package com.loopers.batch.job.rankingcorrection;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

@ConfigurationProperties(prefix = "ranking")
public record RankingCorrectionProperties(
    Weights weights,
    Map<Long, Integer> categoryPriority,
    int defaultCategoryPriority
) {
    public RankingCorrectionProperties {
        if (categoryPriority == null) categoryPriority = Map.of();
    }

    public record Weights(double view, double like, double order) {}
}
