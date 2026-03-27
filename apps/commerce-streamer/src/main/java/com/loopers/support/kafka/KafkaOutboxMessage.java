package com.loopers.support.kafka;

public record KafkaOutboxMessage(String eventId, String eventType, String payload, String occurredAt) {}