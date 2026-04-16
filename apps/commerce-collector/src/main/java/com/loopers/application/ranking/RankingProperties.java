package com.loopers.application.ranking;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ranking")
public record RankingProperties(
    double viewWeight,
    double likeWeight,
    double orderWeight,
    String keyPrefix,
    int ttlDays
) {
}
