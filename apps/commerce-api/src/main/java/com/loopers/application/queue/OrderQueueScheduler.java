package com.loopers.application.queue;

import com.loopers.domain.queue.EntryTokenRepository;
import com.loopers.domain.queue.WaitingQueueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OrderQueueScheduler {

    // 100ms마다 14명씩 = 초당 140명 = 140 TPS
    // DB 커넥션 풀 40개 × 초당 5건(1건 200ms) = 200 TPS → 안전 마진 70% = 140 TPS → 140 / 10 = 14
    private static final int BATCH_SIZE = 14;
    private static final Duration TOKEN_TTL = Duration.ofMinutes(5);

    private final WaitingQueueRepository waitingQueueRepository;
    private final EntryTokenRepository entryTokenRepository;

    @Scheduled(fixedRate = 100)
    public void issueTokens() {
        Set<Long> userIds = waitingQueueRepository.dequeue(BATCH_SIZE);
        for (Long userId : userIds) {
            String token = UUID.randomUUID().toString();
            entryTokenRepository.issueToken(userId, token, TOKEN_TTL);
        }
    }
}
