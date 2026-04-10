package com.loopers.domain.ranking.dto;

import com.loopers.domain.ranking.ProductRankingScoreModel;
import java.util.ArrayList;
import java.util.List;

public class RankingInfo {

    public record RankedScore(long rank, Long productId, double score) {

        public static List<RankedScore> fromScoresWithRank(
                List<ProductRankingScoreModel> scores, int offset) {
            List<RankedScore> result = new ArrayList<>();
            long currentRank = offset + 1;
            Double previousScore = null;

            for (int i = 0; i < scores.size(); i++) {
                ProductRankingScoreModel score = scores.get(i);
                if (previousScore == null || score.getScore() != previousScore) {
                    currentRank = offset + i + 1;
                }
                previousScore = score.getScore();
                result.add(new RankedScore(
                        currentRank, score.getProductId(), score.getScore()));
            }
            return result;
        }
    }
}
