package com.loopers.domain.outbox;

public record ProductSoldPayload(Long productId, Long orderId, long amount) {}