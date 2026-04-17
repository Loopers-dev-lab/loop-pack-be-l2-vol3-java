package com.loopers.application.ranking;

import com.loopers.domain.ranking.WeightConfig;
import org.springframework.stereotype.Component;

@Component
public class RankingScorer {

    public double score(long viewCount, long likeCount, long salesAmount, WeightConfig config) {
        return config.getWView() * viewCount
                + config.getWLike() * likeCount
                + config.getWOrder() * Math.log10(salesAmount + 1);
    }
}
