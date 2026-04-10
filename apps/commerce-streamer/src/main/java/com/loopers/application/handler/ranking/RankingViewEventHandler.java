package com.loopers.application.handler.ranking;

import com.loopers.application.EventHandler;
import com.loopers.domain.ranking.RankingScoreUpdater;
import com.loopers.event.Event;
import com.loopers.event.EventPayload;
import com.loopers.event.EventType;
import com.loopers.event.payload.ProductViewedEventPayload;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RankingViewEventHandler implements EventHandler<ProductViewedEventPayload> {
    private final RankingScoreUpdater rankingScoreUpdater;

    @Override
    public boolean supports(Event<EventPayload> event) {
        return event.getType() == EventType.PRODUCT_VIEWED;
    }

    @Override
    public void handle(Event<ProductViewedEventPayload> event) {
        rankingScoreUpdater.incrementView(event.getPayload().getProductId());
    }
}
