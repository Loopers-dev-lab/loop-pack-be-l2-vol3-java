package com.loopers.infrastructure.payment.pgsimulator.dto;

public record PgSimulatorApiResponse<T>(
        String result,
        T data
) {
}
