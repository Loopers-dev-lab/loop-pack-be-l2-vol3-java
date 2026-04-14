package com.loopers.application.ranking;

import com.loopers.domain.ranking.RankingRepository;
import com.loopers.interfaces.consumer.payload.CatalogEventPayload;
import com.loopers.interfaces.consumer.payload.OrderCreatedEventPayload;
import com.loopers.interfaces.consumer.payload.ProductViewEventPayload;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

@RequiredArgsConstructor
@Component
public class RankingFacade {

    private final RankingRepository rankingRepository;

    public void applyView(ProductViewEventPayload payload) {
        LocalDate date = toUtcDate(payload.occurredAt());
        rankingRepository.incrementScore(payload.productId(), date, 0.1);
    }

    public void applyLike(CatalogEventPayload payload) {
        LocalDate date = toUtcDate(payload.occurredAt());
        rankingRepository.incrementScore(payload.productId(), date, payload.delta() * 0.2);
    }

    public void applyOrder(OrderCreatedEventPayload payload) {
        LocalDate date = toUtcDate(payload.occurredAt());
        payload.items().forEach(item -> {
            double increment = 0.7 * Math.log1p((double) item.quantity() * item.unitPrice());
            rankingRepository.incrementScore(item.productId(), date, increment);
        });
    }

    private LocalDate toUtcDate(ZonedDateTime occurredAt) {
        return occurredAt.withZoneSameInstant(ZoneOffset.UTC).toLocalDate();
    }
}
