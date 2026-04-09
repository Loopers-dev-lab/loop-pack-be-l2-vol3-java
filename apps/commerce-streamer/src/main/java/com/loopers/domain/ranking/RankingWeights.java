package com.loopers.domain.ranking;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 랭킹 점수 가중치 설정.
 *
 * YAML 에서 `ranking.weights.view / like / order` 로 주입되며,
 * 합계가 1.0 이 되도록 튜닝하는 것을 권장한다 (해석 편의).
 *
 * 초기값: view=0.1, like=0.2, order=0.7
 */
@ConfigurationProperties(prefix = "ranking.weights")
public record RankingWeights(
        double view,
        double like,
        double order
) {
    public RankingWeights {
        if (!Double.isFinite(view) || !Double.isFinite(like) || !Double.isFinite(order)) {
            throw new IllegalArgumentException(
                    "ranking.weights must be finite numbers (no NaN/Infinity)");
        }
        if (view < 0 || like < 0 || order < 0) {
            throw new IllegalArgumentException(
                    "ranking.weights must be non-negative");
        }
        if (view + like + order <= 0) {
            throw new IllegalArgumentException(
                    "ranking.weights sum must be positive");
        }
    }
}
