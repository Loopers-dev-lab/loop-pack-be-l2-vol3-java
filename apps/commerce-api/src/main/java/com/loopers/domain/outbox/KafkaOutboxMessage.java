package com.loopers.domain.outbox;

public record KafkaOutboxMessage(String eventId, String eventType, String payload, String occurredAt) {}