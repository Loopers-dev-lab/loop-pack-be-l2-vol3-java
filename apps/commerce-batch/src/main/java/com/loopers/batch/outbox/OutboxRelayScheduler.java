package com.loopers.batch.outbox;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OutboxRelayScheduler {

    private final OutboxRelayService outboxRelayService;
    private final OutboxRelayProperties properties;

    public OutboxRelayScheduler(OutboxRelayService outboxRelayService, OutboxRelayProperties properties) {
        this.outboxRelayService = outboxRelayService;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${outbox.relay.fixed-delay-ms:1000}")
    public void relay() {
        if (!properties.enabled()) {
            return;
        }
        outboxRelayService.relayOnce(properties.batchSize());
    }
}

