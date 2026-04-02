package com.loopers.interfaces.scheduler;

import com.loopers.application.queue.QueueFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@Component
public class QueueScheduler {

    private final QueueFacade queueFacade;

    @Scheduled(fixedDelayString = "${queue.scheduler-interval-ms}")
    public void issueTokens() {
        try {
            queueFacade.issueTokens();
        } catch (Exception e) {
            log.error("queue token issuing failed", e);
        }
    }
}
