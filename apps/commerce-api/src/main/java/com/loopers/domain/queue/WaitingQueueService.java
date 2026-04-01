package com.loopers.domain.queue;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class WaitingQueueService {

    private final WaitingQueueRepository waitingQueueRepository;
    private final Optional<QueueJoinFallbackPublisher> queueJoinFallbackPublisher;

    public WaitingQueueService(
            WaitingQueueRepository waitingQueueRepository,
            Optional<QueueJoinFallbackPublisher> queueJoinFallbackPublisher
    ) {
        this.waitingQueueRepository = waitingQueueRepository;
        this.queueJoinFallbackPublisher = queueJoinFallbackPublisher;
    }

    /**
     * 대기열 진입. Redis 오류 시(설정이 켜져 있으면) Kafka로 비동기 접수 결과를 반환한다.
     */
    public JoinQueueResult joinQueue(String eventId, Long userId, long score, boolean fallbackEnabled) {
        try {
            return joinQueueFromRecovery(eventId, userId, score);
        } catch (CoreException e) {
            throw e;
        } catch (RuntimeException e) {
            if (!isRecoverableQueueBackendFailure(e)) {
                throw e;
            }
            if (fallbackEnabled) {
                String requestId = UUID.randomUUID().toString();
                QueueJoinFallbackPublisher publisher = queueJoinFallbackPublisher
                        .orElseThrow(() -> new CoreException(ErrorType.INTERNAL_ERROR, "대기열 fallback publisher가 구성되지 않았습니다."));
                publisher.publish(eventId, userId, score, requestId);
                return JoinQueueResult.asyncAccepted(requestId);
            }
            throw new CoreException(ErrorType.INTERNAL_ERROR, "대기열을 일시적으로 사용할 수 없습니다.", e);
        }
    }

    /**
     * Kafka 복구 컨슈머 전용. Redis에 직접 반영하며, 실패 시 예외를 던져 Kafka 재시도/DLT로 넘긴다.
     */
    public JoinQueueResult joinQueueFromRecovery(String eventId, Long userId, long score) {
        waitingQueueRepository.addIfAbsent(eventId, userId, score);

        Long rank = waitingQueueRepository.findRank(eventId, userId).orElse(null);
        if (rank == null) {
            // 간헐적 Redis 레이스/복제 지연 상황에서 rank 조회가 비는 케이스 방어.
            waitingQueueRepository.addIfAbsent(eventId, userId, score);
            rank = waitingQueueRepository.findRank(eventId, userId)
                    .orElseThrow(() -> new CoreException(ErrorType.INTERNAL_ERROR, "대기열 순번 조회에 실패했습니다."));
        }
        long totalWaiting = waitingQueueRepository.countWaiting(eventId);

        return JoinQueueResult.synced(rank, totalWaiting);
    }

    /**
     * 대기열에 남아 있을 때만 순번·총 대기 인원을 반환한다. ZSET에 없으면 empty.
     * 순번과 ZCARD는 Redis Lua로 원자적으로 읽어 스케줄러 틱과의 경쟁을 줄인다.
     */
    public Optional<QueuePositionSnapshot> findPosition(String eventId, Long userId) {
        return waitingQueueRepository.findPositionSnapshot(eventId, userId);
    }

    private static boolean isRecoverableQueueBackendFailure(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof DataAccessException) {
                return true;
            }
            if (t instanceof RedisConnectionFailureException) {
                return true;
            }
            if (t instanceof RedisSystemException) {
                return true;
            }
        }
        return false;
    }
}
