package com.loopers.application.queue;

import com.loopers.domain.queue.QueueConstants;
import com.loopers.domain.queue.QueueEntry;
import com.loopers.domain.queue.QueueRepository;
import com.loopers.domain.queue.QueueService;
import com.loopers.domain.queue.QueueTokenService;
import com.loopers.domain.queue.SchedulerLockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

// 대기열에서 유저를 순차적으로 꺼내 입장 토큰을 발급하는 스케줄러.
// 100ms 간격으로 실행되며, 한 번에 최대 BATCH_SIZE(18)명을 처리한다.
// 이를 통해 초당 최대 약 180명(18명 x 10회)의 유저를 대기열에서 입장시킨다.
//
// ZPOPMIN을 사용하여 대기열에서 원자적으로 꺼내고,
// 토큰 발급 실패 시 원래 score로 재삽입하여 순서를 보존한다.
//
// 분산 락:
// DB 기반 scheduler_lock 테이블로 멀티 인스턴스 환경에서 중복 실행을 방지한다.
// 다른 인스턴스가 실행 중이면 이번 주기를 스킵하고 다음 주기에 재시도한다.
// 인스턴스 장애 시 락 만료(30초)로 자동 해제된다.
@Slf4j
@RequiredArgsConstructor
@Component
public class QueueScheduler {

    private static final String LOCK_KEY = "QUEUE_SCHEDULER";
    private static final long LOCK_EXPIRE_SECONDS = 30;

    private final QueueService queueService;
    private final QueueTokenService queueTokenService;
    private final QueueRepository queueRepository;
    private final SchedulerLockRepository schedulerLockRepository;

    // 인스턴스 식별자 (디버깅/모니터링 용도)
    private final String instanceId = UUID.randomUUID().toString().substring(0, 8);

    // fixedDelay: 이전 실행이 완료된 후 설정된 시간(ms)만큼 기다린 뒤 다시 실행.
    // fixedRate와 달리 실행 시간이 겹치지 않아 동시성 이슈를 방지한다.
    // 값은 application.yml의 queue.scheduler.fixed-delay에서 관리한다.
    @Scheduled(fixedDelayString = "${queue.scheduler.fixed-delay}")
    public void processQueue() {
        if (!queueService.isQueueEnabled()) {
            return;
        }

        if (!schedulerLockRepository.tryAcquire(LOCK_KEY, instanceId, LOCK_EXPIRE_SECONDS)) {
            return;
        }

        try {
            processBatch();
        } finally {
            schedulerLockRepository.release(LOCK_KEY, instanceId);
        }
    }

    // ZPOPMIN으로 대기열에서 원자적으로 N명을 꺼내 토큰을 발급한다.
    // 토큰 발급 실패 시 원래 score로 재삽입하여 대기열 순서를 보존한다.
    private void processBatch() {
        List<QueueEntry> entries = queueRepository.popFront(QueueConstants.BATCH_SIZE);
        if (entries.isEmpty()) {
            return;
        }

        for (QueueEntry entry : entries) {
            try {
                // 이미 토큰이 있는 유저는 스킵한다.
                // (이전 주기에서 토큰은 발급했으나 ZPOPMIN 전 장애가 발생한 경우 대비)
                if (queueTokenService.hasToken(entry.userId())) {
                    continue;
                }
                queueTokenService.issueToken(entry.userId());
            } catch (Exception e) {
                // 토큰 발급 실패 시 원래 score로 재삽입하여 순서를 보존한다.
                // ZPOPMIN으로 이미 꺼냈으므로 재삽입하지 않으면 유저가 유실된다.
                queueRepository.enter(entry.userId(), entry.score());
                log.warn("토큰 발급 실패, 대기열 재삽입 userId={}", entry.userId(), e);
            }
        }
    }
}

