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

    private static final String CAPACITY_FULL_MESSAGE = "대기열 정원이 찼습니다.";

    private final WaitingQueueRepository waitingQueueRepository;
    private final Optional<QueueJoinFallbackPublisher> queueJoinFallbackPublisher;
    private final WaitingQueueCapacityPolicy capacityPolicy;

    public WaitingQueueService(
            WaitingQueueRepository waitingQueueRepository,
            Optional<QueueJoinFallbackPublisher> queueJoinFallbackPublisher,
            WaitingQueueCapacityPolicy capacityPolicy
    ) {
        this.waitingQueueRepository = waitingQueueRepository;
        this.queueJoinFallbackPublisher = queueJoinFallbackPublisher;
        this.capacityPolicy = capacityPolicy;
    }

    /**
     * 대기열 진입.
     * <p>
     * 정상 시 {@link #joinQueueFromRecovery(String, Long, long)}와 동일하게 Redis에 반영한다.
     * 복구 가능한 Redis/백엔드 예외이고 {@code fallbackEnabled}이면 Kafka로 진입 의도만 발행하고
     * {@link JoinQueueResult#asyncAccepted(String)}를 반환한다.
     * <p>
     * Kafka {@code publish}가 실패하면 원시 예외 대신 {@link CoreException}({@link ErrorType#INTERNAL_ERROR})으로
     * 감싸 API 응답 형식을 맞춘다.
     *
     * @param eventId         이벤트(대기열) 식별자
     * @param userId          사용자 ID
     * @param score           ZSET score(진입 시각 등)
     * @param fallbackEnabled Redis 장애 시 Kafka 비동기 접수 사용 여부
     * @return 동기 진입 결과 또는 비동기 접수 안내
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
                try {
                    publisher.publish(eventId, userId, score, requestId);
                    return JoinQueueResult.asyncAccepted(requestId);
                } catch (RuntimeException publishException) {
                    throw new CoreException(ErrorType.INTERNAL_ERROR, "대기열을 일시적으로 사용할 수 없습니다.", publishException);
                }
            }
            throw new CoreException(ErrorType.INTERNAL_ERROR, "대기열을 일시적으로 사용할 수 없습니다.", e);
        }
    }

    /**
     * Kafka 복구 컨슈머 전용. Redis에 직접 반영하며, 실패 시 예외를 던져 Kafka 재시도/DLT로 넘긴다.
     */
    public JoinQueueResult joinQueueFromRecovery(String eventId, Long userId, long score) {
        long cap = capacityPolicy.maxWaiting();
        WaitingQueueJoinResult first = waitingQueueRepository.addIfAbsentWithinCapacity(eventId, userId, score, cap);
        if (first == WaitingQueueJoinResult.CAPACITY_FULL) {
            throw new CoreException(ErrorType.CONFLICT, CAPACITY_FULL_MESSAGE);
        }

        QueuePositionSnapshot snapshot = waitingQueueRepository.findPositionSnapshot(eventId, userId)
                .orElseThrow(() -> new CoreException(ErrorType.INTERNAL_ERROR, "대기열 순번 조회에 실패했습니다."));

        return JoinQueueResult.synced(snapshot.position(), snapshot.totalWaiting());
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
