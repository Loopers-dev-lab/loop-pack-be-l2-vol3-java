package com.loopers.domain.queue;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class WaitingQueueService {

    private final WaitingQueueRepository waitingQueueRepository;

    public WaitingQueueService(WaitingQueueRepository waitingQueueRepository) {
        this.waitingQueueRepository = waitingQueueRepository;
    }

    public JoinQueueResult joinQueue(String eventId, Long userId, long score) {
        waitingQueueRepository.addIfAbsent(eventId, userId, score);

        Long rank = waitingQueueRepository.findRank(eventId, userId)
            .orElseThrow(() -> new CoreException(ErrorType.INTERNAL_ERROR, "대기열 순번 조회에 실패했습니다."));
        long totalWaiting = waitingQueueRepository.countWaiting(eventId);

        return new JoinQueueResult(rank, totalWaiting);
    }

    /**
     * 대기열에 남아 있을 때만 순번·총 대기 인원을 반환한다. ZSET에 없으면 empty.
     * 순번과 ZCARD는 Redis Lua로 원자적으로 읽어 스케줄러 틱과의 경쟁을 줄인다.
     */
    public Optional<JoinQueueResult> findPosition(String eventId, Long userId) {
        return waitingQueueRepository.findPositionSnapshot(eventId, userId)
                .map(s -> new JoinQueueResult(s.position(), s.totalWaiting()));
    }

    public record JoinQueueResult(
        long position,
        long totalWaiting
    ) {
    }
}

