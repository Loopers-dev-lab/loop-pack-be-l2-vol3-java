package com.loopers.application.ranking;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ranking")
public record RankingProperties(
    String keyPrefix,
    int ttlDays
) {
}
