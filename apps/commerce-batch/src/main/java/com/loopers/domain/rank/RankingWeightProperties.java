package com.loopers.domain.rank;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ranking.weight")
public record RankingWeightProperties(
        double view,
        double like,
        double order
) {
    private static final double SUM_TOLERANCE = 0.0001;

    public RankingWeightProperties {
        if (view < 0 || like < 0 || order < 0) {
            throw new IllegalArgumentException(
                    "ranking weight must be non-negative: view=" + view + ", like=" + like + ", order=" + order
            );
        }
        double sum = view + like + order;
        if (Math.abs(sum - 1.0) > SUM_TOLERANCE) {
            throw new IllegalArgumentException(
                    "ranking weight sum must be 1.0 (tolerance " + SUM_TOLERANCE + "), actual=" + sum
            );
        }
    }
}
