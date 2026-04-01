package com.loopers.application.queue;

import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.loopers.support.queue.WaitingQueueAdmitter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 대기열 입장 허용 스케줄러.
 *
 * <p>300ms 주기로 대기열에서 최대 18명을 입장열로 이동시킨다.
 * Lua 스크립트로 ZRANGE → ZADD → ZREM을 원자적으로 실행한다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QueueAdmissionScheduler {

    private static final int BATCH_SIZE = 18;

    private final WaitingQueueAdmitter waitingQueueAdmitter;

    @Scheduled(fixedRate = 300)
    public void admit() {
        List<Long> admitted = waitingQueueAdmitter.admit(BATCH_SIZE);

        if (!admitted.isEmpty()) {
            log.debug("입장 허용 [count={}]", admitted.size());
        }
    }
}
