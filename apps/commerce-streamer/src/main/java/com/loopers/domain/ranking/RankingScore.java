package com.loopers.domain.ranking;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record RankingScore(
        Long productId,
        double score
) {

    public RankingScore {
        Objects.requireNonNull(productId, "상품 ID는 필수입니다.");
    }

    public static List<RankingScore> mergeAll(List<RankingScore> scores) {
        Map<Long, RankingScore> merged = new HashMap<>();
        for (RankingScore score : scores) {
            merged.merge(score.productId(), score, RankingScore::merge);
        }
        return merged.values().stream()
                .filter(s -> s.score() != 0.0)
                .toList();
    }

    public RankingScore merge(RankingScore other) {
        if (!this.productId.equals(other.productId)) {
            throw new IllegalArgumentException("서로 다른 상품의 점수는 합산할 수 없습니다.");
        }
        return new RankingScore(productId, this.score + other.score);
    }

    public RankingScore decay(double factor) {
        return new RankingScore(productId, score * factor);
    }
}
