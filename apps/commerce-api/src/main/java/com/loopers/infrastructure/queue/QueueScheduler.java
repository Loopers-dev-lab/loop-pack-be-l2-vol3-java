package com.loopers.infrastructure.queue;

import com.loopers.domain.queue.QueueProperties;
import com.loopers.domain.queue.QueueService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 대기열 스케줄러
 *
 * 주기적으로 대기열 앞쪽에서 배치 크기만큼 사용자를 활성화한다.
 *
 * 설계 결정:
 * - fixedDelay 사용: 이전 실행이 끝나야 다음 대기 시작 → 겹침 방지
 * - ShedLock 미도입: 현재 단일 서버 환경 (멀티 인스턴스 시 도입 필요)
 * - 대기열이 꺼져 있으면 스케줄링도 건너뜀
 */
@Component
public class QueueScheduler {

    private static final Logger log = LoggerFactory.getLogger(QueueScheduler.class);

    private final QueueService queueService;
    private final QueueProperties queueProperties;

    public QueueScheduler(QueueService queueService, QueueProperties queueProperties) {
        this.queueService = queueService;
        this.queueProperties = queueProperties;
    }

    @Scheduled(fixedDelayString = "${queue.scheduler-interval-ms:1000}")
    public void activateNextBatch() {
        if (!queueProperties.isEnabled()) {
            return;
        }

        try {
            int activated = queueService.activateNextBatch();
            if (activated > 0) {
                log.debug("대기열 스케줄러 실행: {}명 활성화", activated);
            }
        } catch (Exception e) {
            log.error("대기열 스케줄러 실행 실패", e);
        }
    }
}
