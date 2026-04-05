package com.loopers.application.queue;

import com.loopers.domain.queue.QueueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class QueueAdmissionScheduler {

    private static final int BATCH_SIZE = 7; // 100ms당 ~7명 (초당 ~70명)

    private final QueueService queueService;

    @Scheduled(fixedRate = 100)
    public void processQueue() {
        List<Long> processedUserIds = queueService.processBatch(BATCH_SIZE);

        if (!processedUserIds.isEmpty()) {
            log.info("대기열 입장 토큰 발급: {}명 (userIds={})", processedUserIds.size(), processedUserIds);
        }
    }
}
