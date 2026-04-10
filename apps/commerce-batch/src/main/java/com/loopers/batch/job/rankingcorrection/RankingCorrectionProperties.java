package com.loopers.batch.job.rankingcorrection;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ranking")
public record RankingCorrectionProperties(Weights weights) {
    public record Weights(double view, double like, double order) {}
}
