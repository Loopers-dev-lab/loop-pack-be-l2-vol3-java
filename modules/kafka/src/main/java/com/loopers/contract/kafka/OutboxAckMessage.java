package com.loopers.contract.kafka;

import java.time.Instant;
import java.util.UUID;

public record OutboxAckMessage(
        UUID eventId,
        String consumerGroup,
        Instant ackedAt
) {
}
