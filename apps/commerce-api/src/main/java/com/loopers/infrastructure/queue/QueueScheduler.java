package com.loopers.infrastructure.queue;

import com.loopers.application.queue.QueueService;
import com.loopers.application.queue.TokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "queue.scheduler.enabled", havingValue = "true", matchIfMissing = false)
public class QueueScheduler {

    private final QueueService queueService;
    private final TokenService tokenService;

    /**
     * 배치 크기 산정 근거:
     * - HikariCP 최대 커넥션: 40개
     * - 주문 1건 평균 처리 시간: 200ms (SELECT FOR UPDATE × N + INSERT)
     * - 이론적 최대 TPS: 40 / 0.2 = 200 TPS
     * - 안전 마진 70% 적용: 140 TPS
     * - 스케줄러 주기: 100ms → 100ms당 처리 가능 인원 = 140 * 0.1 = 14명
     *
     * At-most-once 트레이드오프: ZREM 후 토큰 발급 사이에 장애 발생 시
     * 해당 유저는 큐에서 제거되었으나 토큰을 받지 못할 수 있음.
     * 재진입으로 복구 가능하므로 허용.
     */
    @Value("${queue.scheduler.batch-size:14}")
    private int batchSize;

    @Value("${queue.scheduler.fixed-rate:100}")
    private long fixedRate;

    private final ScheduledExecutorService jitterExecutor = Executors.newScheduledThreadPool(4);

    @PreDestroy
    public void destroy() {
        jitterExecutor.shutdown();
    }

    @Scheduled(fixedRateString = "${queue.scheduler.fixed-rate:100}")
    public void process() {
        List<Long> users = queueService.peekBatch(batchSize);
        if (users.isEmpty()) {
            return;
        }
        users.forEach(userId -> {
            queueService.remove(userId);  // 즉시 큐에서 제거
            if (tokenService.validate(userId)) {
                return;  // 이미 토큰 있음 → TTL 유지
            }
            long jitterMs = ThreadLocalRandom.current().nextLong(0, fixedRate);
            jitterExecutor.schedule(
                () -> {
                    tokenService.issue(userId);
                    log.debug("입장 토큰 발급 (jitter={}ms): userId={}", jitterMs, userId);
                },
                jitterMs, TimeUnit.MILLISECONDS
            );
        });
    }
}
