package com.loopers.batch.outbox;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OutboxDlqRedriveScheduler {

    private final OutboxDlqRedriveService outboxDlqRedriveService;
    private final OutboxDlqRedriveProperties properties;

    public OutboxDlqRedriveScheduler(
            OutboxDlqRedriveService outboxDlqRedriveService,
            OutboxDlqRedriveProperties properties
    ) {
        this.outboxDlqRedriveService = outboxDlqRedriveService;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${outbox.dlq-redrive.fixed-delay-ms:5000}")
    public void redrive() {
        if (!properties.enabled()) {
            return;
        }
        outboxDlqRedriveService.redriveOnce(properties.batchSize());
    }
}
