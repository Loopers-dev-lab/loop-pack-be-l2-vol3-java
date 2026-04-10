package com.loopers.application.queue;

import com.loopers.domain.queue.EntryToken;
import com.loopers.domain.queue.EntryTokenRepository;
import com.loopers.domain.queue.WaitingQueueRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class QueueScheduler {

    // 동시에 주문 진행 가능한 최대 유저 수 (DB 커넥션 풀 기준 설계)
    static final int MAX_CAPACITY = 200;

    private final WaitingQueueRepository waitingQueueRepository;
    private final EntryTokenRepository entryTokenRepository;

    @Scheduled(fixedDelay = 100)
    public void issueTokens() {
        try {
            long activeCount = entryTokenRepository.countActive();
            long available = Math.max(0L, MAX_CAPACITY - activeCount);
            if (available == 0) {
                return;
            }
            List<Long> userIds = waitingQueueRepository.popFront(available);
            for (Long userId : userIds) {
                EntryToken token = EntryToken.issue(userId);
                entryTokenRepository.save(token);
                log.debug("Entry token issued. userId={}", userId);
            }
        } catch (Exception e) {
            log.error("Failed to issue entry tokens. Skipping batch.", e);
        }
    }
}
