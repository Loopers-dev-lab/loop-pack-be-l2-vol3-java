package com.loopers.batch.job.ranking;

import com.loopers.batch.config.RankingBatchProperties;
import org.springframework.stereotype.Component;

/**
 * 이벤트 타입 → 점수 delta 계산.
 *
 * commerce-streamer의 RankingScoreUpdater.calculateDelta()와 동일 로직.
 * 두 모듈이 같은 가중치를 쓰도록 RankingBatchProperties가
 * RankingProperties와 같은 "ranking" prefix yml을 공유한다.
 *
 * 가중치 변경 시: commerce-streamer의 RankingScoreUpdater와 동시 수정 필수.
 */
@Component
public class RankingEventScorer {

    private final RankingBatchProperties properties;

    public RankingEventScorer(RankingBatchProperties properties) {
        this.properties = properties;
    }

    public double calculateDelta(String eventType) {
        RankingBatchProperties.Weights w = properties.getWeights();
        return switch (eventType) {
            case "ProductViewedEvent" -> w.getView();
            case "ProductLikedEvent" -> w.getLike();
            case "ProductUnlikedEvent" -> w.getUnlike();
            case "OrderItemSoldEvent" -> w.getOrder();
            default -> 0.0;
        };
    }
}
