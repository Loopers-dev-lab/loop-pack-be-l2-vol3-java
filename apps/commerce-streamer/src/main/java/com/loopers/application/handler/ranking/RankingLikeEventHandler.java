package com.loopers.application.handler.ranking;

import com.loopers.application.EventHandler;
import com.loopers.domain.ranking.RankingScoreUpdater;
import com.loopers.event.Event;
import com.loopers.event.EventPayload;
import com.loopers.event.EventType;
import com.loopers.event.payload.ProductLikedEventPayload;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RankingLikeEventHandler implements EventHandler<ProductLikedEventPayload> {
    private final RankingScoreUpdater rankingScoreUpdater;

    @Override
    public boolean supports(Event<EventPayload> event) {
        return event.getType() == EventType.PRODUCT_LIKED;
    }

    @Override
    public void handle(Event<ProductLikedEventPayload> event) {
        rankingScoreUpdater.incrementLike(event.getPayload().getProductId());
    }
}
