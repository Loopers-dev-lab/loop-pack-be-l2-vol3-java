package com.loopers.application.queue;

import com.loopers.domain.queue.EntryTokenRepository;
import com.loopers.domain.queue.WaitingQueueRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class EntryTokenScheduler {

    private final WaitingQueueRepository waitingQueueRepository;
    private final EntryTokenRepository entryTokenRepository;

    @Value("${queue.batch-size:18}")
    private int batchSize;

    @Value("${queue.token-ttl-seconds:300}")
    private long tokenTtlSeconds;

    @Scheduled(fixedDelayString = "${queue.scheduler-interval-ms:100}")
    public void issueTokens() {
        try {
            // ZRANGE — 큐에서 제거 없이 상위 N명 조회
            List<Long> candidates = waitingQueueRepository.peekBatch(batchSize);
            if (candidates.isEmpty()) {
                return;
            }

            int issued = 0;
            for (Long userId : candidates) {
                // SET NX EX — 이미 토큰이 있으면 건너뜀 (멱등성)
                // true: 새로 발급, false: 이미 존재 (크래시 복구 시나리오)
                boolean newlyIssued = entryTokenRepository.issueIfAbsent(userId, UUID.randomUUID().toString(), tokenTtlSeconds);

                // 토큰 보유 확인 후 즉시 ZREM — 폴링 주기와 무관하게 처리량 확보
                // 새로 발급됐거나, 이전 실행에서 발급 후 ZREM 실패한 복구 케이스 모두 제거
                waitingQueueRepository.remove(userId);

                if (newlyIssued) {
                    issued++;
                }
            }

            log.debug("입장 토큰 발급 완료: {}명 신규 발급, {}명 후보 처리", issued, candidates.size());
        } catch (Exception e) {
            // 최외곽 try/catch: @Scheduled 스레드 사망 방지
            // uncaught exception 발생 시 스케줄러 스레드 영구 정지 → 이후 모든 토큰 발급 중단
            log.error("토큰 발급 스케줄러 오류", e);
        }
    }
}
