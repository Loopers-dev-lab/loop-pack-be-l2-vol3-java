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
 * <p>주기적으로 대기열에서 일정 인원을 입장열로 이동시킨다.
 * 배치 크기 산정 근거는 ADR-04 참고.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QueueAdmissionScheduler {

    private static final int BATCH_SIZE = 2;

    private final WaitingQueueAdmitter waitingQueueAdmitter;

    @Scheduled(fixedRate = 400)
    public void admit() {
        List<Long> admitted = waitingQueueAdmitter.admit(BATCH_SIZE);

        if (!admitted.isEmpty()) {
            log.debug("입장 허용 [count={}]", admitted.size());
        }
    }
}
