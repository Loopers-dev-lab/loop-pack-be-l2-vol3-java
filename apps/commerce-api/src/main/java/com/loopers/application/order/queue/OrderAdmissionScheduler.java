package com.loopers.application.order.queue;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "loopers.queue.order", name = "scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class OrderAdmissionScheduler {

    private final OrderAdmissionApplicationService orderAdmissionApplicationService;

    @Scheduled(fixedDelayString = "${loopers.queue.order.scheduler-fixed-delay-ms:1000}")
    public void schedule() {
        orderAdmissionApplicationService.issueAdmissions();
    }
}
