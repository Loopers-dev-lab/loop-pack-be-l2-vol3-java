package com.loopers.application.queue;

import com.loopers.domain.queue.QueueRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class QueueScheduler {

    /**
     * 처리량 설계 기준 (상세: docs/design/05-queue-throughput-design.md)
     * - DB 커넥션 풀: 50 (jpa.yml)
     * - 주문 1건 평균 처리 시간: 200ms (보수적 추정, 실측 필요)
     * - 이론적 최대 TPS: 50 / 0.2 = 250
     * - 안전 마진 70%: 175 TPS
     * - Thundering Herd 완화: 1초를 10구간으로 나눠 100ms마다 18명씩 발급
     */
    private static final int BATCH_SIZE = 18;
    private static final long TOKEN_TTL_SECONDS = 300;

    private final QueueRepository queueRepository;

    @Scheduled(fixedRate = 100)
    @SchedulerLock(name = "queue-activate", lockAtMostFor = "PT1S", lockAtLeastFor = "PT0.09S")
    public void activateUsers() {
        long activeCount = queueRepository.getActiveTokenCount();
        long deficit = BATCH_SIZE - activeCount;
        if (deficit <= 0) {
            return;
        }

        List<Long> userIds = queueRepository.popFromWaitQueue(deficit);
        if (userIds.isEmpty()) {
            return;
        }

        for (Long userId : userIds) {
            String token = UUID.randomUUID().toString();
            queueRepository.issueToken(userId, token, TOKEN_TTL_SECONDS);
        }
        queueRepository.incrementActiveTokenCount(userIds.size(), TOKEN_TTL_SECONDS);
        log.debug("대기열 활성화: {}명 토큰 발급 (활성: {}, 보충: {})", userIds.size(), activeCount, deficit);
    }
}
