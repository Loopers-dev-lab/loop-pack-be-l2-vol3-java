package com.loopers.application.ranking;

import com.loopers.event.EventType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RankingScoreCalculator {

    private final RankingProperties properties;

    public double calculate(EventType eventType, Double eventValue) {
        return switch (eventType) {
            case PRODUCT_VIEWED -> properties.viewWeight() * 1;
            case LIKE_CREATED -> properties.likeWeight() * 1;
            case LIKE_CANCELED -> -properties.likeWeight() * 1;
            case ORDER_COMPLETED -> properties.orderWeight() * 1;
            default -> 0.0;
        };
    }
}
