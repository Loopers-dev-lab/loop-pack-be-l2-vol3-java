package com.loopers.domain.ranking;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RankingEventType {

    VIEW(0.1),
    LIKE(0.2),
    ORDER(0.7);

    private final double weight;

    public double calculateScore(int amount) {
        return switch (this) {
            case VIEW, LIKE -> this.weight;
            case ORDER -> this.weight * amount;
        };
    }
}
