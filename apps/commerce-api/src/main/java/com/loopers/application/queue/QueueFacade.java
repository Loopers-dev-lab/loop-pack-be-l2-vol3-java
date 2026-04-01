package com.loopers.application.queue;

import com.loopers.domain.queue.EntryTokenRepository;
import com.loopers.domain.queue.JoinQueueOutcome;
import com.loopers.domain.queue.JoinQueueResult;
import com.loopers.domain.queue.QueuePollHintPolicy;
import com.loopers.domain.queue.QueuePositionEstimator;
import com.loopers.domain.queue.WaitingQueueService;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

@Component
public class QueueFacade {

    private static final String DEFAULT_EVENT_ID = "default";

    private final WaitingQueueService waitingQueueService;
    private final EntryTokenRepository entryTokenRepository;
    private final QueuePositionProperties queuePositionProperties;
    private final QueueFallbackProperties queueFallbackProperties;

    public QueueFacade(
            WaitingQueueService waitingQueueService,
            EntryTokenRepository entryTokenRepository,
            QueuePositionProperties queuePositionProperties,
            QueueFallbackProperties queueFallbackProperties
    ) {
        this.waitingQueueService = waitingQueueService;
        this.entryTokenRepository = entryTokenRepository;
        this.queuePositionProperties = queuePositionProperties;
        this.queueFallbackProperties = queueFallbackProperties;
    }

    /**
     * 대기열 진입 결과를 API 계약으로 변환한다.
     * Redis 정상 시 동기 순번을, 장애 fallback 시 비동기 접수 상태를 반환한다.
     */
    public QueueInfo joinQueue(Long userId) {
        JoinQueueOutcome outcome = waitingQueueService.joinQueue(
                DEFAULT_EVENT_ID,
                userId,
                Instant.now().toEpochMilli(),
                queueFallbackProperties.enabled()
        );
        // 정상 경로: 즉시 순번/대기 인원 반환
        if (outcome instanceof JoinQueueOutcome.Sync sync) {
            JoinQueueResult r = sync.result();
            return new QueueInfo(r.position(), r.totalWaiting());
        }
        // fallback 경로: Kafka 비동기 접수 상태와 requestId 반환
        if (outcome instanceof JoinQueueOutcome.AsyncAccepted async) {
            return QueueInfo.asyncAccepted(async.requestId());
        }
        throw new IllegalStateException("unsupported JoinQueueOutcome: " + outcome);
    }

    /**
     * 대기열에 없으면 empty. 순번·예상 대기·입장 토큰(있으면)·폴링 힌트를 포함한다.
     */
    public Optional<QueuePositionInfo> getQueuePosition(Long userId) {
        Optional<JoinQueueResult> pos = waitingQueueService.findPosition(DEFAULT_EVENT_ID, userId);
        if (pos.isEmpty()) {
            return Optional.empty();
        }
        JoinQueueResult p = pos.get();
        String token = entryTokenRepository.findEntryToken(userId).orElse(null);
        double tps = queuePositionProperties.throughputTps();
        long estimated = QueuePositionEstimator.estimatedWaitSeconds(p.position(), tps);
        long pollMs = QueuePollHintPolicy.suggestedPollIntervalMs(p.position());
        long retrySec = QueuePollHintPolicy.retryAfterSeconds(p.position());
        return Optional.of(new QueuePositionInfo(
                p.position(),
                p.totalWaiting(),
                estimated,
                token,
                pollMs,
                retrySec
        ));
    }
}
