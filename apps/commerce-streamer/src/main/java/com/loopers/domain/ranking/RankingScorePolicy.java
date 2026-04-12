package com.loopers.domain.ranking;

import org.springframework.stereotype.Component;

@Component
public class RankingScorePolicy {

    private static final double VIEW_SCORE = 0.1;
    private static final double LIKE_SCORE = 0.2;
    private static final double ORDER_MULTIPLIER = 0.6;

    public double calculate(String eventType, Long price, Integer quantity) {
        return switch (eventType) {
            case "VIEW" -> VIEW_SCORE;
            case "LIKE" -> LIKE_SCORE;
            case "ORDER" -> price * quantity * ORDER_MULTIPLIER;
            default -> 0.0;
        };
    }
}
