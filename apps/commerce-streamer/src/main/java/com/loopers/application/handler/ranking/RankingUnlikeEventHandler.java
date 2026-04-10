package com.loopers.application.handler.ranking;

import com.loopers.application.EventHandler;
import com.loopers.domain.ranking.RankingScoreUpdater;
import com.loopers.event.Event;
import com.loopers.event.EventPayload;
import com.loopers.event.EventType;
import com.loopers.event.payload.ProductUnlikedEventPayload;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RankingUnlikeEventHandler implements EventHandler<ProductUnlikedEventPayload> {
    private final RankingScoreUpdater rankingScoreUpdater;

    @Override
    public boolean supports(Event<EventPayload> event) {
        return event.getType() == EventType.PRODUCT_UNLIKED;
    }

    @Override
    public void handle(Event<ProductUnlikedEventPayload> event) {
        rankingScoreUpdater.decrementLike(event.getPayload().getProductId());
    }
}
