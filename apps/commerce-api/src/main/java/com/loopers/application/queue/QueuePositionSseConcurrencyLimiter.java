package com.loopers.application.queue;

import org.springframework.stereotype.Component;

import java.util.concurrent.Semaphore;

/**
 * {@link QueuePositionStreamService}의 동시 SSE 연결 수를 {@link QueuePositionProperties.SseStream}에 맞춰 제한한다.
 * <p>
 * {@code maxConcurrentConnections == 0}이면 세마포어를 두지 않고 항상 허용한다.
 */
@Component
public class QueuePositionSseConcurrencyLimiter {

    private final Semaphore semaphore;

    public QueuePositionSseConcurrencyLimiter(QueuePositionProperties positionProperties) {
        int max = positionProperties.sseStream().maxConcurrentConnections();
        this.semaphore = max == 0 ? null : new Semaphore(max, true);
    }

    /**
     * @return {@code false}이면 새 SSE 연결을 허용하지 않는다(호출자는 429로 응답해야 한다).
     */
    public boolean tryAcquire() {
        return semaphore == null || semaphore.tryAcquire();
    }

    /** 스트림이 완료·타임아웃·오류로 끝날 때 한 번만 호출한다. */
    public void release() {
        if (semaphore != null) {
            semaphore.release();
        }
    }
}
