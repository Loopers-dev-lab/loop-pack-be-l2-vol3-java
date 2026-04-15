package com.loopers.domain.ranking;

import lombok.Getter;

@Getter
public enum RankingWeight {

    VIEW(0.1),
    LIKE(0.2),
    ORDER(0.6);

    private final double weight;

    RankingWeight(double weight) {
        this.weight = weight;
    }

    public double calculate(double rawScore) {
        return weight * rawScore;
    }

    public static RankingWeight fromEventType(String eventType) {
        return switch (eventType) {
            case "PRODUCT_VIEWED" -> VIEW;
            case "PRODUCT_LIKED", "PRODUCT_UNLIKED" -> LIKE;
            case "ORDER_COMPLETED" -> ORDER;
            default -> throw new IllegalArgumentException("알 수 없는 이벤트 타입: " + eventType);
        };
    }
}
