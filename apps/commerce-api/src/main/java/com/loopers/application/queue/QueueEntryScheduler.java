package com.loopers.application.queue;

import com.loopers.domain.queue.QueueRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Component
public class QueueEntryScheduler {

    private static final String DEFAULT_EVENT_ID = "bf2024";

    private final QueueRepository queueRepository;
    private final QueueTokenService queueTokenService;
    private final QueueMetrics queueMetrics;

    @Scheduled(fixedDelay = QueueConstants.SCHEDULER_INTERVAL_MS)
    public void schedule() {
        processQueue(DEFAULT_EVENT_ID);
    }

    public void processQueue(String eventId) {
        List<Long> userIds = queueRepository.popFront(eventId, QueueConstants.BATCH_SIZE);

        if (userIds.isEmpty()) {
            return;
        }

        for (Long userId : userIds) {
            String token = queueTokenService.issueToken(eventId, userId);
            log.debug("토큰 발급 완료. eventId={}, userId={}, token={}", eventId, userId, token);
        }

        queueMetrics.recordTokenIssued(eventId, userIds.size());
        queueMetrics.updateWaitingSize(eventId, queueRepository.getTotalCount(eventId));
        log.info("대기열 처리 완료. eventId={}, 처리 건수={}", eventId, userIds.size());
    }
}
