package com.loopers.interfaces.consumer;

import java.time.LocalDate;

public record RankingCommandPayload(
        String commandType,
        LocalDate date
) {
    public static final String RECALCULATE = "RECALCULATE";
}
